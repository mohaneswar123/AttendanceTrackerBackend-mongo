package com.AttendanceRegister.sdc.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The student's weekly class plan: which subjects have class on which weekday, and for
 * how long. One document per student.
 *
 * This is Attendance's own plan, deliberately separate from the Timetable feature. A
 * timetable activity is a block of time with a title; a planned class is a reference to a
 * subject that attendance is counted against. Tying the two together by name would break
 * the moment a subject was renamed, and the features are kept unlinked by design.
 *
 * Keys are {@link java.time.DayOfWeek} names ("MONDAY".."SUNDAY"). The list order is the
 * order of classes in the day, and the same subject may appear more than once — two
 * maths classes on a Tuesday are two entries.
 */
@Document(collection = "attendance_plan_table")
public class AttendancePlan {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private Map<String, List<PlannedClass>> days = new HashMap<>();

    public AttendancePlan() {
    }

    public AttendancePlan(String userId) {
        this.userId = userId;
    }

    /** One class in the plan: the subject and how many hours it runs (1-3). */
    public static class PlannedClass {
        private String subjectId;
        private int hours;

        public PlannedClass() {
        }

        public PlannedClass(String subjectId, int hours) {
            this.subjectId = subjectId;
            this.hours = hours;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public void setSubjectId(String subjectId) {
            this.subjectId = subjectId;
        }

        public int getHours() {
            return hours;
        }

        public void setHours(int hours) {
            this.hours = hours;
        }
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

    public Map<String, List<PlannedClass>> getDays() {
        return days;
    }

    public void setDays(Map<String, List<PlannedClass>> days) {
        this.days = days == null ? new HashMap<>() : days;
    }
}
