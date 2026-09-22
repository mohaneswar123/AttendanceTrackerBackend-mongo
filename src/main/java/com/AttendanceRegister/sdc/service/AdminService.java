package com.AttendanceRegister.sdc.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.AdminRepository;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Admin;
import com.AttendanceRegister.sdc.security.PasswordHasher;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordHasher passwordHasher;

    public AdminService(AdminRepository adminRepository, PasswordHasher passwordHasher) {
        this.adminRepository = adminRepository;
        this.passwordHasher = passwordHasher;
    }

    public Admin login(String email, String password) {
        return adminRepository.findByEmail(email)
                .filter(admin -> passwordHasher.matches(password, admin.getPassword()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Invalid email or password"));
    }
}
