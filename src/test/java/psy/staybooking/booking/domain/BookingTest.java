package psy.staybooking.booking.domain;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import psy.staybooking.common.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    @Test
    void createPendingBooking() {
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-1", 120_000L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getBookingNo()).isEqualTo("B-001");
        assertThat(booking.getBookedPriceAmount()).isEqualTo(120_000L);
        assertThat(booking.isActive()).isTrue();
    }

    @Test
    void confirmChangesStatusToConfirmed() {
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-1", 120_000L);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 14, 10, 0);

        booking.confirm(confirmedAt);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(booking.isActive()).isTrue();
    }

    @Test
    void failChangesStatusToFailed() {
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-1", 120_000L);
        LocalDateTime failedAt = LocalDateTime.of(2026, 5, 14, 10, 5);

        booking.fail("PAYMENT_FAILED", failedAt);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(booking.getFailureCode()).isEqualTo("PAYMENT_FAILED");
        assertThat(booking.getFailedAt()).isEqualTo(failedAt);
        assertThat(booking.isActive()).isFalse();
    }

    @Test
    void confirmThrowsWhenStatusIsNotPending() {
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-1", 120_000L);
        booking.fail("PAYMENT_FAILED", LocalDateTime.of(2026, 5, 14, 10, 5));

        assertThatThrownBy(() -> booking.confirm(LocalDateTime.of(2026, 5, 14, 10, 10)))
            .isInstanceOf(BusinessException.class);
    }
}
