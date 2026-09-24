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
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.TimetableService;

// The signed-in student's timetable modes and their weekly activities
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableService timetableService;
    private final AccessGuard accessGuard;

    public TimetableController(TimetableService timetableService, AccessGuard accessGuard) {
        this.timetableService = timetableService;
        this.accessGuard = accessGuard;
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

        return ResponseEntity.ok(timetableService.createMode(studentId(jwt), request));
    }

    @PutMapping("/modes/{modeId}")
    public ResponseEntity<TimetableModeResponse> updateMode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId,
            @RequestBody TimetableModeRequest request) {

        return ResponseEntity.ok(timetableService.updateMode(studentId(jwt), modeId, request));
    }

    // ✅ Make this the active mode; returns every mode with its new state
    @PutMapping("/modes/{modeId}/activate")
    public ResponseEntity<List<TimetableModeResponse>> activateMode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId) {

        return ResponseEntity.ok(timetableService.activateMode(studentId(jwt), modeId));
    }

    // ✅ Delete the mode with its whole week
    @DeleteMapping("/modes/{modeId}")
    public ResponseEntity<List<TimetableModeResponse>> deleteMode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId) {

        return ResponseEntity.ok(timetableService.deleteMode(studentId(jwt), modeId));
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

        return ResponseEntity.ok(timetableService.createActivity(studentId(jwt), modeId, request));
    }

    @PutMapping("/activities/{activityId}")
    public ResponseEntity<TimetableActivityResponse> updateActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String activityId,
            @RequestBody TimetableActivityRequest request) {

        return ResponseEntity.ok(timetableService.updateActivity(studentId(jwt), activityId, request));
    }

    @DeleteMapping("/activities/{activityId}")
    public ResponseEntity<Void> deleteActivity(@AuthenticationPrincipal Jwt jwt, @PathVariable String activityId) {
        timetableService.deleteActivity(studentId(jwt), activityId);
        return ResponseEntity.noContent().build();
    }

    // ✅ Replace other days with a copy of one day
    @PostMapping("/modes/{modeId}/copy-day")
    public ResponseEntity<List<TimetableActivityResponse>> copyDay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String modeId,
            @RequestBody CopyDayRequest request) {

        return ResponseEntity.ok(timetableService.copyDay(studentId(jwt), modeId, request));
    }

    private String studentId(Jwt jwt) {
        return accessGuard.requireActiveStudent(jwt).getId();
    }
}
