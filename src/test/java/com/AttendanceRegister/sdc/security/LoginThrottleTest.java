package com.AttendanceRegister.sdc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.exception.ApiException;

class LoginThrottleTest {

    /** A clock the test moves by hand, so no test has to wait */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-27T10:00:00Z");

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void lettsAFewWrongTriesThrough() {
        for (int attempt = 0; attempt < LoginThrottle.MAX_ATTEMPTS - 1; attempt++) {
            assertThatCode(() -> throttle.check("admin@example.com")).doesNotThrowAnyException();
            throttle.recordFailure("admin@example.com");
        }
        assertThatCode(() -> throttle.check("admin@example.com")).doesNotThrowAnyException();
    }

    @Test
    void refusesAfterTooManyWrongTries() {
        for (int attempt = 0; attempt < LoginThrottle.MAX_ATTEMPTS; attempt++) {
            throttle.recordFailure("admin@example.com");
        }

        assertThatThrownBy(() -> throttle.check("admin@example.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    ApiException api = (ApiException) thrown;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
                    assertThat(api.getMessage()).contains("minutes");
                });
    }

    @Test
    void theLockoutEndsOnItsOwn() {
        for (int attempt = 0; attempt < LoginThrottle.MAX_ATTEMPTS; attempt++) {
            throttle.recordFailure("admin@example.com");
        }
        clock.advanceMinutes(LoginThrottle.LOCKOUT.toMinutes() + 1);

        assertThatCode(() -> throttle.check("admin@example.com")).doesNotThrowAnyException();
    }

    @Test
    void gettingItRightClearsTheCount() {
        for (int attempt = 0; attempt < LoginThrottle.MAX_ATTEMPTS - 1; attempt++) {
            throttle.recordFailure("admin@example.com");
        }
        throttle.recordSuccess("admin@example.com");

        // The next wrong try starts from one again, so it is nowhere near a lockout
        throttle.recordFailure("admin@example.com");
        assertThatCode(() -> throttle.check("admin@example.com")).doesNotThrowAnyException();
    }

    @Test
    void oneAccountsFailuresDoNotLockAnother() {
        for (int attempt = 0; attempt < LoginThrottle.MAX_ATTEMPTS; attempt++) {
            throttle.recordFailure("admin@example.com");
        }

        assertThatCode(() -> throttle.check("other@example.com")).doesNotThrowAnyException();
    }

    @Test
    void theEmailIsMatchedIgnoringCaseAndSpacing() {
        for (int attempt = 0; attempt < LoginThrottle.MAX_ATTEMPTS; attempt++) {
            throttle.recordFailure("Admin@Example.com");
        }

        assertThatThrownBy(() -> throttle.check("  admin@example.com  "))
                .isInstanceOf(ApiException.class);
    }
}
