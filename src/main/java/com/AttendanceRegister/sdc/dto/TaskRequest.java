package com.AttendanceRegister.sdc.dto;

import java.util.List;

/**
 * Body of POST /api/tasks and PUT /api/tasks/{id}.
 *
 * Everything but the title and the date is optional. {@code steps} replaces the task's
 * steps wholesale, which is how the detail sheet saves them: it always sends the list it
 * is showing, so reordering, renaming and ticking are all the same request.
 */
public record TaskRequest(
        String title,
        String taskDate,
        Boolean important,
        String note,
        List<TaskStepRequest> steps) {

    /** {@code id} is null for a step that has just been typed */
    public record TaskStepRequest(String id, String title, Boolean done) {
    }
}
