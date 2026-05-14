package psy.staybooking.point.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import psy.staybooking.point.domain.PointWallet;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
}
