package psy.staybooking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardPaymentConfirmRequest {

    private String paymentToken;
    private String orderId;
    private long amount;
}
