package psy.staybooking.point.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import psy.staybooking.point.domain.PointWallet;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {

    @Query("select distinct wallet from PointWallet wallet left join fetch wallet.holds where wallet.userId = :userId")
    Optional<PointWallet> findWalletWithHoldsByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct wallet from PointWallet wallet left join fetch wallet.holds where wallet.userId = :userId")
    Optional<PointWallet> findWalletWithHoldsByUserIdForUpdate(Long userId);
}
