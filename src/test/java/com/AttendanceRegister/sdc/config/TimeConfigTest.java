package com.AttendanceRegister.sdc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;

class TimeConfigTest {

    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void putTheZoneBack() {
        TimeZone.setDefault(original);
    }

    @Test
    void theClockRunsInTheConfiguredZone() {
        assertThat(new TimeConfig("Asia/Kolkata").clock().getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    /**
     * The bug this setting exists for.
     *
     * At 00:35 in India it is still the previous day in UTC. A server on a UTC clock told a
     * student that today had not happened yet and refused to mark a habit; on the students'
     * own zone the same moment is simply today.
     */
    @Test
    void justAfterMidnightInIndiaIsAlreadyTheNewDay() {
        Instant justAfterMidnightIST = Instant.parse("2026-11-10T19:05:00Z");
        ZoneId india = new TimeConfig("Asia/Kolkata").clock().getZone();

        assertThat(LocalDate.now(Clock.fixed(justAfterMidnightIST, india)))
                .isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(LocalDate.now(Clock.fixed(justAfterMidnightIST, ZoneId.of("UTC"))))
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    /** So that a plain LocalDate.now() anywhere in the app means the same day as the clock */
    @Test
    void theJvmDefaultIsSetToTheSameZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        TimeConfig config = new TimeConfig("Asia/Kolkata");

        config.useOneZoneEverywhere();

        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    /**
     * Not just the class in isolation: Spring has to run that @PostConstruct, or a plain
     * LocalDate.now() in a service would still be on the server's zone.
     */
    @Test
    void springAppliesTheZoneWhenTheContextStarts() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        new ApplicationContextRunner()
                .withUserConfiguration(TimeConfig.class)
                .withPropertyValues("app.time-zone=Asia/Kolkata")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
                    // What a bare LocalDate.now() anywhere in the app now reads
                    assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Kolkata"));
                });
    }

    @Test
    void anotherZoneCanBeSetForSomewhereElse() {
        assertThat(new TimeConfig("America/Los_Angeles").clock().getZone())
                .isEqualTo(ZoneId.of("America/Los_Angeles"));
    }

    /** A typo stops the app rather than running it quietly on the wrong day */
    @Test
    void aZoneThatIsNotARealZoneIsRefusedAtStartup() {
        Throwable thrown = catchThrowable(() -> new TimeConfig("Asia/Kolkatta"));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown).hasMessageContaining("app.time-zone");
    }
}
