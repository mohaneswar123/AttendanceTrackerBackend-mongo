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

import com.AttendanceRegister.sdc.dto.CalendarEventRequest;
import com.AttendanceRegister.sdc.dto.CalendarEventResponse;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.CalendarEventService;
import com.AttendanceRegister.sdc.service.UserAuditService;

// The signed-in student's calendar entries
@RestController
@RequestMapping("/api/calendar/events")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;
    private final AccessGuard accessGuard;
    private final UserAuditService audit;

    public CalendarEventController(CalendarEventService calendarEventService, AccessGuard accessGuard,
            UserAuditService audit) {
        this.calendarEventService = calendarEventService;
        this.accessGuard = accessGuard;
        this.audit = audit;
    }

    // ✅ Entries dated from..to (inclusive) and/or whose title contains q; all optional
    @GetMapping
    public ResponseEntity<List<CalendarEventResponse>> getEvents(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q) {

        return ResponseEntity.ok(calendarEventService.getEvents(studentId(jwt), from, to, q));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<CalendarEventResponse> getEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        return ResponseEntity.ok(calendarEventService.getEvent(studentId(jwt), eventId));
    }

    @PostMapping
    public ResponseEntity<CalendarEventResponse> createEvent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CalendarEventRequest request) {

        String userId = studentId(jwt);
        CalendarEventResponse event = calendarEventService.createEvent(userId, request);
        audited(userId, UserActionType.CREATE);
        return ResponseEntity.ok(event);
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<CalendarEventResponse> updateEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String eventId,
            @RequestBody CalendarEventRequest request) {

        String userId = studentId(jwt);
        CalendarEventResponse event = calendarEventService.updateEvent(userId, eventId, request);
        audited(userId, UserActionType.UPDATE);
        return ResponseEntity.ok(event);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        String userId = studentId(jwt);
        calendarEventService.deleteEvent(userId, eventId);
        audited(userId, UserActionType.DELETE);
        return ResponseEntity.noContent().build();
    }

    private String studentId(Jwt jwt) {
        return accessGuard.requireActiveStudent(jwt).getId();
    }

    // Only a student reaches these, so an admin is never the one acting
    private void audited(String userId, UserActionType action) {
        audit.record(userId, UserActionFeature.CALENDAR, action, false);
    }
}
