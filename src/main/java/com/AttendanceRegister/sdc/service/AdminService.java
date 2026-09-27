package com.AttendanceRegister.sdc.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.AdminRepository;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Admin;
import com.AttendanceRegister.sdc.security.LoginThrottle;
import com.AttendanceRegister.sdc.security.PasswordHasher;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordHasher passwordHasher;
    private final LoginThrottle loginThrottle;

    public AdminService(AdminRepository adminRepository, PasswordHasher passwordHasher, LoginThrottle loginThrottle) {
        this.adminRepository = adminRepository;
        this.passwordHasher = passwordHasher;
        this.loginThrottle = loginThrottle;
    }

    public Admin login(String email, String password) {
        // Refuse before looking anything up, so a locked-out caller learns nothing
        loginThrottle.check(email);

        Admin admin = adminRepository.findByEmail(email)
                .filter(candidate -> passwordHasher.matches(password, candidate.getPassword()))
                .orElse(null);

        if (admin == null) {
            loginThrottle.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Invalid email or password");
        }

        loginThrottle.recordSuccess(email);
        return admin;
    }
}
