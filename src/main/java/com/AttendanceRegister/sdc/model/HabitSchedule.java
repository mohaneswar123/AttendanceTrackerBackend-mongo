package com.AttendanceRegister.sdc.model;

/**
 * How often a habit is meant to happen.
 *
 * TIMES_PER_WEEK exists because a student's week moves around: "gym 3x a week" should not
 * be broken by going on Tuesday instead of Monday.
 */
public enum HabitSchedule {
    /** Every day */
    DAILY,
    /** Monday to Friday */
    WEEKDAYS,
    /** Only the days the student picked, such as Mon/Wed/Fri */
    CUSTOM_DAYS,
    /** A number of times within a week, on whichever days suit */
    TIMES_PER_WEEK
}
