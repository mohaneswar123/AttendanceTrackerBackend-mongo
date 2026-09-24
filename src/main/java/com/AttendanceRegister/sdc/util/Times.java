package com.AttendanceRegister.sdc.util;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import com.AttendanceRegister.sdc.exception.ApiException;

// Clock times of day as minutes from midnight, which keeps ordering and overlap
// comparisons exact. The API speaks "HH:mm".
public final class Times {

    public static final int MINUTES_IN_DAY = 24 * 60;

    private Times() {}

    // "09:30" -> 570. Strict, so "25:00" or "9am" are rejected.
    public static int parseMinutes(String value, String label) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(label + " is required");
        }
        try {
            LocalTime time = LocalTime.parse(value.trim());
            return time.getHour() * 60 + time.getMinute();
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(label + " must be a time such as 09:30");
        }
    }

    // 570 -> "09:30"
    public static String format(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    // 570 -> "9:30 AM", for messages students read
    public static String format12Hour(int minutes) {
        int hour = minutes / 60;
        int minute = minutes % 60;
        int shown = hour % 12 == 0 ? 12 : hour % 12;
        return String.format("%d:%02d %s", shown, minute, hour < 12 ? "AM" : "PM");
    }
}
