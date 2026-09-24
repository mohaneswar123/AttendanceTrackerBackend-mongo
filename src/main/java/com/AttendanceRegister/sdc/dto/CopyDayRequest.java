package com.AttendanceRegister.sdc.dto;

import java.time.DayOfWeek;
import java.util.List;

// Body of POST /api/timetable/modes/{id}/copy-day: each target day is replaced by a
// copy of the source day
public record CopyDayRequest(DayOfWeek fromDay, List<DayOfWeek> toDays) {}
