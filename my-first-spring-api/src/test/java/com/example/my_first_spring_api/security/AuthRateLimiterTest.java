package com.example.my_first_spring_api.security;

import com.example.my_first_spring_api.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the in-process auth rate limiter (Phase D, security hardening).
 * The package-private constructor takes a mutable {@link Clock}, so every window
 * boundary is exercised deterministically instead of sleeping.
 */
class AuthRateLimiterTest {

    private static final String MOBILE = "9876543210";
    private static final String IP = "203.0.113.7";

    @Test
    void disabledLimiterNeverCountsAndNeverThrows() {
        AuthRateLimiter limiter = new AuthRateLimiter(false, new MutableClock(Instant.parse("2026-10-12T10:00:00Z")));
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.recordLoginFailure(MOBILE, IP))
                    .as("a disabled limiter must never report a lockout").isFalse();
            limiter.recordRegistration(IP);
        }
        assertThatCode(() -> {
            limiter.assertLoginAllowed(MOBILE, IP);
            limiter.assertRegistrationAllowed(IP);
            limiter.clearLoginFailures(MOBILE);
        }).doesNotThrowAnyException();
    }

    @Test
    void fifthFailureLocksMobileAndIpUntilTheWindowPasses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(true, clock);

        for (int i = 0; i < AuthRateLimiter.LOGIN_MAX_FAILURES - 1; i++) {
            assertThat(limiter.recordLoginFailure(MOBILE, IP))
                    .as("the lock trips exactly on the %d-th failure", AuthRateLimiter.LOGIN_MAX_FAILURES)
                    .isFalse();
            assertThatCode(() -> limiter.assertLoginAllowed(MOBILE, IP))
                    .doesNotThrowAnyException();
        }
        assertThat(limiter.recordLoginFailure(MOBILE, IP)).isTrue();
        assertThatThrownBy(() -> limiter.assertLoginAllowed(MOBILE, IP))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> limiter.assertLoginAllowed("1234500000", IP))
                .as("the source IP is locked for every mobile, not just this one")
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.assertLoginAllowed("1234500000", "198.51.100.9"))
                .as("an unrelated mobile from an unrelated source IP is not affected")
                .doesNotThrowAnyException();

        clock.advance(AuthRateLimiter.LOGIN_WINDOW.plusSeconds(1));
        assertThatCode(() -> limiter.assertLoginAllowed(MOBILE, IP))
                .as("the lock must lapse once the window has passed")
                .doesNotThrowAnyException();
    }

    @Test
    void successClearsThePerMobileCounterButTheIpCounterKeepsCounting() {
        AuthRateLimiter limiter = new AuthRateLimiter(true, new MutableClock(Instant.parse("2026-10-12T10:00:00Z")));
        String sharedIp = "198.51.100.20";
        for (int i = 0; i < 4; i++) {
            limiter.recordLoginFailure(MOBILE, sharedIp);
        }
        limiter.clearLoginFailures(MOBILE);
        for (int i = 0; i < 4; i++) {
            assertThat(limiter.recordLoginFailure(MOBILE, sharedIp))
                    .as("the per-mobile counter was reset, so the lock must not trip yet")
                    .isFalse();
        }
        assertThatThrownBy(() -> limiter.assertLoginAllowed("1111111111", sharedIp))
                .as("the per-IP counter is deliberately NOT reset by a success: a shared "
                        + "NAT that trips the limit stays limited until the window passes")
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void registrationIsCappedPerIpAndOtherIpsAreUnaffected() {
        AuthRateLimiter limiter = new AuthRateLimiter(true, new MutableClock(Instant.parse("2026-10-12T10:00:00Z")));
        for (int i = 0; i < AuthRateLimiter.REGISTRATION_MAX - 1; i++) {
            limiter.recordRegistration(IP);
            assertThatCode(() -> limiter.assertRegistrationAllowed(IP)).doesNotThrowAnyException();
        }
        limiter.recordRegistration(IP);
        assertThatThrownBy(() -> limiter.assertRegistrationAllowed(IP))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.assertRegistrationAllowed("198.51.100.30"))
                .as("the cap is per IP; other IPs keep registering")
                .doesNotThrowAnyException();
    }

    /** Test double: a clock the test advances by hand. */
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