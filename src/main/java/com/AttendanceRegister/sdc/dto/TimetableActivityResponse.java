package com.AttendanceRegister.sdc.dto;

import java.time.DayOfWeek;
import java.time.Instant;

import com.AttendanceRegister.sdc.model.ActivityCategory;

// An activity with its times both as "HH:mm" and as minutes from midnight
public record TimetableActivityResponse(
        String id,
        String modeId,
        DayOfWeek dayOfWeek,
        String title,
        ActivityCategory category,
        String startTime,
        String endTime,
        int startMinutes,
        int endMinutes,
        Instant createdAt,
        Instant updatedAt) {}
