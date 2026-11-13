package com.AttendanceRegister.sdc.dto;

import java.util.List;

/**
 * Body of "set one weekday of the class plan". The list replaces that day whole — add,
 * remove, reorder and change-hours are all the same operation — and an empty list clears
 * the day. Sending the day as one piece means a half-applied edit cannot exist.
 */
public record PlanDayRequest(List<Entry> classes) {

    public record Entry(String subjectId, Integer hours) {
    }
}
