package com.AttendanceRegister.sdc.dto;

import java.util.List;

/**
 * How the student's habits went over a chosen stretch of time: this week, this month or
 * this year.
 *
 * The figures are worked out on the server for the same reason the per-habit streaks are:
 * a "3 times a week" habit has to be judged a week at a time, and a day still inside its
 * grace period must not be counted as missed. Doing that in the page would mean two
 * implementations that drift apart.
 *
 * {@code rate} values are whole percentages, and -1 means nothing was due in that
 * stretch, which reads differently from 0%.
 */
public record HabitProgressResponse(
        String range,
        String from,
        String to,
        int done,
        int due,
        int rate,
        /** The stretch cut into readable pieces: days for a week, weeks for a month, months for a year */
        List<Bucket> buckets,
        /**
         * Why the pieces may not add up to the headline, or null when they do. A piece one
         * day long cannot judge a "3 times a week" habit, so those habits sit out the
         * day-sized pieces and the page says so rather than leaving the student to wonder.
         */
        String bucketNote,
        /** The same stretch, per habit, worst first so what needs attention is at the top */
        List<HabitFigures> habits) {

    public record Bucket(String label, String from, String to, int done, int due, int rate) {
    }

    public record HabitFigures(String habitId, String name, String icon, int done, int due, int rate) {
    }
}
