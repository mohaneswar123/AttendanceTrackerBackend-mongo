package com.AttendanceRegister.sdc.model;

/**
 * Whether a task is finished.
 *
 * IN_PROGRESS is from when this was a Kanban board. Nothing writes it any more, but the
 * value stays so tasks saved back then still load; the service reads it as not done.
 */
public enum TaskStatus {
    TODO,
    /** @deprecated kept only so tasks from the old board still load */
    @Deprecated
    IN_PROGRESS,
    DONE
}
