package com.AttendanceRegister.sdc.controller;

import com.AttendanceRegister.sdc.model.Admin;
import com.AttendanceRegister.sdc.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public ResponseEntity<Admin> login(@RequestBody Admin loginRequest) {
        Admin loggedInAdmin = adminService.login(loginRequest.getEmail(), loginRequest.getPassword());
        return ResponseEntity.ok(loggedInAdmin);
    }
}
