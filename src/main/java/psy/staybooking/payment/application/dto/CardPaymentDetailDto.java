package psy.staybooking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.payment.domain.ExternalPaymentMethod;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardPaymentDetailDto implements PaymentDetailDto {

    private String paymentToken;
    private Integer installmentMonths;

    @Override
    public ExternalPaymentMethod getExternalPaymentMethod() {
        return ExternalPaymentMethod.CARD;
    }
}
