package psy.staybooking.point.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.common.persistence.BaseTimeEntity;

@Getter
@Entity
@Table(name = "point_holds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHold extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pointHoldId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private PointWallet wallet;

    private Long bookingId;

    private long amount;

    @Enumerated(EnumType.STRING)
    private PointHoldStatus status;

    @Builder(access = AccessLevel.PACKAGE)
    PointHold(Long pointHoldId, PointWallet wallet, Long bookingId, long amount, PointHoldStatus status) {
        this.pointHoldId = pointHoldId;
        this.wallet = wallet;
        this.bookingId = bookingId;
        this.amount = amount;
        this.status = status;
    }

    static PointHold hold(Long bookingId, long amount) {
        if (bookingId == null || bookingId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 식별자는 필수입니다.");
        }
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 잠금 금액은 0보다 커야 합니다.");
        }

        return PointHold.builder()
            .bookingId(bookingId)
            .amount(amount)
            .status(PointHoldStatus.HELD)
            .build();
    }

    void commit() {
        if (this.status != PointHoldStatus.HELD) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "포인트 잠금 상태 전이가 올바르지 않습니다.");
        }
        this.status = PointHoldStatus.COMMITTED;
    }

    void release() {
        if (this.status != PointHoldStatus.HELD) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "포인트 잠금 상태 전이가 올바르지 않습니다.");
        }
        this.status = PointHoldStatus.RELEASED;
    }

    void assignWallet(PointWallet wallet) {
        if (wallet == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "포인트 지갑 정보는 필수입니다.");
        }
        this.wallet = wallet;
    }
}
