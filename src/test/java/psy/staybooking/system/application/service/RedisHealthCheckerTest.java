package psy.staybooking.system.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import psy.staybooking.system.domain.SystemModeType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisHealthCheckerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ModeService modeService;

    @InjectMocks
    private RedisHealthChecker redisHealthChecker;

    @Test
    void checkRedisHealthSwitchesToDbFallbackWhenRedisNormalAndRedisFails() {
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.REDIS_NORMAL);
        when(stringRedisTemplate.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any()))
            .thenThrow(new RuntimeException("redis down"));

        redisHealthChecker.checkRedisHealth();

        verify(modeService).switchToDbFallback("redis health check failed", "redis-health-checker");
    }

    @Test
    void checkRedisHealthSwitchesToRecoveringWhenDbFallbackAndRedisRecovers() {
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.DB_FALLBACK);
        when(stringRedisTemplate.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any())).thenReturn("PONG");

        redisHealthChecker.checkRedisHealth();

        verify(modeService).switchToRecovering("redis health check recovered", "redis-health-checker");
    }

    @Test
    void checkRedisHealthSwitchesToDbFallbackWhenRecoveringAndRedisFails() {
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.RECOVERING);
        when(stringRedisTemplate.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any()))
            .thenThrow(new RuntimeException("redis down"));

        redisHealthChecker.checkRedisHealth();

        verify(modeService).switchToDbFallback("redis health check failed", "redis-health-checker");
    }

    @Test
    void checkRedisHealthDoesNothingWhenRedisNormalAndRedisIsHealthy() {
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.REDIS_NORMAL);
        when(stringRedisTemplate.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any())).thenReturn("PONG");

        redisHealthChecker.checkRedisHealth();

        verify(modeService, never()).switchToDbFallback(any(), any());
        verify(modeService, never()).switchToRecovering(any(), any());
    }
}
