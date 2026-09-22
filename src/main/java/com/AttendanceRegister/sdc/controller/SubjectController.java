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
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.SubjectService;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;
    private final AccessGuard accessGuard;

    public SubjectController(SubjectService subjectService, AccessGuard accessGuard) {
        this.subjectService = subjectService;
        this.accessGuard = accessGuard;
    }

    // ✅ Add a new subject for a user
    @PostMapping("/add")
    public ResponseEntity<Subject> addSubject(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String userId,
            @RequestParam String name) {

        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        return ResponseEntity.ok(subjectService.addSubject(userId, name));
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
        return ResponseEntity.noContent().build();
    }

}
