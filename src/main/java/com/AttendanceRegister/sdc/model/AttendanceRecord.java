package com.AttendanceRegister.sdc.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "attendance_table")
public class AttendanceRecord {

    @Id
    private String id;

    private String status;

    private String date;

    // ✅ New field to track multiple classes per day
    private int classNumber;

    // Reference IDs instead of JPA relations
    private String userId;

    private String subjectId;

    // 👉 Constructors
    public AttendanceRecord() {}

    public AttendanceRecord(String status, String date, int classNumber, String userId, String subjectId) {
        this.status = status;
        this.date = date;
        this.classNumber = classNumber;
        this.userId = userId;
        this.subjectId = subjectId;
    }

    // 👉 Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getClassNumber() {
        return classNumber;
    }

    public void setClassNumber(int classNumber) {
        this.classNumber = classNumber;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    @Override
    public String toString() {
        return "AttendanceRecord{" +
            "id=" + id +
            ", status='" + status + '\'' +
            ", date='" + date + '\'' +
            ", classNumber=" + classNumber +
            ", userId=" + userId +
            ", subjectId=" + subjectId +
            '}';
    }
}
