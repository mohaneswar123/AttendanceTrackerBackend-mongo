package com.AttendanceRegister.sdc.dto;

import java.time.Instant;

import com.AttendanceRegister.sdc.model.TimetableColor;

// A mode as the frontend shows it. `active` is worked out from the student's single
// preference field, so there is no second copy of that truth.
public record TimetableModeResponse(
        String id,
        String name,
        String icon,
        TimetableColor color,
        boolean active,
        long activityCount,
        Instant createdAt,
        Instant updatedAt) {}
