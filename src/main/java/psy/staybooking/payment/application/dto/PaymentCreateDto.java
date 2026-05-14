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
public class PaymentCreateDto {

    private Long bookingId;
    private long totalAmount;
    private long pointAmount;
    private ExternalPaymentMethod externalPaymentMethod;
    private PaymentDetailDto paymentDetail;
}
