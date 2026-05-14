package psy.staybooking.point.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.common.persistence.BaseTimeEntity;

@Getter
@Entity
@Table(name = "point_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet extends BaseTimeEntity {

    @Id
    private Long userId;

    private long totalAmount;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL)
    private List<PointHold> holds = new ArrayList<>();

    @Builder(access = AccessLevel.PACKAGE)
    PointWallet(Long userId, long totalAmount, List<PointHold> holds) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        if (holds != null) {
            this.holds = holds;
        }
    }

    public static PointWallet create(Long userId, long totalAmount) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }
        if (totalAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 총액은 0 이상이어야 합니다.");
        }

        return PointWallet.builder()
            .userId(userId)
            .totalAmount(totalAmount)
            .holds(new ArrayList<>())
            .build();
    }

    public PointHold hold(Long bookingId, long amount) {
        if (this.holds.stream().anyMatch(existing -> existing.getBookingId().equals(bookingId))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "하나의 예약에는 하나의 포인트 잠금만 연결할 수 있습니다.");
        }
        if (getAvailableAmount() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT, "사용 가능한 포인트가 부족합니다.");
        }

        PointHold hold = PointHold.hold(bookingId, amount);

        this.holds.add(hold);
        hold.assignWallet(this);
        return hold;
    }

    public void commitHold(Long bookingId) {
        if (bookingId == null || bookingId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 식별자는 필수입니다.");
        }
        PointHold hold = this.holds.stream()
            .filter(existing -> existing.getBookingId().equals(bookingId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARAMETER, "포인트 잠금 정보가 없습니다."));
        decrease(hold.getAmount());
        hold.commit();
    }

    public void releaseHold(Long bookingId) {
        if (bookingId == null || bookingId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 식별자는 필수입니다.");
        }
        PointHold hold = this.holds.stream()
            .filter(existing -> existing.getBookingId().equals(bookingId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARAMETER, "포인트 잠금 정보가 없습니다."));
        hold.release();
    }

    public void decrease(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "차감 금액은 0보다 커야 합니다.");
        }
        if (this.totalAmount < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT, "보유 포인트가 부족합니다.");
        }
        this.totalAmount -= amount;
    }

    public long getAvailableAmount() {
        long heldAmount = this.holds.stream()
            .filter(hold -> hold.getStatus() == PointHoldStatus.HELD)
            .mapToLong(PointHold::getAmount)
            .sum();
        return this.totalAmount - heldAmount;
    }
}
