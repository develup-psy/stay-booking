package psy.staybooking.booking.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import psy.staybooking.booking.domain.BookingStatus;
import psy.staybooking.booking.repository.BookingRepository;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class BookingStockService {

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;
    private final BookingRepository bookingRepository;
    @Qualifier("bookingReserveStockScript")
    private final RedisScript<Long> bookingReserveStockScript;
    @Qualifier("bookingReleaseStockScript")
    private final RedisScript<Long> bookingReleaseStockScript;

    public long getAvailableDatabaseStock(Long productId, int totalStock) {
        long activeBookingCount = bookingRepository.countByProductIdAndStatuses(
            productId,
            List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED)
        );
        long availableStock = totalStock - activeBookingCount;
        if (availableStock < 0) {
            return 0;
        }
        return availableStock;
    }

    public long getAvailableRedisStock(Long productId) {
        //1. Redis remaining 값을 기준으로 현재 판매 가능 수량 조회
        String remainingKey = "stock:product:" + productId + ":remaining";
        String stockValue = stringRedisTemplate.opsForValue().get(remainingKey);
        if (stockValue == null) {
            throw new BusinessException(ErrorCode.BOOKING_STOCK_UNAVAILABLE, "Redis 재고 키를 읽을 수 없습니다.");
        }
        return Long.parseLong(stockValue);
    }

    public boolean reserveRedisStock(Long productId, String checkoutTokenId) {
        //1. Redis remaining 감소와 holders 기록을 Lua로 원자 처리
        String remainingKey = "stock:product:" + productId + ":remaining";
        String holdersKey = "stock:product:" + productId + ":holders";
        Long result = stringRedisTemplate.execute(
            bookingReserveStockScript,
            List.of(remainingKey, holdersKey),
            checkoutTokenId,
            String.valueOf(Instant.now(clock).toEpochMilli())
        );

        if (result == null) {
            throw new BusinessException(ErrorCode.BOOKING_STOCK_UNAVAILABLE, "Redis 재고 확보 결과를 확인할 수 없습니다.");
        }
        if (result == 0L) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }
        if (result == 2L) {
            return false;
        }
        if (result == -1L) {
            throw new BusinessException(ErrorCode.BOOKING_STOCK_UNAVAILABLE, "Redis 재고 키가 초기화되지 않았습니다.");
        }
        return true;
    }

    public void releaseRedisStock(Long productId, String checkoutTokenId) {
        //1. holders 흔적이 있으면 제거하고 remaining을 복구
        String remainingKey = "stock:product:" + productId + ":remaining";
        String holdersKey = "stock:product:" + productId + ":holders";
        stringRedisTemplate.execute(bookingReleaseStockScript, List.of(remainingKey, holdersKey), checkoutTokenId);
    }
}
