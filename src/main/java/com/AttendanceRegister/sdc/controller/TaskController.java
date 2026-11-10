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
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.TaskService;
import com.AttendanceRegister.sdc.service.UserAuditService;

// The signed-in student's to-do list
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final AccessGuard accessGuard;
    private final UserAuditService audit;

    public TaskController(TaskService taskService, AccessGuard accessGuard, UserAuditService audit) {
        this.taskService = taskService;
        this.accessGuard = accessGuard;
        this.audit = audit;
    }

    // Only a student reaches these, so an admin is never the one acting
    private void audited(String userId, UserActionType action) {
        audit.record(userId, UserActionFeature.TASK, action, false);
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

    // ✅ Add a task to the bottom of the list
    @PostMapping
    public ResponseEntity<Task> createTask(@AuthenticationPrincipal Jwt jwt, @RequestBody TaskRequest request) {
        String userId = accessGuard.requireActiveStudent(jwt).getId();
        Task task = taskService.createTask(userId, request);
        audited(userId, UserActionType.CREATE);
        return ResponseEntity.ok(task);
    }

    // ✅ Edit the title, the day, the star, the note and the steps
    @PutMapping("/{taskId}")
    public ResponseEntity<Task> updateTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String taskId,
            @RequestBody TaskRequest request) {

        String userId = accessGuard.requireActiveStudent(jwt).getId();
        Task task = taskService.updateTask(userId, taskId, request);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(task);
    }

    // ✅ Tick a task off, or put it back. One tap, so it is its own call.
    @PutMapping("/{taskId}/done")
    public ResponseEntity<Task> markDone(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId) {
        String userId = accessGuard.requireActiveStudent(jwt).getId();
        Task task = taskService.setDone(userId, taskId, true);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/{taskId}/done")
    public ResponseEntity<Task> markNotDone(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId) {
        String userId = accessGuard.requireActiveStudent(jwt).getId();
        Task task = taskService.setDone(userId, taskId, false);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(task);
    }

    // ✅ Drag to a new place in the list
    @PutMapping("/{taskId}/move")
    public ResponseEntity<Task> moveTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String taskId,
            @RequestBody MoveTaskRequest request) {

        String userId = accessGuard.requireActiveStudent(jwt).getId();
        Task task = taskService.moveTask(userId, taskId, request);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(task);
    }

    // ✅ Delete a task
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId) {
        String userId = accessGuard.requireActiveStudent(jwt).getId();
        taskService.deleteTask(userId, taskId);
        audited(userId, UserActionType.DELETE);
        return ResponseEntity.noContent().build();
    }
}
