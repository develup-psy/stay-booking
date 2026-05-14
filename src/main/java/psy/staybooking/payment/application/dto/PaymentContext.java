package psy.staybooking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentContext {

    private Long paymentId;
    private ExternalPaymentMethod externalPaymentMethod;
    private long amount;
    private PaymentDetailDto paymentDetail;

    public static PaymentContext from(Payment payment, PaymentCreateDto request) {
        return PaymentContext.builder()
            .paymentId(payment.getPaymentId())
            .externalPaymentMethod(payment.getExternalMethod())
            .amount(payment.getExternalAmount())
            .paymentDetail(request.getPaymentDetail())
            .build();
    }
}
