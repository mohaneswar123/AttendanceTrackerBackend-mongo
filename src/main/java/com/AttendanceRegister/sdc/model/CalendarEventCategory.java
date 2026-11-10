package com.AttendanceRegister.sdc.model;

/**
 * What an entry is about, which is separate from whether it is an event or a reminder.
 *
 * A deadline can be either: "essay due" as a reminder on the day, or a two-hour slot you
 * booked to finish it. Keeping the two apart is what lets the calendar colour entries by
 * what they are for rather than by how they were entered.
 */
public enum CalendarEventCategory {
    CLASS,
    STUDY,
    EXAM,
    DEADLINE,
    PERSONAL
}
