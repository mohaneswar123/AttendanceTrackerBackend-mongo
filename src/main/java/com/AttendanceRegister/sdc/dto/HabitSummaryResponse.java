package com.AttendanceRegister.sdc.dto;

/**
 * The week in review, for the panel shown from Sunday onward.
 *
 * {@code bestName} and {@code slippedName} are null when there is nothing to say: a
 * student with one habit has no "best", and a perfect week has nothing that slipped.
 */
public record HabitSummaryResponse(
        String weekStart,
        String weekEnd,
        int overallRate,
        String bestName,
        int bestRate,
        String slippedName,
        int slippedRate,
        String suggestion) {
}
