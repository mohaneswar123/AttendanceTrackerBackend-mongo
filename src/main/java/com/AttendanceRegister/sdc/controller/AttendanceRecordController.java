package com.AttendanceRegister.sdc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.AttendanceRegister.sdc.model.AttendanceRecord;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.AttendanceRecordService;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceRecordController {

    private final AttendanceRecordService attendanceService;
    private final AccessGuard accessGuard;

    public AttendanceRecordController(AttendanceRecordService attendanceService, AccessGuard accessGuard) {
        this.attendanceService = attendanceService;
        this.accessGuard = accessGuard;
    }

    // ✅ Add a new attendance record; classNumber is the class length in hours
    @PostMapping("/add")
    public ResponseEntity<AttendanceRecord> addRecord(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String userId,
            @RequestParam String subjectId,
            @RequestParam String status,
            @RequestParam String date,
            @RequestParam int classNumber) {

        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        AttendanceRecord record = attendanceService.addRecord(userId, subjectId, status, date, classNumber);
        return ResponseEntity.ok(record);
    }

    // ✅ Get all attendance records for a specific user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AttendanceRecord>> getRecordsByUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        return ResponseEntity.ok(attendanceService.getRecordsByUser(userId));
    }

    // ✅ Update a specific attendance record
    @PutMapping("/{id}")
    public ResponseEntity<AttendanceRecord> updateRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestBody AttendanceRecord updatedRecord) {

        AttendanceRecord existing = attendanceService.getRecord(id);
        accessGuard.requireActiveSelfOrAdmin(jwt, existing.getUserId());
        return ResponseEntity.ok(attendanceService.updateRecord(existing, updatedRecord));
    }

    // ✅ Delete a specific attendance record by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        AttendanceRecord existing = attendanceService.getRecord(id);
        accessGuard.requireActiveSelfOrAdmin(jwt, existing.getUserId());
        attendanceService.deleteRecord(existing);
        return ResponseEntity.noContent().build();
    }
}
