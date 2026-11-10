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

import com.AttendanceRegister.sdc.dto.HabitEntryRequest;
import com.AttendanceRegister.sdc.dto.HabitEntryResponse;
import com.AttendanceRegister.sdc.dto.HabitProgressResponse;
import com.AttendanceRegister.sdc.dto.HabitRequest;
import com.AttendanceRegister.sdc.dto.HabitResponse;
import com.AttendanceRegister.sdc.dto.HabitSummaryResponse;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.HabitService;
import com.AttendanceRegister.sdc.service.UserAuditService;

// The signed-in student's habits, and how well they are being kept
@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService habitService;
    private final AccessGuard accessGuard;
    private final UserAuditService audit;

    public HabitController(HabitService habitService, AccessGuard accessGuard, UserAuditService audit) {
        this.habitService = habitService;
        this.accessGuard = accessGuard;
        this.audit = audit;
    }

    // ✅ Every habit with its streaks and rates; archived ones only when asked for
    @GetMapping
    public ResponseEntity<List<HabitResponse>> getHabits(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        return ResponseEntity.ok(habitService.getHabits(studentId(jwt), includeArchived));
    }

    // ✅ The marked days in a range, for the month grid
    @GetMapping("/entries")
    public ResponseEntity<List<HabitEntryResponse>> getEntries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String from,
            @RequestParam String to) {

        return ResponseEntity.ok(habitService.getEntries(studentId(jwt), from, to));
    }

    // ✅ Last week in review, with one suggestion
    @GetMapping("/summary")
    public ResponseEntity<HabitSummaryResponse> summary(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(habitService.weeklySummary(studentId(jwt)));
    }

    /**
     * How the habits went this week, this month or this year. Anything other than those
     * three is a mistake worth saying out loud rather than quietly answering for a week.
     */
    @GetMapping("/progress")
    public ResponseEntity<HabitProgressResponse> progress(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "WEEK") String range) {

        HabitService.ProgressRange chosen;
        try {
            chosen = HabitService.ProgressRange.valueOf(range.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw ApiException.badRequest("Range must be WEEK, MONTH or YEAR");
        }
        return ResponseEntity.ok(habitService.progress(studentId(jwt), chosen));
    }

    @PostMapping
    public ResponseEntity<HabitResponse> createHabit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody HabitRequest request) {

        String userId = studentId(jwt);
        HabitResponse habit = habitService.createHabit(userId, request);
        audited(userId, UserActionType.CREATE);
        return ResponseEntity.ok(habit);
    }

    @PutMapping("/{habitId}")
    public ResponseEntity<HabitResponse> updateHabit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String habitId,
            @RequestBody HabitRequest request) {

        String userId = studentId(jwt);
        HabitResponse habit = habitService.updateHabit(userId, habitId, request);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(habit);
    }

    // ✅ Archive keeps the history; the habit simply stops being due
    @PutMapping("/{habitId}/archive")
    public ResponseEntity<HabitResponse> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable String habitId) {
        String userId = studentId(jwt);
        HabitResponse habit = habitService.setArchived(userId, habitId, true);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(habit);
    }

    @PutMapping("/{habitId}/unarchive")
    public ResponseEntity<HabitResponse> unarchive(@AuthenticationPrincipal Jwt jwt, @PathVariable String habitId) {
        String userId = studentId(jwt);
        HabitResponse habit = habitService.setArchived(userId, habitId, false);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(habit);
    }

    // ✅ Only a habit that was never marked; anything else is 409 HABIT_HAS_HISTORY
    @DeleteMapping("/{habitId}")
    public ResponseEntity<Void> deleteHabit(@AuthenticationPrincipal Jwt jwt, @PathVariable String habitId) {
        String userId = studentId(jwt);
        habitService.deleteHabit(userId, habitId);
        audited(userId, UserActionType.DELETE);
        return ResponseEntity.noContent().build();
    }

    // ✅ Mark one day done, missed or skipped; marking it again replaces the mark
    @PutMapping("/{habitId}/entries/{date}")
    public ResponseEntity<HabitResponse> mark(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String habitId,
            @PathVariable String date,
            @RequestBody HabitEntryRequest request) {

        String userId = studentId(jwt);
        HabitResponse habit = habitService.mark(userId, habitId, date, request);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(habit);
    }

    // ✅ Undo a mark, leaving the day untouched
    @DeleteMapping("/{habitId}/entries/{date}")
    public ResponseEntity<HabitResponse> clearMark(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String habitId,
            @PathVariable String date) {

        String userId = studentId(jwt);
        HabitResponse habit = habitService.clearMark(userId, habitId, date);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(habit);
    }

    // Only a student reaches these, so an admin is never the one acting
    private void audited(String userId, UserActionType action) {
        audit.record(userId, UserActionFeature.HABIT, action, false);
    }

    private String studentId(Jwt jwt) {
        return accessGuard.requireActiveStudent(jwt).getId();
    }
}
