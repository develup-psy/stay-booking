package psy.staybooking.system.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psy.staybooking.system.domain.SystemMode;
import psy.staybooking.system.domain.SystemModeType;
import psy.staybooking.system.repository.SystemModeRepository;

@Service
@RequiredArgsConstructor
public class SystemModeService {

    private final SystemModeRepository systemModeRepository;
    private final Clock clock;

    @Transactional
    public SystemModeType getCurrentMode() {
        SystemMode systemMode = systemModeRepository.findById((byte) 1)
            .orElseGet(() -> systemModeRepository.save(SystemMode.initialize(LocalDateTime.now(clock))));
        return systemMode.getMode();
    }

    @Transactional
    public void switchToDbFallback(String reason) {
        SystemMode systemMode = systemModeRepository.findById((byte) 1)
            .orElseGet(() -> systemModeRepository.save(SystemMode.initialize(LocalDateTime.now(clock))));
        systemMode.switchMode(SystemModeType.DB_FALLBACK, reason, "booking-service", LocalDateTime.now(clock));
    }
}
