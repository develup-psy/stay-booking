package psy.staybooking.system.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkerLockService {

    private final JdbcTemplate jdbcTemplate;

    public boolean tryLock(String lockName) {
        Long result = jdbcTemplate.queryForObject("SELECT GET_LOCK(?, 0)", Long.class, lockName);
        return result != null && result == 1L;
    }

    public void unlock(String lockName) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Long.class, lockName);
    }
}
