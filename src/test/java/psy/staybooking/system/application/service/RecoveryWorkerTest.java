package psy.staybooking.system.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import psy.staybooking.booking.application.service.BookingStockService;
import psy.staybooking.booking.application.service.BookingTransactionService;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.booking.domain.BookingStatus;
import psy.staybooking.booking.repository.BookingRepository;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.repository.PaymentRepository;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.repository.ProductRepository;
import psy.staybooking.system.domain.SystemModeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryWorkerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BookingStockService bookingStockService;

    @Mock
    private BookingTransactionService bookingTransactionService;

    @Mock
    private ModeService modeService;

    @Mock
    private WorkerLockService workerLockService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-05-14T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private RecoveryWorker recoveryWorker;

    @Test
    void recoverOrphanStockAllocationsReleasesStaleHolderWhenBookingDoesNotExist() {
        Product product = Product.create(
            "P-001",
            "seoul-stay",
            100_000L,
            10,
            LocalDateTime.of(2026, 5, 14, 17, 0),
            LocalDateTime.of(2026, 5, 20, 20, 0),
            LocalDateTime.of(2026, 6, 1, 15, 0),
            LocalDateTime.of(2026, 6, 2, 11, 0)
        );
        setProductId(product, 1L);
        String reservedAt = String.valueOf(clock.instant().minusSeconds(60).toEpochMilli());

        when(workerLockService.tryLock("recovery:orphan-stock-worker")).thenReturn(true);
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(bookingStockService.getRedisHolders(1L)).thenReturn(Map.of("token-id", reservedAt));
        when(bookingRepository.findByCheckoutTokenId("token-id")).thenReturn(Optional.empty());

        setRecoveryField("orphanStockStaleSeconds", 30L);
        recoveryWorker.recoverOrphanStockAllocations();

        verify(bookingStockService).releaseRedisStock(1L, "token-id");
        verify(workerLockService).unlock("recovery:orphan-stock-worker");
    }

    @Test
    void recoverTimedOutPendingPaymentsFailsBookingAndReleasesRedisStock() {
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(booking, 1L);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        payment.startProcessing();
        setPaymentId(payment, 1L);

        when(workerLockService.tryLock("recovery:pending-timeout-worker")).thenReturn(true);
        when(bookingRepository.findByStatusAndCreatedAtBefore(eq(BookingStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
            .thenReturn(List.of(booking));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(payment));
        when(bookingStockService.hasRedisHolder(10L, "token-id")).thenReturn(true);

        setRecoveryField("pendingPaymentTimeoutSeconds", 180L);
        recoveryWorker.recoverTimedOutPendingPayments();

        verify(bookingTransactionService).failBooking(eq(1L), eq(1L), eq(1L), any());
        verify(bookingStockService).releaseRedisStock(10L, "token-id");
        verify(workerLockService).unlock("recovery:pending-timeout-worker");
    }

    @Test
    void resyncRedisStockRebuildsRemainingAndHoldersAndSwitchesToRedisNormal() {
        Product product = Product.create(
            "P-001",
            "seoul-stay",
            100_000L,
            10,
            LocalDateTime.of(2026, 5, 14, 17, 0),
            LocalDateTime.of(2026, 5, 20, 20, 0),
            LocalDateTime.of(2026, 6, 1, 15, 0),
            LocalDateTime.of(2026, 6, 2, 11, 0)
        );
        setProductId(product, 1L);

        Booking pendingBooking = Booking.createPending("B-001", 1L, 1L, "token-1", 100_000L);
        setBookingId(pendingBooking, 1L);
        setCreatedAt(pendingBooking, LocalDateTime.of(2026, 5, 14, 17, 30));

        Booking confirmedBooking = Booking.createPending("B-002", 2L, 1L, "token-2", 100_000L);
        setBookingId(confirmedBooking, 2L);
        confirmedBooking.confirm(LocalDateTime.of(2026, 5, 14, 17, 35));
        setCreatedAt(confirmedBooking, LocalDateTime.of(2026, 5, 14, 17, 31));

        when(workerLockService.tryLock("recovery:redis-resync-worker")).thenReturn(true);
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.RECOVERING);
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(bookingRepository.findByStatusIn(List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED)))
            .thenReturn(List.of(pendingBooking, confirmedBooking));

        recoveryWorker.resyncRedisStock();

        ArgumentCaptor<Map<String, String>> holdersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(bookingStockService).syncRedisStock(eq(1L), eq(8L), holdersCaptor.capture());
        assertThat(holdersCaptor.getValue()).containsKeys("token-1", "token-2");
        verify(modeService).switchToRedisNormal("redis stock resynchronized", "redis-resync-worker");
        verify(workerLockService).unlock("recovery:redis-resync-worker");
    }

    @Test
    void resyncRedisStockSkipsWhenCurrentModeIsNotRecovering() {
        when(workerLockService.tryLock("recovery:redis-resync-worker")).thenReturn(true);
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.DB_FALLBACK);

        recoveryWorker.resyncRedisStock();

        verify(productRepository, never()).findAll();
        verify(bookingStockService, never()).syncRedisStock(any(), anyLong(), any());
        verify(modeService, never()).switchToRedisNormal(any(), any());
    }

    private void setProductId(Product product, Long productId) {
        try {
            var field = Product.class.getDeclaredField("productId");
            field.setAccessible(true);
            field.set(product, productId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setBookingId(Booking booking, Long bookingId) {
        try {
            var field = Booking.class.getDeclaredField("bookingId");
            field.setAccessible(true);
            field.set(booking, bookingId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setPaymentId(Payment payment, Long paymentId) {
        try {
            var field = Payment.class.getDeclaredField("paymentId");
            field.setAccessible(true);
            field.set(payment, paymentId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setCreatedAt(Booking booking, LocalDateTime createdAt) {
        try {
            var field = booking.getClass().getSuperclass().getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(booking, createdAt);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setRecoveryField(String fieldName, long value) {
        try {
            var field = RecoveryWorker.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setLong(recoveryWorker, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
