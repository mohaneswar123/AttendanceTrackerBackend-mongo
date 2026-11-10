package com.AttendanceRegister.sdc.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "subject_table")
public class Subject {

    @Id
    private String id;

    private String name;

    // Reference by ID instead of relational mapping
    private String userId;

    /**
     * The colour the student gave this subject, or null for one saved before colours
     * existed. The page falls back to a colour worked out from the name, which is what it
     * did for every subject before this field.
     */
    private TimetableColor color;

    // 👉 Constructors
    public Subject() {}

    public Subject(String name, String userId) {
        this.name = name;
        this.userId = userId;
    }

    // 👉 Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TimetableColor getColor() {
        return color;
    }

    public void setColor(TimetableColor color) {
        this.color = color;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Subject{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", userId=" + userId +
            '}';
    }
}
