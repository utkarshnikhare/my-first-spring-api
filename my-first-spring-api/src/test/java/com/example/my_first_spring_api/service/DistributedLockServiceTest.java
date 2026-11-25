package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DistributedLockServiceTest {

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cluster_locks");
    }

    @Test
    void acquiresLockAndBlocksConcurrentAttempt() {
        String key = "test_lock_1";
        boolean acquiredFirst = lockService.tryLock(key, Duration.ofMinutes(5));
        assertThat(acquiredFirst).isTrue();

        // Second attempt for the same unexpired key must fail (simulating another cluster node)
        boolean acquiredSecond = lockService.tryLock(key, Duration.ofMinutes(5));
        assertThat(acquiredSecond).isFalse();

        // Release lock
        lockService.unlock(key);

        // Third attempt after unlock must succeed
        boolean acquiredThird = lockService.tryLock(key, Duration.ofMinutes(5));
        assertThat(acquiredThird).isTrue();
    }

    @Test
    void allowsReacquiringExpiredLock() {
        String key = "test_lock_expired";
        // Acquire with 0 duration (instantly expired)
        boolean acquired = lockService.tryLock(key, Duration.ofMillis(0));
        assertThat(acquired).isTrue();

        // Reacquire should succeed because the lock already expired
        boolean reacquired = lockService.tryLock(key, Duration.ofMinutes(5));
        assertThat(reacquired).isTrue();
    }
}
