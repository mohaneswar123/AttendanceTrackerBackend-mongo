package com.AttendanceRegister.sdc.dto;

import com.AttendanceRegister.sdc.model.TimetableColor;

// Body of POST and PUT /api/timetable/modes
public record TimetableModeRequest(String name, String icon, TimetableColor color) {}
