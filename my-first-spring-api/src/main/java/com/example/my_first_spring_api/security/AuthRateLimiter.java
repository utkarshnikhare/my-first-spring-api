package com.example.my_first_spring_api.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, bounded login / registration rate limiter for the single-instance
 * Render pilot. Deliberately uses no Redis or other external store: the state
 * lives in a bounded {@link ConcurrentHashMap} and is process-local.
 *
 * <p>The limiter can be switched off with {@code sociomart.ratelimit.enabled=false}
 * (the fast integration-test profile does this so unrelated tests are not coupled
 * through the shared in-memory counters); dedicated security tests turn it back on.</p>
 *
 * <p><b>Policy</b></p>
 * <ul>
 *   <li>Login: 5 failed attempts within 15 minutes (per mobile AND per IP) lock
 *       the subject for 15 minutes (HTTP 429). A successful login clears the
 *       per-mobile counter. The per-IP counter decays over the window and is not
 *       cleared by a single success, so a shared NAT that trips the limit stays
 *       limited until the window passes.</li>
 *   <li>Registration: 5 registrations within 15 minutes per IP (HTTP 429).</li>
 * </ul>
 *
 * <p><b>Documented limitations (acceptable for the single-instance pilot)</b></p>
 * <ul>
 *   <li>State is lost on restart, so a redeploy clears every counter.</li>
 *   <li>Counters are not shared across instances; if the pilot is scaled
 *       horizontally this must be replaced with a shared store (e.g. Redis).
 *       That is a follow-up, not required for the current single-instance pilot.</li>
 * </ul>
 */
@Component
public class AuthRateLimiter {

    public static final int LOGIN_MAX_FAILURES = 5;
    public static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    public static final int REGISTRATION_MAX = 5;
    public static final Duration REGISTRATION_WINDOW = Duration.ofMinutes(15);
    /** Manual password recovery: 3 forgot-password requests per mobile AND per IP per window. */
    public static final int RECOVERY_MAX = 3;
    public static final Duration RECOVERY_WINDOW = Duration.ofMinutes(15);

    /** Hard cap on tracked keys so spoofed mobiles/IPs cannot grow memory without bound. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final boolean enabled;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Annotated because the class also defines the package-private Clock ctor. */
    @Autowired
    public AuthRateLimiter(@Value("${sociomart.ratelimit.enabled:true}") boolean enabled) {
        this(enabled, Clock.systemUTC());
    }

    AuthRateLimiter(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    /**
     * Checks whether a login attempt for this mobile from this IP is permitted.
     * Throws {@code TooManyRequestsException} (429) if the account or the IP is locked.
     */
    public void assertLoginAllowed(String mobile, String ip) {
        assertKeys(loginMobileKey(mobile), loginIpKey(ip));
    }

    /**
     * Checks whether a registration attempt from this IP is permitted.
     * Throws {@code TooManyRequestsException} (429) if the IP is locked.
     */
    public void assertRegistrationAllowed(String ip) {
        assertKeys(registrationKey(ip));
    }

    /**
     * Checks whether a forgot-password (manual recovery) request for this mobile
     * from this IP is permitted. Throws {@code TooManyRequestsException} (429) if
     * either key is locked, so the recovery endpoint cannot be spammed.
     */
    public void assertRecoveryAllowed(String mobile, String ip) {
        assertKeys(recoveryMobileKey(mobile), recoveryIpKey(ip));
    }

    /** Throws {@code TooManyRequestsException} (429) if any of the given keys is currently locked. */
    private void assertKeys(String... keys) {
        if (!enabled) {
            return;
        }
        Instant now = now();
        for (String key : keys) {
            Bucket bucket = buckets.get(key);
            if (bucket != null && bucket.lockedUntil != null && now.isBefore(bucket.lockedUntil)) {
                throw new com.example.my_first_spring_api.exception.TooManyRequestsException();
            }
        }
    }

    /**
     * Records a failed login for both the mobile and the IP.
     *
     * @return {@code true} if this failure just tripped the per-mobile lock, so the
     *         caller can audit the lockout exactly once.
     */
    public boolean recordLoginFailure(String mobile, String ip) {
        if (!enabled) {
            return false;
        }
        boolean mobileLocked = hit(loginMobileKey(mobile), LOGIN_MAX_FAILURES, LOGIN_WINDOW);
        hit(loginIpKey(ip), LOGIN_MAX_FAILURES, LOGIN_WINDOW);
        return mobileLocked;
    }

    /** Clears the per-mobile failure counter after a successful login. */
    public void clearLoginFailures(String mobile) {
        if (!enabled) {
            return;
        }
        buckets.remove(loginMobileKey(mobile));
    }

    /** Records a registration attempt from an IP; locks when the threshold is reached. */
    public void recordRegistration(String ip) {
        if (!enabled) {
            return;
        }
        hit(registrationKey(ip), REGISTRATION_MAX, REGISTRATION_WINDOW);
    }

    /** Records a forgot-password (manual recovery) request; locks when the threshold is reached. */
    public void recordRecovery(String mobile, String ip) {
        if (!enabled) {
            return;
        }
        hit(recoveryMobileKey(mobile), RECOVERY_MAX, RECOVERY_WINDOW);
        hit(recoveryIpKey(ip), RECOVERY_MAX, RECOVERY_WINDOW);
    }

    private boolean hit(String key, int max, Duration window) {
        Bucket bucket = bucket(key);
        Instant now = now();
        boolean lockedNow = false;
        synchronized (bucket) {
            if (bucket.windowStart == null
                    || Duration.between(bucket.windowStart, now).compareTo(window) > 0) {
                bucket.windowStart = now;
                bucket.count = 0;
                bucket.lockedUntil = null;
            }
            bucket.count++;
            if (bucket.count >= max) {
                bucket.lockedUntil = now.plus(window);
                lockedNow = true;
            }
        }
        return lockedNow;
    }

    private Bucket bucket(String key) {
        if (buckets.size() >= MAX_TRACKED_KEYS) {
            Instant cutoff = now().minus(LOGIN_WINDOW.multipliedBy(2));
            buckets.entrySet().removeIf(e -> {
                Bucket b = e.getValue();
                Instant last = b.lockedUntil != null ? b.lockedUntil : b.windowStart;
                return last == null || last.isBefore(cutoff);
            });
        }
        return buckets.computeIfAbsent(key, k -> new Bucket());
    }

    private Instant now() {
        return Instant.now(clock);
    }

    static String loginMobileKey(String mobile) {
        return "L:m:" + mobile;
    }

    static String loginIpKey(String ip) {
        return "L:i:" + ip;
    }

    static String registrationKey(String ip) {
        return "R:" + ip;
    }

    static String recoveryMobileKey(String mobile) {
        return "F:m:" + mobile;
    }

    static String recoveryIpKey(String ip) {
        return "F:i:" + ip;
    }

    /**
     * One counter window. Mutations happen inside {@code synchronized (bucket)} in
     * {@link #hit} so concurrent attempts on the same subject cannot lose an
     * increment; the fields are volatile because {@link #assertKeys} reads them
     * without holding the monitor.
     */
    private static final class Bucket {
        /** Start of the current counting window; null until first use. */
        volatile Instant windowStart;
        /** Attempts observed inside the current window. */
        int count;
        /** Non-null while locked; attempts are rejected until this instant. */
        volatile Instant lockedUntil;
    }
}

