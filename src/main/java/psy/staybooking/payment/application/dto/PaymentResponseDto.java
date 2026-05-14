package psy.staybooking.payment.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.exception.ErrorCode;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto {

    private boolean approved;
    private String providerTransactionId;
    private String errorCode;
    private String providerErrorCode;
    private String errorMessage;
    private LocalDateTime approvedAt;

    public static PaymentResponseDto approved(String providerTransactionId, LocalDateTime approvedAt) {
        return PaymentResponseDto.builder()
            .approved(true)
            .providerTransactionId(providerTransactionId)
            .approvedAt(approvedAt)
            .build();
    }

    public static PaymentResponseDto approvalFailed(String providerErrorCode, String errorMessage) {
        return PaymentResponseDto.builder()
            .approved(false)
            .errorCode(ErrorCode.PAYMENT_APPROVAL_FAILED.getCode())
            .providerErrorCode(providerErrorCode)
            .errorMessage(errorMessage)
            .build();
    }

    public static PaymentResponseDto providerUnavailable(String providerErrorCode, String errorMessage) {
        return PaymentResponseDto.builder()
            .approved(false)
            .errorCode(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE.getCode())
            .providerErrorCode(providerErrorCode)
            .errorMessage(errorMessage)
            .build();
    }

    public static PaymentResponseDto processingFailed(String errorMessage) {
        return PaymentResponseDto.builder()
            .approved(false)
            .errorCode(ErrorCode.PAYMENT_PROCESSING_FAILED.getCode())
            .errorMessage(errorMessage)
            .build();
    }
}
