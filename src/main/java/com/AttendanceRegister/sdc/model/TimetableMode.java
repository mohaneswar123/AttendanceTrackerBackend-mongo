package com.AttendanceRegister.sdc.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// A reusable weekly routine, such as College Mode. Which mode is active is not stored
// here but in the student's single TimetablePreference, so it can only have one value.
@Document(collection = "timetable_mode_table")
public class TimetableMode {

    @Id
    private String id;

    private String userId;

    private String name;

    // A short emoji shown next to the name
    private String icon;

    private TimetableColor color;

    // Order in the student's list of modes
    private double position;

    private Instant createdAt;

    private Instant updatedAt;

    public TimetableMode() {}

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

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
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
