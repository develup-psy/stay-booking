package psy.staybooking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YpayPaymentConfirmRequest {

    private String authorizationToken;
    private String orderId;
    private long amount;
}
