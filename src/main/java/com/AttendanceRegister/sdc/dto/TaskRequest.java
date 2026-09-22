package com.AttendanceRegister.sdc.dto;

import com.AttendanceRegister.sdc.model.TaskPriority;

// Body of POST /api/tasks and PUT /api/tasks/{id}; priority may be null
public record TaskRequest(String title, String taskDate, TaskPriority priority) {}
