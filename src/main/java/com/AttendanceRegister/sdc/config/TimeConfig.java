package com.AttendanceRegister.sdc.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * The one time zone the whole app runs on.
 *
 * Almost every date in here is a day in a student's life: whether a habit is due today,
 * which day focus minutes belong to, what date a calendar entry falls on. Those days run
 * on the students' own clock, not the server's.
 *
 * On a server in UTC a student in India who opened the app after midnight was shown the
 * previous day, and marking a habit was refused as "a day that has not happened yet" — the
 * day had happened, just not in UTC.
 *
 * Two things are set from one property. The {@link Clock} bean is what services inject and
 * what tests replace; the JVM default is set as well, so a plain {@code LocalDate.now()}
 * anywhere in the app — including code written later that forgets the clock — still means
 * the same day as everything else.
 */
@Configuration
public class TimeConfig {

    private final ZoneId zone;

    public TimeConfig(@Value("${app.time-zone:Asia/Kolkata}") String timeZone) {
        this.zone = zoneFromSetting(timeZone);
    }

    @PostConstruct
    void useOneZoneEverywhere() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
    }

    // Injected wherever "now" matters, so tests can control the time
    @Bean
    public Clock clock() {
        return Clock.system(zone);
    }

    ZoneId zone() {
        return zone;
    }

    /** A misspelt zone stops the app at startup rather than quietly running on the wrong day */
    static ZoneId zoneFromSetting(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException ex) {
            throw new IllegalStateException(
                    "app.time-zone must be a time zone id such as Asia/Kolkata, not '" + timeZone + "'", ex);
        }
    }
}
