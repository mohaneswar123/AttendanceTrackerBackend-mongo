package com.AttendanceRegister.sdc.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.dto.LoginRequest;
import com.AttendanceRegister.sdc.model.User;
import com.AttendanceRegister.sdc.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ✅ Register new user
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        User registered = userService.registerUser(user);
        
        return ResponseEntity.ok(registered);
    }
 // UserController.java
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User user = userService.validateLogin(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(user);

        } catch (RuntimeException ex) {
            // RETURN EXACT MESSAGE (not LOGIN_FAILED)
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }



    // ✅ Get user by ID
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    // ✅ Get all users
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ✅ Delete user
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ✅ Update email
    @PutMapping("/{userId}/email")
    public ResponseEntity<User> updateEmail(
            @PathVariable String userId,
            @RequestBody Map<String, String> req) {

        String newEmail = req.get("email");
        User updatedUser = userService.updateEmail(userId, newEmail);
        return ResponseEntity.ok(updatedUser);
    }

    
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> req) {

        String email = req.get("email");
        String oldPassword = req.get("oldPassword");
        String newPassword = req.get("newPassword");

        String result = userService.changePassword(email, oldPassword, newPassword);

        if (result.equals("Password updated successfully")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(400).body(result);
        }
    }

    
 // ✅ ADMIN: Activate user for X days
    @PutMapping("/admin/activate/{userId}")
    public ResponseEntity<User> activateUser(
            @PathVariable String userId,@RequestParam int days) {

        User activatedUser = userService.activateUser(userId, days);
        return ResponseEntity.ok(activatedUser);
    }

    // ✅ ADMIN: Deactivate user
    @PutMapping("/admin/deactivate/{userId}")
    public ResponseEntity<User> deactivateUser(@PathVariable String userId) {
        User deactivated = userService.deactivateUser(userId);
        return ResponseEntity.ok(deactivated);
    }

    
}
