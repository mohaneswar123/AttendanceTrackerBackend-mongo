package com.AttendanceRegister.sdc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.model.Subject;
import com.AttendanceRegister.sdc.service.SubjectService;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    // ✅ Add a new subject for a user
    @PostMapping("/add")
    public ResponseEntity<Subject> addSubject(@RequestParam String userId, @RequestParam String name) {
        Subject created = subjectService.addSubject(userId, name);
        return ResponseEntity.ok(created);
    }

    // ✅ Get all subjects for a specific user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Subject>> getSubjectsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(subjectService.getSubjectsByUser(userId));
    }

    // ✅ Delete a subject by ID
    @DeleteMapping("/{subjectId}/user/{userId}")
    public ResponseEntity<Void> deleteSubjectForUser(
            @PathVariable String subjectId,
            @PathVariable String userId) {
        subjectService.deleteSubjectForUser(subjectId, userId);
        return ResponseEntity.noContent().build();
    }

}
