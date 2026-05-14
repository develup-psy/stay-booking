package psy.staybooking.system.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import psy.staybooking.booking.application.service.BookingStockService;
import psy.staybooking.booking.application.service.BookingTransactionService;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.booking.domain.BookingStatus;
import psy.staybooking.booking.repository.BookingRepository;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.repository.PaymentRepository;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.repository.ProductRepository;
import psy.staybooking.system.domain.SystemModeType;

@Component
@RequiredArgsConstructor
public class RecoveryWorker {

    private static final String ORPHAN_STOCK_LOCK = "recovery:orphan-stock-worker";
    private static final String PENDING_TIMEOUT_LOCK = "recovery:pending-timeout-worker";
    private static final String REDIS_RESYNC_LOCK = "recovery:redis-resync-worker";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final BookingStockService bookingStockService;
    private final BookingTransactionService bookingTransactionService;
    private final ModeService modeService;
    private final WorkerLockService workerLockService;
    private final Clock clock;

    @Value("${recovery.orphan-stock.stale-seconds:30}")
    private long orphanStockStaleSeconds;

    @Value("${recovery.pending-payment.timeout-seconds:180}")
    private long pendingPaymentTimeoutSeconds;

    @Scheduled(
        fixedDelayString = "${recovery.orphan-stock.delay-ms:10000}",
        initialDelayString = "${recovery.orphan-stock.initial-delay-ms:10000}"
    )
    public void recoverOrphanStockAllocations() {
        if (!workerLockService.tryLock(ORPHAN_STOCK_LOCK)) {
            return;
        }

        try {
            //1. 상품별 Redis holders를 조회
            for (Product product : productRepository.findAll()) {
                Map<String, String> holders = bookingStockService.getRedisHolders(product.getProductId());

                //2. 오래된 holder 중 booking이 없거나 이미 FAILED면 재고를 반납
                for (Map.Entry<String, String> holder : holders.entrySet()) {
                    long reservedAt = Long.parseLong(holder.getValue());
                    long ageSeconds = (clock.instant().toEpochMilli() - reservedAt) / 1000;

                    if (ageSeconds < orphanStockStaleSeconds) {
                        continue;
                    }

                    Booking booking = bookingRepository.findByCheckoutTokenId(holder.getKey()).orElse(null);
                    if (booking == null || booking.getStatus() == BookingStatus.FAILED) {
                        bookingStockService.releaseRedisStock(product.getProductId(), holder.getKey());
                    }
                }
            }
        } catch (RuntimeException exception) {
            modeService.switchToDbFallback("orphan stock recovery failed", "recovery-worker");
        } finally {
            workerLockService.unlock(ORPHAN_STOCK_LOCK);
        }
    }

    @Scheduled(
        fixedDelayString = "${recovery.pending-payment.delay-ms:10000}",
        initialDelayString = "${recovery.pending-payment.initial-delay-ms:10000}"
    )
    public void recoverTimedOutPendingPayments() {
        if (!workerLockService.tryLock(PENDING_TIMEOUT_LOCK)) {
            return;
        }

        try {
            //1. 오래된 PENDING_PAYMENT 예약을 조회
            LocalDateTime threshold = LocalDateTime.now(clock).minusSeconds(pendingPaymentTimeoutSeconds);
            List<Booking> staleBookings = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING_PAYMENT, threshold);

            //2. 결제 실패와 포인트 해제, 예약 실패를 확정
            for (Booking staleBooking : staleBookings) {
                Payment payment = paymentRepository.findByBookingId(staleBooking.getBookingId()).orElse(null);
                if (payment == null) {
                    continue;
                }

                bookingTransactionService.failBooking(
                    staleBooking.getBookingId(),
                    payment.getPaymentId(),
                    staleBooking.getUserId(),
                    PaymentResponseDto.processingFailed("예약 결제 처리 시간이 초과되었습니다.")
                );

                //3. Redis 정상 경로였다면 holders 흔적이 있는 경우에만 재고를 반납
                if (bookingStockService.hasRedisHolder(staleBooking.getProductId(), staleBooking.getCheckoutTokenId())) {
                    bookingStockService.releaseRedisStock(staleBooking.getProductId(), staleBooking.getCheckoutTokenId());
                }
            }
        } catch (RuntimeException exception) {
            modeService.switchToDbFallback("pending payment recovery failed", "recovery-worker");
        } finally {
            workerLockService.unlock(PENDING_TIMEOUT_LOCK);
        }
    }

    @Scheduled(
        fixedDelayString = "${recovery.redis-resync.delay-ms:10000}",
        initialDelayString = "${recovery.redis-resync.initial-delay-ms:10000}"
    )
    public void resyncRedisStock() {
        if (!workerLockService.tryLock(REDIS_RESYNC_LOCK)) {
            return;
        }

        try {
            //1. RECOVERING일 때만 DB 기준으로 Redis 재고를 다시 구성
            if (modeService.getCurrentMode() != SystemModeType.RECOVERING) {
                return;
            }

            List<Booking> activeBookings = bookingRepository.findByStatusIn(List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));
            Map<Long, List<Booking>> bookingsByProductId = activeBookings.stream().collect(java.util.stream.Collectors.groupingBy(Booking::getProductId));

            //2. 상품별 remaining과 holders를 DB 기준으로 재구성
            for (Product product : productRepository.findAll()) {
                List<Booking> productBookings = bookingsByProductId.getOrDefault(product.getProductId(), List.of());
                long remainingStock = product.getTotalStock() - productBookings.size();
                Map<String, String> holders = new HashMap<>();

                for (Booking booking : productBookings) {
                    LocalDateTime createdAt = booking.getCreatedAt() == null ? LocalDateTime.now(clock) : booking.getCreatedAt();
                    long reservedAt = createdAt.atZone(clock.getZone()).toInstant().toEpochMilli();
                    holders.put(booking.getCheckoutTokenId(), String.valueOf(reservedAt));
                }

                bookingStockService.syncRedisStock(product.getProductId(), remainingStock, holders);
            }

            //3. 재동기화가 끝나면 REDIS_NORMAL로 복귀
            modeService.switchToRedisNormal("redis stock resynchronized", "redis-resync-worker");
        } catch (RuntimeException exception) {
            modeService.switchToDbFallback("redis resync failed", "redis-resync-worker");
        } finally {
            workerLockService.unlock(REDIS_RESYNC_LOCK);
        }
    }
}
