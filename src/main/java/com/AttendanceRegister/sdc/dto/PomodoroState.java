package com.AttendanceRegister.sdc.dto;

// What the Pomodoro timer should show right now. remainingSeconds is measured on the
// server so a wrong device clock doesn't matter.
public record PomodoroState(
        Phase phase,
        String sessionId,
        long remainingSeconds,
        long totalSeconds,
        boolean paused) {

    public enum Phase { IDLE, FOCUS, BREAK }
}
