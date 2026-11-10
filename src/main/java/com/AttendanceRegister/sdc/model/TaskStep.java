package com.AttendanceRegister.sdc.model;

/**
 * One step of a task, such as "find the references" under "write the essay".
 *
 * Steps are embedded in the task rather than kept in their own collection: they are only
 * ever read and written with the task they belong to, and a task with more than a handful
 * of steps wants to be two tasks.
 */
public class TaskStep {

    private String id;

    private String title;

    private boolean done;

    public TaskStep() {
    }

    public TaskStep(String id, String title, boolean done) {
        this.id = id;
        this.title = title;
        this.done = done;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
