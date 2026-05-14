package psy.staybooking.payment.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto {

    private boolean approved;
    private String providerTransactionId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime approvedAt;

    public static PaymentResponseDto approved(String providerTransactionId, LocalDateTime approvedAt) {
        return PaymentResponseDto.builder()
            .approved(true)
            .providerTransactionId(providerTransactionId)
            .approvedAt(approvedAt)
            .build();
    }

    public static PaymentResponseDto failed(String errorCode, String errorMessage) {
        return PaymentResponseDto.builder()
            .approved(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
