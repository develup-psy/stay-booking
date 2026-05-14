package psy.staybooking.system.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import psy.staybooking.system.domain.SystemMode;
import psy.staybooking.system.domain.SystemModeType;
import psy.staybooking.system.repository.SystemModeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModeServiceTest {

    @Mock
    private SystemModeRepository systemModeRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-05-14T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private ModeService modeService;

    @Test
    void getCurrentModeReturnsInitializedModeWhenRowDoesNotExist() {
        when(systemModeRepository.findById((byte) 1)).thenReturn(Optional.empty());
        when(systemModeRepository.save(org.mockito.ArgumentMatchers.any(SystemMode.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        SystemModeType currentMode = modeService.getCurrentMode();

        assertThat(currentMode).isEqualTo(SystemModeType.REDIS_NORMAL);
    }

    @Test
    void switchToDbFallbackChangesMode() {
        SystemMode systemMode = SystemMode.initialize(LocalDateTime.of(2026, 5, 14, 18, 0));
        when(systemModeRepository.findByIdForUpdate((byte) 1)).thenReturn(Optional.of(systemMode));

        modeService.switchToDbFallback("redis down", "redis-health-checker");

        assertThat(systemMode.getMode()).isEqualTo(SystemModeType.DB_FALLBACK);
        assertThat(systemMode.getReason()).isEqualTo("redis down");
        assertThat(systemMode.getUpdatedBy()).isEqualTo("redis-health-checker");
    }

    @Test
    void switchToRecoveringChangesMode() {
        SystemMode systemMode = SystemMode.initialize(LocalDateTime.of(2026, 5, 14, 18, 0));
        systemMode.switchMode(SystemModeType.DB_FALLBACK, "redis down", "redis-health-checker", LocalDateTime.of(2026, 5, 14, 18, 1));
        when(systemModeRepository.findByIdForUpdate((byte) 1)).thenReturn(Optional.of(systemMode));

        modeService.switchToRecovering("redis restored", "redis-health-checker");

        assertThat(systemMode.getMode()).isEqualTo(SystemModeType.RECOVERING);
        assertThat(systemMode.getReason()).isEqualTo("redis restored");
    }

    @Test
    void switchToDbFallbackDoesNothingWhenAlreadyFallback() {
        SystemMode systemMode = SystemMode.initialize(LocalDateTime.of(2026, 5, 14, 18, 0));
        systemMode.switchMode(SystemModeType.DB_FALLBACK, "redis down", "redis-health-checker", LocalDateTime.of(2026, 5, 14, 18, 1));
        when(systemModeRepository.findByIdForUpdate((byte) 1)).thenReturn(Optional.of(systemMode));

        modeService.switchToDbFallback("redis down again", "booking-service");

        assertThat(systemMode.getReason()).isEqualTo("redis down");
        verify(systemModeRepository, never()).save(org.mockito.ArgumentMatchers.any(SystemMode.class));
    }
}
