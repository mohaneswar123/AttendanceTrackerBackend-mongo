package com.AttendanceRegister.sdc.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.dto.PlanDayRequest;
import com.AttendanceRegister.sdc.model.AttendancePlan.PlannedClass;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.AttendancePlanService;
import com.AttendanceRegister.sdc.service.UserAuditService;

// The signed-in student's weekly class plan, behind the Record checklist
@RestController
@RequestMapping("/api/attendance/plan")
public class AttendancePlanController {

    private final AttendancePlanService planService;
    private final AccessGuard accessGuard;
    private final UserAuditService audit;

    public AttendancePlanController(AttendancePlanService planService, AccessGuard accessGuard,
            UserAuditService audit) {
        this.planService = planService;
        this.accessGuard = accessGuard;
        this.audit = audit;
    }

    // ✅ The whole week: { "MONDAY": [{subjectId, hours}], ... }; unplanned days are absent
    @GetMapping
    public ResponseEntity<Map<String, List<PlannedClass>>> getPlan(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(planService.getPlan(studentId(jwt)));
    }

    // ✅ Replace one weekday whole; an empty list clears the day
    @PutMapping("/{weekday}")
    public ResponseEntity<Map<String, List<PlannedClass>>> setDay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String weekday,
            @RequestBody PlanDayRequest request) {

        String userId = studentId(jwt);
        Map<String, List<PlannedClass>> plan = planService.setDay(userId, weekday, request);
        audit.record(userId, UserActionFeature.ATTENDANCE, UserActionType.UPDATE, false);
        return ResponseEntity.ok(plan);
    }

    private String studentId(Jwt jwt) {
        return accessGuard.requireActiveStudent(jwt).getId();
    }
}
