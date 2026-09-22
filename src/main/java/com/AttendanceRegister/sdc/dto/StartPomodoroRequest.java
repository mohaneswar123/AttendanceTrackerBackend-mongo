package com.AttendanceRegister.sdc.dto;

// Optional body of POST /api/pomodoro/start: the lengths the student picked, in minutes.
// Either may be left out to use the default (25 and 5).
public record StartPomodoroRequest(Integer focusMinutes, Integer breakMinutes) {}
