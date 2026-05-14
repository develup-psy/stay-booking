package psy.staybooking.booking.repository;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.booking.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByCheckoutTokenId(String checkoutTokenId);

    @Query("select count(booking) from Booking booking where booking.productId = :productId and booking.status in :statuses")
    long countByProductIdAndStatuses(Long productId, Collection<BookingStatus> statuses);
}
