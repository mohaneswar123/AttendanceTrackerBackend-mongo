package com.AttendanceRegister.sdc.dto;

import com.AttendanceRegister.sdc.model.HabitEntryStatus;

// One marked day, with the date as "YYYY-MM-DD" so the page does no date maths
public record HabitEntryResponse(
        String habitId,
        String date,
        HabitEntryStatus status,
        int amount,
        String note) {
}
