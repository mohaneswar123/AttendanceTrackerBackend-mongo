package com.AttendanceRegister.sdc.dto;

import java.time.DayOfWeek;

import com.AttendanceRegister.sdc.model.ActivityCategory;

// Body of POST and PUT for activities. Times are "HH:mm" and category may be null.
public record TimetableActivityRequest(
        DayOfWeek dayOfWeek,
        String title,
        ActivityCategory category,
        String startTime,
        String endTime) {}
