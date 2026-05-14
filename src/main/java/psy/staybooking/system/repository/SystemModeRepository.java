package psy.staybooking.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import psy.staybooking.system.domain.SystemMode;

public interface SystemModeRepository extends JpaRepository<SystemMode, Byte> {
}
