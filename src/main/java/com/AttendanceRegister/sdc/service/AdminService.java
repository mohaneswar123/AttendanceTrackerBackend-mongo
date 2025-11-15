package com.AttendanceRegister.sdc.service;

import com.AttendanceRegister.sdc.model.Admin;
import com.AttendanceRegister.sdc.Repository.AdminRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class AdminService {

    private final AdminRepository adminRepository;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public Admin login(String email, String password) {
        Optional<Admin> adminOpt = adminRepository.findByEmail(email);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (admin.getPassword().equals(password)) {
                return admin;
            } else {
                throw new RuntimeException("Invalid password");
            }
        } else {
            throw new RuntimeException("Admin not found");
        }
    }
}
