package com.AttendanceRegister.sdc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.ResetService;

@RestController
@RequestMapping("/api/reset")
public class ResetController {

    private final ResetService resetService;
    private final AccessGuard accessGuard;

    public ResetController(ResetService resetService, AccessGuard accessGuard) {
        this.resetService = resetService;
        this.accessGuard = accessGuard;
    }

    // ✅ Reset attendance and subject data for a specific user
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> resetUserData(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        resetService.resetUserData(userId);
        return ResponseEntity.noContent().build();
    }
}
