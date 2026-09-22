package com.AttendanceRegister.sdc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.dto.MoveTaskRequest;
import com.AttendanceRegister.sdc.dto.TaskRequest;
import com.AttendanceRegister.sdc.model.Task;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.TaskService;

// The signed-in student's Kanban tasks
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final AccessGuard accessGuard;

    public TaskController(TaskService taskService, AccessGuard accessGuard) {
        this.taskService = taskService;
        this.accessGuard = accessGuard;
    }

    // ✅ Tasks dated from..to (inclusive); leave both out for all tasks
    @GetMapping
    public ResponseEntity<List<Task>> getTasks(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        String userId = accessGuard.requireActiveStudent(jwt).getId();
        return ResponseEntity.ok(taskService.getTasks(userId, from, to));
    }

    // ✅ Add a task to the bottom of To Do
    @PostMapping
    public ResponseEntity<Task> createTask(@AuthenticationPrincipal Jwt jwt, @RequestBody TaskRequest request) {
        String userId = accessGuard.requireActiveStudent(jwt).getId();
        return ResponseEntity.ok(taskService.createTask(userId, request));
    }

    // ✅ Edit title, date and priority
    @PutMapping("/{taskId}")
    public ResponseEntity<Task> updateTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String taskId,
            @RequestBody TaskRequest request) {

        String userId = accessGuard.requireActiveStudent(jwt).getId();
        return ResponseEntity.ok(taskService.updateTask(userId, taskId, request));
    }

    // ✅ Move to a column and/or a new place in it
    @PutMapping("/{taskId}/move")
    public ResponseEntity<Task> moveTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String taskId,
            @RequestBody MoveTaskRequest request) {

        String userId = accessGuard.requireActiveStudent(jwt).getId();
        return ResponseEntity.ok(taskService.moveTask(userId, taskId, request));
    }

    // ✅ Delete a task
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId) {
        String userId = accessGuard.requireActiveStudent(jwt).getId();
        taskService.deleteTask(userId, taskId);
        return ResponseEntity.noContent().build();
    }
}
