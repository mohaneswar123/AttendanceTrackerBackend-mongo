package com.AttendanceRegister.sdc.model;

/**
 * Which part of the app a student's action touched. Stored in the audit log, so a name
 * is never reused for something else.
 */
public enum UserActionFeature {
    /** A class marked present, absent or no-class */
    ATTENDANCE,
    /** A subject in the student's list */
    SUBJECT,
    /** A task on the board */
    TASK,
    /** A dated entry in the calendar */
    CALENDAR,
    /** A timetable mode or one of its weekly activities */
    TIMETABLE,
    /** A focus session */
    FOCUS
}
