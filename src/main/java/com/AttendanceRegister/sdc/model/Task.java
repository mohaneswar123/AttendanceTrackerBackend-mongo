package com.AttendanceRegister.sdc.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "task_table")
public class Task {

    @Id
    private String id;

    private String userId;

    private String title;

    // "YYYY-MM-DD", the day the task is planned for
    private String taskDate;

    private TaskStatus status;

    /**
     * From when this was a Kanban board. Nothing sets it any more; it stays so the one
     * thing it was used for, marking a task as worth doing first, can still be read back
     * as {@code important} for tasks saved before the star existed.
     */
    private TaskPriority priority;

    /** Starred, so it sits at the top and appears under Important */
    private Boolean important;

    /** Anything that does not fit in the title: a link, a page number, a reminder why */
    private String note;

    /** Checkable steps within the task, in the order the student put them */
    private List<TaskStep> steps = new ArrayList<>();

    // Order in the list; smaller comes first
    private double position;

    private Instant createdAt;

    private Instant updatedAt;

    // Set while the task is in DONE
    private Instant completedAt;

    public Task() {}

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTaskDate() {
        return taskDate;
    }

    public void setTaskDate(String taskDate) {
        this.taskDate = taskDate;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    /**
     * Starred or not. Tasks saved before the star existed fall back to whether they were
     * marked high priority, which is the same idea under the old name.
     */
    public boolean isImportant() {
        return important != null ? important : priority == TaskPriority.HIGH;
    }

    public void setImportant(boolean important) {
        this.important = important;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<TaskStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TaskStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }

    /** True once the task has been ticked off */
    public boolean isDone() {
        return status == TaskStatus.DONE;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
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

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
