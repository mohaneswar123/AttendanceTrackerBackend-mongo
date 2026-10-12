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
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.dto.CopyDayRequest;
import com.AttendanceRegister.sdc.dto.TimetableActivityRequest;
import com.AttendanceRegister.sdc.dto.TimetableActivityResponse;
import com.AttendanceRegister.sdc.dto.TimetableModeRequest;
import com.AttendanceRegister.sdc.dto.TimetableModeResponse;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.TimetableService;
import com.AttendanceRegister.sdc.service.UserAuditService;

// The signed-in student's timetable modes and their weekly activities
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableService timetableService;
    private final AccessGuard accessGuard;
    private final UserAuditService audit;

    public TimetableController(TimetableService timetableService, AccessGuard accessGuard, UserAuditService audit) {
        this.timetableService = timetableService;
        this.accessGuard = accessGuard;
        this.audit = audit;
    }

    // ✅ All of the student's modes, in order, with their activity counts
    @GetMapping("/modes")
    public ResponseEntity<List<TimetableModeResponse>> getModes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(timetableService.getModes(studentId(jwt)));
    }

    // ✅ Create a mode; the student's first one becomes active
    @PostMapping("/modes")
    public ResponseEntity<TimetableModeResponse> createMode(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TimetableModeRequest request) {

        String userId = studentId(jwt);
        TimetableModeResponse mode = timetableService.createMode(userId, request);
        audited(userId, UserActionType.CREATE);
        return ResponseEntity.ok(mode);
    }

    @PutMapping("/modes/{modeId}")
    public ResponseEntity<TimetableModeResponse> updateMode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId,
            @RequestBody TimetableModeRequest request) {

        String userId = studentId(jwt);
        TimetableModeResponse mode = timetableService.updateMode(userId, modeId, request);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(mode);
    }

    // ✅ Make this the active mode; returns every mode with its new state
    @PutMapping("/modes/{modeId}/activate")
    public ResponseEntity<List<TimetableModeResponse>> activateMode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId) {

        String userId = studentId(jwt);
        List<TimetableModeResponse> modes = timetableService.activateMode(userId, modeId);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(modes);
    }

    // ✅ Delete the mode with its whole week
    @DeleteMapping("/modes/{modeId}")
    public ResponseEntity<List<TimetableModeResponse>> deleteMode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId) {

        String userId = studentId(jwt);
        List<TimetableModeResponse> modes = timetableService.deleteMode(userId, modeId);
        audited(userId, UserActionType.DELETE);
        return ResponseEntity.ok(modes);
    }

    // ✅ That mode's week, ordered by day then start time
    @GetMapping("/modes/{modeId}/activities")
    public ResponseEntity<List<TimetableActivityResponse>> getActivities(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId) {

        return ResponseEntity.ok(timetableService.getActivities(studentId(jwt), modeId));
    }

    @PostMapping("/modes/{modeId}/activities")
    public ResponseEntity<TimetableActivityResponse> createActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId,
            @RequestBody TimetableActivityRequest request) {

        String userId = studentId(jwt);
        TimetableActivityResponse activity = timetableService.createActivity(userId, modeId, request);
        audited(userId, UserActionType.CREATE);
        return ResponseEntity.ok(activity);
    }

    @PutMapping("/activities/{activityId}")
    public ResponseEntity<TimetableActivityResponse> updateActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String activityId,
            @RequestBody TimetableActivityRequest request) {

        String userId = studentId(jwt);
        TimetableActivityResponse activity = timetableService.updateActivity(userId, activityId, request);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(activity);
    }

    @DeleteMapping("/activities/{activityId}")
    public ResponseEntity<Void> deleteActivity(@AuthenticationPrincipal Jwt jwt, @PathVariable String activityId) {
        String userId = studentId(jwt);
        timetableService.deleteActivity(userId, activityId);
        audited(userId, UserActionType.DELETE);
        return ResponseEntity.noContent().build();
    }

    // ✅ Replace other days with a copy of one day
    @PostMapping("/modes/{modeId}/copy-day")
    public ResponseEntity<List<TimetableActivityResponse>> copyDay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId,
            @RequestBody CopyDayRequest request) {

        String userId = studentId(jwt);
        List<TimetableActivityResponse> activities = timetableService.copyDay(userId, modeId, request);
        audited(userId, UserActionType.CREATE);
        return ResponseEntity.ok(activities);
    }

    // Only a student reaches these, so an admin is never the one acting.
    // Modes and activities are both "the timetable"; the log does not say which.
    private void audited(String userId, UserActionType action) {
        audit.record(userId, UserActionFeature.TIMETABLE, action, false);
    }

    private String studentId(Jwt jwt) {
        return accessGuard.requireActiveStudent(jwt).getId();
    }
}
