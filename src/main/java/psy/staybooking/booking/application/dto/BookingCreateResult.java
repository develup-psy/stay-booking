package psy.staybooking.booking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.payment.domain.Payment;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateResult {

    private Booking booking;
    private Payment payment;
}
