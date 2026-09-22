package com.AttendanceRegister.sdc.dto;

import com.AttendanceRegister.sdc.model.TaskStatus;

// Body of PUT /api/tasks/{id}/move: the destination column and the cards the task
// should sit between, as the student sees them. Either neighbour may be null.
public record MoveTaskRequest(TaskStatus status, String afterTaskId, String beforeTaskId) {}
