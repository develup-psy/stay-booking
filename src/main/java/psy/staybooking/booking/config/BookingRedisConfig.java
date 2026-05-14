package psy.staybooking.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class BookingRedisConfig {

    @Bean(name = "bookingReserveStockScript")
    public RedisScript<Long> bookingReserveStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/booking-reserve-stock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean(name = "bookingReleaseStockScript")
    public RedisScript<Long> bookingReleaseStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/booking-release-stock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
