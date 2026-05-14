package psy.staybooking.booking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.payment.application.dto.PaymentDetailDto;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateRequest {

    private Long productId;
    private String checkoutToken;
    private long pointAmount;
    private PaymentDetailDto paymentDetail;
}
