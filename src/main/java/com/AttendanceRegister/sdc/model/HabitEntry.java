package com.AttendanceRegister.sdc.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * How one day went for one habit. At most one of these exists per habit per date, which a
 * unique index enforces, so marking the same day twice replaces the mark rather than
 * leaving two contradictory ones.
 */
@Document(collection = "habit_entry_table")
public class HabitEntry {

    @Id
    private String id;

    private String userId;

    private String habitId;

    /** The day being marked, in the student's own local date */
    private LocalDate date;

    private HabitEntryStatus status;

    /** How much was done, for COUNT and DURATION habits; 1 for a done YES_NO */
    private int amount;

    /** The student's own words about the day, such as "exam week" */
    private String note;

    private Instant createdAt;

    private Instant updatedAt;

    public HabitEntry() {
    }

    public HabitEntry(String userId, String habitId, LocalDate date, Instant now) {
        this.userId = userId;
        this.habitId = habitId;
        this.date = date;
        this.createdAt = now;
        this.updatedAt = now;
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

    public String getHabitId() {
        return habitId;
    }

    public void setHabitId(String habitId) {
        this.habitId = habitId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public HabitEntryStatus getStatus() {
        return status;
    }

    public void setStatus(HabitEntryStatus status) {
        this.status = status;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
}
