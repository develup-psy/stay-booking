package psy.staybooking.system.domain;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemModeTest {

    @Test
    void initializeCreatesDefaultMode() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 14, 13, 0);

        SystemMode systemMode = SystemMode.initialize(now);

        assertThat(systemMode.getSystemModeId()).isEqualTo((byte) 1);
        assertThat(systemMode.getMode()).isEqualTo(SystemModeType.REDIS_NORMAL);
        assertThat(systemMode.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void switchModeUpdatesModeAndReason() {
        SystemMode systemMode = SystemMode.initialize(LocalDateTime.of(2026, 5, 14, 13, 0));
        LocalDateTime changedAt = LocalDateTime.of(2026, 5, 14, 13, 5);

        systemMode.switchMode(SystemModeType.DB_FALLBACK, "redis down", "health-checker", changedAt);

        assertThat(systemMode.getMode()).isEqualTo(SystemModeType.DB_FALLBACK);
        assertThat(systemMode.getReason()).isEqualTo("redis down");
        assertThat(systemMode.getUpdatedBy()).isEqualTo("health-checker");
        assertThat(systemMode.getUpdatedAt()).isEqualTo(changedAt);
    }
}
