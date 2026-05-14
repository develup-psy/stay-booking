package psy.staybooking.booking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.common.persistence.BaseTimeEntity;

@Getter
@Entity
@Table(name = "bookings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    private String bookingNo;

    private Long userId;

    private Long productId;

    private String checkoutTokenId;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private long bookedPriceAmount;

    private String failureCode;

    private LocalDateTime confirmedAt;

    private LocalDateTime failedAt;

    @Builder(access = AccessLevel.PACKAGE)
    Booking(
        Long bookingId,
        String bookingNo,
        Long userId,
        Long productId,
        String checkoutTokenId,
        BookingStatus status,
        long bookedPriceAmount,
        String failureCode,
        LocalDateTime confirmedAt,
        LocalDateTime failedAt
    ) {
        this.bookingId = bookingId;
        this.bookingNo = bookingNo;
        this.userId = userId;
        this.productId = productId;
        this.checkoutTokenId = checkoutTokenId;
        this.status = status;
        this.bookedPriceAmount = bookedPriceAmount;
        this.failureCode = failureCode;
        this.confirmedAt = confirmedAt;
        this.failedAt = failedAt;
    }

    public static Booking createPending(
        String bookingNo,
        Long userId,
        Long productId,
        String checkoutTokenId,
        long bookedPriceAmount
    ) {
        if (bookingNo == null || bookingNo.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 번호는 필수입니다.");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }
        if (productId == null || productId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "상품 식별자는 필수입니다.");
        }
        if (checkoutTokenId == null || checkoutTokenId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "체크아웃 토큰 식별자는 필수입니다.");
        }
        if (bookedPriceAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "예약 금액은 0보다 커야 합니다.");
        }

        return Booking.builder()
            .bookingNo(bookingNo)
            .userId(userId)
            .productId(productId)
            .checkoutTokenId(checkoutTokenId)
            .status(BookingStatus.PENDING_PAYMENT)
            .bookedPriceAmount(bookedPriceAmount)
            .build();
    }

    public void confirm(LocalDateTime confirmedAt) {
        if (this.status != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "예약 상태 전이가 올바르지 않습니다.");
        }
        if (confirmedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 확정 시각은 필수입니다.");
        }

        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
        this.failedAt = null;
        this.failureCode = null;
    }

    public void fail(String failureCode, LocalDateTime failedAt) {
        if (this.status != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "예약 상태 전이가 올바르지 않습니다.");
        }
        if (failureCode == null || failureCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 실패 코드는 필수입니다.");
        }
        if (failedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 실패 시각은 필수입니다.");
        }

        this.status = BookingStatus.FAILED;
        this.failureCode = failureCode;
        this.failedAt = failedAt;
    }

    public boolean isActive() {
        return this.status == BookingStatus.PENDING_PAYMENT || this.status == BookingStatus.CONFIRMED;
    }
}
