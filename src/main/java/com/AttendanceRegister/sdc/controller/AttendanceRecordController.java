package com.AttendanceRegister.sdc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.AttendanceRegister.sdc.model.AttendanceRecord;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.AttendanceRecordService;
import com.AttendanceRegister.sdc.service.UserAuditService;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceRecordController {

    private final AttendanceRecordService attendanceService;
    private final AccessGuard accessGuard;
    private final UserAuditService audit;

    public AttendanceRecordController(AttendanceRecordService attendanceService, AccessGuard accessGuard,
            UserAuditService audit) {
        this.attendanceService = attendanceService;
        this.accessGuard = accessGuard;
        this.audit = audit;
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
        audit.record(userId, UserActionFeature.ATTENDANCE, UserActionType.CREATE, accessGuard.isAdmin(jwt));
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
        AttendanceRecord saved = attendanceService.updateRecord(existing, updatedRecord);
        audit.record(existing.getUserId(), UserActionFeature.ATTENDANCE, UserActionType.UPDATE, accessGuard.isAdmin(jwt));
        return ResponseEntity.ok(saved);
    }

    // ✅ Delete a specific attendance record by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        AttendanceRecord existing = attendanceService.getRecord(id);
        accessGuard.requireActiveSelfOrAdmin(jwt, existing.getUserId());
        attendanceService.deleteRecord(existing);
        audit.record(existing.getUserId(), UserActionFeature.ATTENDANCE, UserActionType.DELETE, accessGuard.isAdmin(jwt));
        return ResponseEntity.noContent().build();
    }
}
