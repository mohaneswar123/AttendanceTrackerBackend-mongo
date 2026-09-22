package com.AttendanceRegister.sdc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.exception.ApiException;

@Component
public class PasswordHasher {

    // BCrypt only looks at the first 72 bytes, and Spring refuses longer input.
    private static final int MAX_PASSWORD_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // Hashes a password a user has chosen, rejecting ones BCrypt can't handle.
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw ApiException.badRequest("Password is required");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("Password must be at most 72 characters");
        }
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isHashed(storedPassword)) {
            return encoder.matches(rawPassword, storedPassword);
        }
        // Accounts created before hashing was added hold plain text until
        // LegacyPasswordMigration rewrites them at startup.
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isHashed(String storedPassword) {
        return storedPassword != null
                && (storedPassword.startsWith("$2a$")
                        || storedPassword.startsWith("$2b$")
                        || storedPassword.startsWith("$2y$"));
    }
}
