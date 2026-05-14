package psy.staybooking.payment.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import psy.staybooking.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);
}
