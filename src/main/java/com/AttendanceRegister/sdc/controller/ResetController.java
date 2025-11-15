package com.AttendanceRegister.sdc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.service.ResetService;

@RestController
@RequestMapping("/api/reset")
public class ResetController {

    private final ResetService resetService;

    public ResetController(ResetService resetService) {
        this.resetService = resetService;
    }

    // ✅ Reset attendance and subject data for a specific user
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> resetUserData(@PathVariable String userId) {
        resetService.resetUserData(userId);
        return ResponseEntity.noContent().build();
    }
}
