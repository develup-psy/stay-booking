package psy.staybooking.system.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import psy.staybooking.system.domain.SystemModeType;

@Component
@RequiredArgsConstructor
public class RedisHealthChecker {

    private final StringRedisTemplate stringRedisTemplate;
    private final ModeService modeService;

    @Scheduled(
        fixedDelayString = "${mode.health-checker.delay-ms:5000}",
        initialDelayString = "${mode.health-checker.initial-delay-ms:5000}"
    )
    public void checkRedisHealth() {
        SystemModeType currentMode = modeService.getCurrentMode();
        boolean redisHealthy = isRedisHealthy();

        if (!redisHealthy) {
            if (currentMode == SystemModeType.REDIS_NORMAL || currentMode == SystemModeType.RECOVERING) {
                modeService.switchToDbFallback("redis health check failed", "redis-health-checker");
            }
            return;
        }

        if (currentMode == SystemModeType.DB_FALLBACK) {
            modeService.switchToRecovering("redis health check recovered", "redis-health-checker");
        }
    }

    boolean isRedisHealthy() {
        try {
            String ping = stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return ping != null && ping.equalsIgnoreCase("PONG");
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
