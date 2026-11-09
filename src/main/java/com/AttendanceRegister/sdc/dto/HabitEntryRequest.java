package com.AttendanceRegister.sdc.dto;

import com.AttendanceRegister.sdc.model.HabitEntryStatus;

/**
 * Body of the mark endpoint. {@code amount} is how much was done, for COUNT and DURATION
 * habits, and {@code note} is the student's own words about the day.
 */
public record HabitEntryRequest(HabitEntryStatus status, Integer amount, String note) {
}
