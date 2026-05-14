package psy.staybooking.payment.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    private Long bookingId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private long totalAmount;

    private long pointAmount;

    @Enumerated(EnumType.STRING)
    private ExternalPaymentMethod externalMethod;

    private long externalAmount;

    private String providerTransactionId;

    private LocalDateTime approvedAt;

    private String lastErrorCode;

    private String lastErrorMessage;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentLog> logs = new ArrayList<>();

    @Builder(access = AccessLevel.PACKAGE)
    Payment(
        Long paymentId,
        Long bookingId,
        PaymentStatus status,
        long totalAmount,
        long pointAmount,
        ExternalPaymentMethod externalMethod,
        long externalAmount,
        String providerTransactionId,
        LocalDateTime approvedAt,
        String lastErrorCode,
        String lastErrorMessage,
        List<PaymentLog> logs
    ) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.pointAmount = pointAmount;
        this.externalMethod = externalMethod;
        this.externalAmount = externalAmount;
        this.providerTransactionId = providerTransactionId;
        this.approvedAt = approvedAt;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        if (logs != null) {
            this.logs = logs;
        }
    }

    public static Payment create(
        Long bookingId,
        long totalAmount,
        long pointAmount,
        ExternalPaymentMethod externalPaymentMethod
    ) {
        if (bookingId == null || bookingId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "예약 식별자는 필수입니다.");
        }
        if (totalAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "총 결제 금액은 0보다 커야 합니다.");
        }
        if (pointAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 금액은 0 이상이어야 합니다.");
        }
        long externalAmount = totalAmount - pointAmount;

        if (externalAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 금액은 총 결제 금액을 초과할 수 없습니다.");
        }

        if (externalAmount == 0 && externalPaymentMethod != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 전액 결제에는 외부 결제 수단이 있으면 안 됩니다.");
        }

        if (externalAmount > 0 && externalPaymentMethod == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "외부 결제 금액이 있으면 결제 수단이 필요합니다.");
        }

        Payment payment = Payment.builder()
            .bookingId(bookingId)
            .status(PaymentStatus.PENDING)
            .totalAmount(totalAmount)
            .pointAmount(pointAmount)
            .externalMethod(externalPaymentMethod)
            .externalAmount(externalAmount)
            .build();
        payment.addLog(PaymentEventType.CREATED, null);
        return payment;
    }

    public void startProcessing() {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "결제 상태 전이가 올바르지 않습니다.");
        }
        if (this.externalMethod == null || this.externalAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "외부 결제가 없는 결제는 처리 시작할 수 없습니다.");
        }
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        addLog(PaymentEventType.APPROVAL_REQUESTED, "PG 승인 요청 시작");
    }

    public void approve(String providerTransactionId, LocalDateTime approvedAt) {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "결제 상태 전이가 올바르지 않습니다.");
        }
        if (this.isPointOnly()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 전액 결제는 승인 처리할 수 없습니다.");
        }
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "외부 결제 거래 식별자는 필수입니다.");
        }
        if (approvedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "승인 시각은 필수입니다.");
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.providerTransactionId = providerTransactionId;
        this.approvedAt = approvedAt;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        addLog(PaymentEventType.SUCCEEDED, "결제가 승인되었습니다.");
    }

    public void recordApprovalSuccess(String providerTransactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "결제 상태 전이가 올바르지 않습니다.");
        }
        if (this.isPointOnly()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 전액 결제는 외부 결제 시도 성공 기록을 남길 수 없습니다.");
        }
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "외부 결제 거래 식별자는 필수입니다.");
        }
        addLog(PaymentEventType.APPROVAL_SUCCEEDED, "PG 승인에 성공했습니다.");
    }

    public void recordApprovalFailure(String errorMessage) {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "결제 상태 전이가 올바르지 않습니다.");
        }
        if (this.isPointOnly()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 전액 결제는 외부 결제 시도 실패 기록을 남길 수 없습니다.");
        }
        addLog(PaymentEventType.APPROVAL_FAILED, errorMessage);
    }

    public void fail(String errorCode, String errorMessage) {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "결제 상태 전이가 올바르지 않습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        addLog(PaymentEventType.FAILED, errorMessage);
    }

    public void markSucceeded() {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "결제 상태 전이가 올바르지 않습니다.");
        }
        if (!this.isPointOnly()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "외부 결제가 있는 결제는 승인 처리로 완료해야 합니다.");
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        addLog(PaymentEventType.SUCCEEDED, "포인트 결제가 완료되었습니다.");
    }

    public boolean isPointOnly() {
        return this.externalAmount == 0;
    }

    private void addLog(PaymentEventType eventType, String eventMessage) {
        PaymentLog log = PaymentLog.create(this, eventType, eventMessage);
        this.logs.add(log);
    }
}
