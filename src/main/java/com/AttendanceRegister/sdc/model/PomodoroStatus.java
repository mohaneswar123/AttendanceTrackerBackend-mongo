package com.AttendanceRegister.sdc.model;

public enum PomodoroStatus {
    // Focus time is running or paused; at most one per student
    ACTIVE,
    // The full focus time was reached
    COMPLETED,
    // The student reset the timer
    INTERRUPTED
}
