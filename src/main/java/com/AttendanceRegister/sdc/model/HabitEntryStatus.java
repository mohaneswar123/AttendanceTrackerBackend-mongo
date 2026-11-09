package com.AttendanceRegister.sdc.model;

/**
 * How one day went.
 *
 * SKIPPED is the point of having three states rather than two: illness, exams and travel
 * are not failures, so a skipped day neither breaks a streak nor counts against the
 * completion rate. Without it a student who has one bad day gives up on the habit.
 */
public enum HabitEntryStatus {
    DONE,
    MISSED,
    SKIPPED
}
