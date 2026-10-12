package com.AttendanceRegister.sdc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.model.Subject;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.SubjectService;
import com.AttendanceRegister.sdc.service.UserAuditService;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;
    private final AccessGuard accessGuard;
    private final UserAuditService audit;

    public SubjectController(SubjectService subjectService, AccessGuard accessGuard, UserAuditService audit) {
        this.subjectService = subjectService;
        this.accessGuard = accessGuard;
        this.audit = audit;
    }

    // ✅ Add a new subject for a user
    @PostMapping("/add")
    public ResponseEntity<Subject> addSubject(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String userId,
            @RequestParam String name) {

        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        Subject subject = subjectService.addSubject(userId, name);
        audit.record(userId, UserActionFeature.SUBJECT, UserActionType.CREATE, accessGuard.isAdmin(jwt));
        return ResponseEntity.ok(subject);
    }

    // ✅ Get all subjects for a specific user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Subject>> getSubjectsByUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        return ResponseEntity.ok(subjectService.getSubjectsByUser(userId));
    }

    // ✅ Delete a subject and its attendance records
    @DeleteMapping("/{subjectId}/user/{userId}")
    public ResponseEntity<Void> deleteSubjectForUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String subjectId,
            @PathVariable String userId) {

        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        subjectService.deleteSubjectForUser(subjectId, userId);
        audit.record(userId, UserActionFeature.SUBJECT, UserActionType.DELETE, accessGuard.isAdmin(jwt));
        return ResponseEntity.noContent().build();
    }

}
