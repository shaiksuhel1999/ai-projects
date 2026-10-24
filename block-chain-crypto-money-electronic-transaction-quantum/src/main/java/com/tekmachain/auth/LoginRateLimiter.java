package com.tekmachain.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small in-memory brute-force guard for the login endpoint: after a number of
 * consecutive failures for a username it locks that username out for a cool-down.
 * A successful login clears the counter.
 *
 * <p>POC note: state is per-node and in-memory, which is fine for a single instance.
 * A multi-node deployment would back this with a shared store (e.g. Redis) so the
 * limit holds across the fleet.</p>
 */
@Component
public class LoginRateLimiter {

    @Value("${tekmachain.auth.max-login-attempts:5}")
    private int maxAttempts;

    @Value("${tekmachain.auth.lockout-minutes:15}")
    private long lockoutMinutes;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final class Attempt {
        int failures;
        long lockedUntil;
    }

    /** Throws 429 if this username is currently locked out. */
    public void assertNotLocked(String username) {
        Attempt a = attempts.get(key(username));
        if (a != null && a.lockedUntil > System.currentTimeMillis()) {
            long secs = (a.lockedUntil - System.currentTimeMillis()) / 1000;
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts — try again in " + Math.max(1, secs / 60 + 1) + " min");
        }
    }

    public void recordFailure(String username) {
        Attempt a = attempts.computeIfAbsent(key(username), k -> new Attempt());
        synchronized (a) {
            a.failures++;
            if (a.failures >= maxAttempts) {
                a.lockedUntil = System.currentTimeMillis() + Duration.ofMinutes(lockoutMinutes).toMillis();
                a.failures = 0;
            }
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
