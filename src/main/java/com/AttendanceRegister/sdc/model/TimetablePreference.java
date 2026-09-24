package com.AttendanceRegister.sdc.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One document per student holding which timetable mode is active. Because that is a
// single field written by a single update, two activations at once can't both win.
@Document(collection = "timetable_preference_table")
public class TimetablePreference {

    @Id
    private String id;

    // Unique; the index is created at startup by config/TimetableIndexes
    private String userId;

    // null when the student has no modes
    private String activeModeId;

    private Instant updatedAt;

    public TimetablePreference() {}

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

    public String getActiveModeId() {
        return activeModeId;
    }

    public void setActiveModeId(String activeModeId) {
        this.activeModeId = activeModeId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
