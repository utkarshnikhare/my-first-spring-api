package com.example.my_first_spring_api.security;

import com.example.my_first_spring_api.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the forgot-password (manual recovery) rate limit: 3 requests
 * per mobile AND per IP within 15 minutes, then 429 until the window passes.
 * Uses the package-private Clock constructor for deterministic windows.
 */
class AuthRateLimiterRecoveryTest {

    private static final String MOBILE = "9765432100";
    private static final String IP = "203.0.113.50";

    @Test
    void thirdRecoveryRequestLocksMobileAndIpUntilTheWindowPasses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(true, clock);

        for (int i = 0; i < AuthRateLimiter.RECOVERY_MAX - 1; i++) {
            limiter.recordRecovery(MOBILE, IP);
            assertThatCode(() -> limiter.assertRecoveryAllowed(MOBILE, IP))
                    .doesNotThrowAnyException();
        }
        limiter.recordRecovery(MOBILE, IP);
        assertThatThrownBy(() -> limiter.assertRecoveryAllowed(MOBILE, IP))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> limiter.assertRecoveryAllowed("9111111111", IP))
                .as("the source IP is locked for every mobile, not just this one")
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.assertRecoveryAllowed("9111111111", "198.51.100.44"))
                .as("an unrelated mobile from an unrelated source IP is not affected")
                .doesNotThrowAnyException();

        clock.advance(AuthRateLimiter.RECOVERY_WINDOW.plusSeconds(1));
        assertThatCode(() -> limiter.assertRecoveryAllowed(MOBILE, IP))
                .as("the lock must lapse once the window has passed")
                .doesNotThrowAnyException();
    }

    @Test
    void disabledLimiterNeverLocksRecovery() {
        AuthRateLimiter limiter = new AuthRateLimiter(false, new MutableClock(Instant.parse("2026-10-12T10:00:00Z")));
        for (int i = 0; i < 10; i++) {
            limiter.recordRecovery(MOBILE, IP);
        }
        assertThatCode(() -> limiter.assertRecoveryAllowed(MOBILE, IP)).doesNotThrowAnyException();
    }

    /** Test double: a clock the test advances by hand (same pattern as AuthRateLimiterTest). */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
