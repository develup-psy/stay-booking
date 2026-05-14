package psy.staybooking.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import psy.staybooking.booking.domain.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
