package com.AttendanceRegister.sdc.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.exception.ApiException;

/**
 * Slows down guessing at a password.
 *
 * There is one admin account and one password, and nothing else stands between a guess
 * and the whole system, so the endpoint refuses to keep answering after a few wrong
 * tries. A correct password clears the count, so somebody who mistypes twice and then
 * gets it right is never held up.
 *
 * The counts are held in memory. That is deliberate: it needs no extra service, and the
 * worst a restart can do is forgive some failed attempts. It also means the limit is per
 * instance — if the API is ever run on more than one, this has to move to shared storage
 * for the limit to mean anything.
 */
@Component
public class LoginThrottle {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCKOUT = Duration.ofMinutes(15);

    /** How long a quiet key is kept before it is forgotten */
    private static final Duration FORGET_AFTER = Duration.ofHours(1);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Called before checking the password. Throws 429 while the key is locked out. */
    public void check(String key) {
        Attempts current = attempts.get(normalise(key));
        if (current == null) {
            return;
        }
        Instant now = clock.instant();
        if (current.count >= MAX_ATTEMPTS && current.last.plus(LOCKOUT).isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, current.last.plus(LOCKOUT)).toMinutes());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-ins. Try again in " + minutes
                            + (minutes == 1 ? " minute." : " minutes."));
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        sweep(now);
        attempts.compute(normalise(key), (ignored, current) -> {
            // A lockout that has run out starts the count again rather than resuming it
            if (current == null || current.last.plus(LOCKOUT).isBefore(now) && current.count >= MAX_ATTEMPTS) {
                return new Attempts(1, now);
            }
            return new Attempts(current.count + 1, now);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(normalise(key));
    }

    private void sweep(Instant now) {
        attempts.entrySet().removeIf(entry -> entry.getValue().last.plus(FORGET_AFTER).isBefore(now));
    }

    private static String normalise(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private record Attempts(int count, Instant last) {
    }
}
