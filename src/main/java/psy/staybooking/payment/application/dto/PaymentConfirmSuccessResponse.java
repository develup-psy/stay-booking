package psy.staybooking.payment.application.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmSuccessResponse {

    private String providerTransactionId;
    private String orderId;
    private String status;
    private OffsetDateTime approvedAt;
    private String method;
    private Long totalAmount;
}
