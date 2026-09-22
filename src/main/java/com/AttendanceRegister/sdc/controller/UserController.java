package com.AttendanceRegister.sdc.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.dto.AuthResponse;
import com.AttendanceRegister.sdc.dto.LoginRequest;
import com.AttendanceRegister.sdc.dto.RegisterRequest;
import com.AttendanceRegister.sdc.model.User;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.security.TokenService;
import com.AttendanceRegister.sdc.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;
    private final AccessGuard accessGuard;

    public UserController(UserService userService, TokenService tokenService, AccessGuard accessGuard) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.accessGuard = accessGuard;
    }

    // ✅ Register new user (public)
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.registerUser(request));
    }

    // ✅ Log in (public); returns a bearer token for the other endpoints
    @PostMapping("/login")
    public ResponseEntity<AuthResponse<User>> login(@RequestBody LoginRequest request) {
        User user = userService.validateLogin(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(new AuthResponse<>(tokenService.issueUserToken(user), user));
    }

    // ✅ Signed-in user's account; 403 SUBSCRIPTION_* once it is no longer active
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        User user = accessGuard.currentUser(jwt);
        userService.requireActiveSubscription(user);
        return ResponseEntity.ok(user);
    }

    // ✅ Get user by ID (the user themselves or an admin)
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        accessGuard.requireSelfOrAdmin(jwt, userId);
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    // ✅ ADMIN: Get all users
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ✅ ADMIN: Delete user with all their subjects and attendance
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ✅ Update email
    @PutMapping("/{userId}/email")
    public ResponseEntity<User> updateEmail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody Map<String, String> req) {

        accessGuard.requireActiveSelfOrAdmin(jwt, userId);
        return ResponseEntity.ok(userService.updateEmail(userId, req.get("email")));
    }

    // ✅ Change the signed-in user's password
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> req) {

        User user = accessGuard.currentUser(jwt);
        userService.changePassword(user, req.get("oldPassword"), req.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    // ✅ ADMIN: Activate user for X days
    @PutMapping("/admin/activate/{userId}")
    public ResponseEntity<User> activateUser(@PathVariable String userId, @RequestParam int days) {
        return ResponseEntity.ok(userService.activateUser(userId, days));
    }

    // ✅ ADMIN: Deactivate user
    @PutMapping("/admin/deactivate/{userId}")
    public ResponseEntity<User> deactivateUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.deactivateUser(userId));
    }

    // ✅ ADMIN: Set a new password for a user who has forgotten theirs
    @PutMapping("/admin/{userId}/password")
    public ResponseEntity<Map<String, String>> setPassword(
            @PathVariable String userId,
            @RequestBody Map<String, String> req) {

        userService.setPassword(userId, req.get("password"));
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }
}
