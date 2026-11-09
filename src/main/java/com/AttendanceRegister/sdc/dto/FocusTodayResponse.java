package com.AttendanceRegister.sdc.dto;

/** How much focus the student has finished today, for the Home screen */
public record FocusTodayResponse(int minutes, int sessions) {
}
