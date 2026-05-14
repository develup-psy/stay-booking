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
public class ModeService {

    private final SystemModeRepository systemModeRepository;
    private final Clock clock;

    @Transactional
    public SystemModeType getCurrentMode() {
        SystemMode systemMode = systemModeRepository.findById((byte) 1)
            .orElseGet(() -> systemModeRepository.save(SystemMode.initialize(LocalDateTime.now(clock))));
        return systemMode.getMode();
    }

    @Transactional
    public void switchToDbFallback(String reason, String updatedBy) {
        SystemMode systemMode = systemModeRepository.findByIdForUpdate((byte) 1)
            .orElseGet(() -> systemModeRepository.save(SystemMode.initialize(LocalDateTime.now(clock))));

        if (systemMode.getMode() == SystemModeType.DB_FALLBACK) {
            return;
        }

        systemMode.switchMode(SystemModeType.DB_FALLBACK, reason, updatedBy, LocalDateTime.now(clock));
    }

    @Transactional
    public void switchToRecovering(String reason, String updatedBy) {
        SystemMode systemMode = systemModeRepository.findByIdForUpdate((byte) 1)
            .orElseGet(() -> systemModeRepository.save(SystemMode.initialize(LocalDateTime.now(clock))));

        if (systemMode.getMode() == SystemModeType.RECOVERING) {
            return;
        }

        systemMode.switchMode(SystemModeType.RECOVERING, reason, updatedBy, LocalDateTime.now(clock));
    }

    @Transactional
    public void switchToRedisNormal(String reason, String updatedBy) {
        SystemMode systemMode = systemModeRepository.findByIdForUpdate((byte) 1)
            .orElseGet(() -> systemModeRepository.save(SystemMode.initialize(LocalDateTime.now(clock))));

        if (systemMode.getMode() == SystemModeType.REDIS_NORMAL) {
            return;
        }

        systemMode.switchMode(SystemModeType.REDIS_NORMAL, reason, updatedBy, LocalDateTime.now(clock));
    }
}
