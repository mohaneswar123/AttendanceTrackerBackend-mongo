package com.AttendanceRegister.sdc.controller;

import com.AttendanceRegister.sdc.dto.AuthResponse;
import com.AttendanceRegister.sdc.dto.LoginRequest;
import com.AttendanceRegister.sdc.model.Admin;
import com.AttendanceRegister.sdc.security.TokenService;
import com.AttendanceRegister.sdc.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final TokenService tokenService;

    public AdminController(AdminService adminService, TokenService tokenService) {
        this.adminService = adminService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse<Admin>> login(@RequestBody LoginRequest loginRequest) {
        Admin admin = adminService.login(loginRequest.getEmail(), loginRequest.getPassword());
        return ResponseEntity.ok(new AuthResponse<>(tokenService.issueAdminToken(admin), admin));
    }
}
