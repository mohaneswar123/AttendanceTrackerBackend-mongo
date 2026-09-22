package com.AttendanceRegister.sdc.dto;

import java.time.Instant;

import com.AttendanceRegister.sdc.model.CalendarEventType;

// A calendar entry as the frontend shows it: date and times already converted to the
// calendar time zone, plus the stored instants.
public record CalendarEventResponse(
        String id,
        String title,
        CalendarEventType type,
        String date,
        boolean allDay,
        String startTime,
        String endTime,
        Instant startAt,
        Instant endAt,
        Instant createdAt,
        Instant updatedAt) {}
