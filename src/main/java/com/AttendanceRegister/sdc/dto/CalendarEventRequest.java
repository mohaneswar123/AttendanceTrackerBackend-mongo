package com.AttendanceRegister.sdc.dto;

import com.AttendanceRegister.sdc.model.CalendarEventCategory;
import com.AttendanceRegister.sdc.model.CalendarEventType;

// Body of POST and PUT /api/calendar/events. date is "YYYY-MM-DD" and the times are
// "HH:mm", both in the calendar time zone; the times are ignored for all-day entries.
public record CalendarEventRequest(
        String title,
        CalendarEventType type,
        /** What it is about; null keeps whatever the entry already had */
        CalendarEventCategory category,
        String date,
        Boolean allDay,
        String startTime,
        String endTime) {}
