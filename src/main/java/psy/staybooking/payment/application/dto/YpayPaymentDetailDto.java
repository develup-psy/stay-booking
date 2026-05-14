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
public class YpayPaymentDetailDto implements PaymentDetailDto {

    private String authorizationToken;

    @Override
    public ExternalPaymentMethod getExternalPaymentMethod() {
        return ExternalPaymentMethod.YPAY;
    }
}
