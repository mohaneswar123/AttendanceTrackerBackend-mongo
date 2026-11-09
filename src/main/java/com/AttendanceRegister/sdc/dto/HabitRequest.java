package com.AttendanceRegister.sdc.dto;

import java.time.DayOfWeek;
import java.util.Set;

import com.AttendanceRegister.sdc.model.HabitCategory;
import com.AttendanceRegister.sdc.model.HabitGoal;
import com.AttendanceRegister.sdc.model.HabitSchedule;
import com.AttendanceRegister.sdc.model.TimetableColor;

/**
 * Body of POST and PUT for a habit.
 *
 * Which of the optional fields matter depends on the other two choices: weekdays only for
 * a WEEKDAYS schedule, timesPerWeek only for TIMES_PER_WEEK, target and unit only for a
 * COUNT or DURATION goal. The service checks that what arrives makes sense together.
 * reminderTime is "HH:mm", or null for no reminder, and startDate is "YYYY-MM-DD",
 * defaulting to today.
 */
public record HabitRequest(
        String name,
        String icon,
        TimetableColor color,
        HabitCategory category,
        String reason,
        HabitSchedule schedule,
        Set<DayOfWeek> weekdays,
        Integer timesPerWeek,
        HabitGoal goal,
        Integer target,
        String unit,
        String reminderTime,
        String startDate) {
}
