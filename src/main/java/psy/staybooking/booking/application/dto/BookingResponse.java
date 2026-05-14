package psy.staybooking.booking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.booking.domain.BookingStatus;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.domain.PaymentStatus;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long bookingId;
    private String bookingNo;
    private BookingStatus bookingStatus;
    private Long paymentId;
    private PaymentStatus paymentStatus;
    private long bookedPriceAmount;
    private long pointAmount;
    private long externalAmount;
    private ExternalPaymentMethod externalPaymentMethod;

    public static BookingResponse from(Booking booking, Payment payment) {
        return BookingResponse.builder()
            .bookingId(booking.getBookingId())
            .bookingNo(booking.getBookingNo())
            .bookingStatus(booking.getStatus())
            .paymentId(payment.getPaymentId())
            .paymentStatus(payment.getStatus())
            .bookedPriceAmount(booking.getBookedPriceAmount())
            .pointAmount(payment.getPointAmount())
            .externalAmount(payment.getExternalAmount())
            .externalPaymentMethod(payment.getExternalMethod())
            .build();
    }
}
