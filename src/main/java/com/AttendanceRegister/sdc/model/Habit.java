package com.AttendanceRegister.sdc.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One habit a student is trying to keep.
 *
 * Habits are archived rather than deleted, so the history behind a streak is never thrown
 * away by tidying up. The exception is a habit with no entries at all: there is nothing to
 * keep, so it can be removed outright.
 */
@Document(collection = "habit_table")
public class Habit {

    @Id
    private String id;

    private String userId;

    private String name;

    /** A short emoji shown next to the name */
    private String icon;

    /** The same six-colour palette the timetable modes use */
    private TimetableColor color;

    private HabitCategory category;

    /** Optional: why this matters, shown when the student is deciding whether to skip */
    private String reason;

    private HabitSchedule schedule;

    /** Which days, when the schedule is CUSTOM_DAYS */
    private Set<DayOfWeek> weekdays = new LinkedHashSet<>();

    /** How many times a week, when the schedule is TIMES_PER_WEEK */
    private int timesPerWeek;

    private HabitGoal goal;

    /** How many to reach for COUNT, or how many minutes for DURATION; 1 for YES_NO */
    private int target;

    /** What the count is counted in, such as "glasses". Only meaningful for COUNT. */
    private String unit;

    /** "HH:mm", or null when the student turned the reminder off */
    private String reminderTime;

    private boolean archived;

    /** Order in the student's list */
    private double position;

    /** The first day this habit counts from, so a streak cannot start before it existed */
    private LocalDate startDate;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant archivedAt;

    public Habit() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public TimetableColor getColor() {
        return color;
    }

    public void setColor(TimetableColor color) {
        this.color = color;
    }

    public HabitCategory getCategory() {
        return category;
    }

    public void setCategory(HabitCategory category) {
        this.category = category;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public HabitSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(HabitSchedule schedule) {
        this.schedule = schedule;
    }

    public Set<DayOfWeek> getWeekdays() {
        return weekdays;
    }

    public void setWeekdays(Set<DayOfWeek> weekdays) {
        this.weekdays = weekdays == null ? new LinkedHashSet<>() : weekdays;
    }

    public int getTimesPerWeek() {
        return timesPerWeek;
    }

    public void setTimesPerWeek(int timesPerWeek) {
        this.timesPerWeek = timesPerWeek;
    }

    public HabitGoal getGoal() {
        return goal;
    }

    public void setGoal(HabitGoal goal) {
        this.goal = goal;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(String reminderTime) {
        this.reminderTime = reminderTime;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    /** True when this habit is meant to happen on the given weekday */
    public boolean fallsOn(DayOfWeek day) {
        return switch (schedule) {
            case DAILY -> true;
            case WEEKDAYS -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            case CUSTOM_DAYS -> weekdays.contains(day);
            // Any day will do; which days are due is decided by what is left in the week
            case TIMES_PER_WEEK -> true;
        };
    }
}
