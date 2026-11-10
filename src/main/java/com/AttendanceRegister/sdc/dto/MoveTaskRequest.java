package com.AttendanceRegister.sdc.dto;

/**
 * Body of PUT /api/tasks/{id}/move: where the task should sit, named by the tasks either
 * side of the gap rather than by an index, so two students dragging at once cannot land
 * on the same number.
 */
public record MoveTaskRequest(String afterTaskId, String beforeTaskId) {}
