package com.AttendanceRegister.sdc.dto;

import java.time.DayOfWeek;
import java.util.Set;

import com.AttendanceRegister.sdc.model.HabitCategory;
import com.AttendanceRegister.sdc.model.HabitGoal;
import com.AttendanceRegister.sdc.model.HabitSchedule;
import com.AttendanceRegister.sdc.model.TimetableColor;

/**
 * A habit as the page sees it: what the student set up, plus everything that has to be
 * worked out from its history. The streaks and rates are computed on the server so the
 * page and the weekly summary can never disagree about them.
 *
 * {@code completionRate} values are whole percentages, and -1 means nothing was due in
 * that week, which reads differently from 0%.
 */
public record HabitResponse(
        String id,
        String name,
        String icon,
        TimetableColor color,
        HabitCategory category,
        String reason,
        HabitSchedule schedule,
        Set<DayOfWeek> weekdays,
        int timesPerWeek,
        HabitGoal goal,
        int target,
        String unit,
        String reminderTime,
        boolean archived,
        boolean dueToday,
        HabitEntryResponse today,
        int currentStreak,
        int bestStreak,
        int doneThisWeek,
        int thisWeekRate,
        int lastWeekRate,
        int last30Rate,
        String startDate) {
}
