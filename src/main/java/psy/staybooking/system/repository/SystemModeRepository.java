package psy.staybooking.system.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import psy.staybooking.system.domain.SystemMode;

public interface SystemModeRepository extends JpaRepository<SystemMode, Byte> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select systemMode from SystemMode systemMode where systemMode.systemModeId = :systemModeId")
    Optional<SystemMode> findByIdForUpdate(@Param("systemModeId") Byte systemModeId);
}
