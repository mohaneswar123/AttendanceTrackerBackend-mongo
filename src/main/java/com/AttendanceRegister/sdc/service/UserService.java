package com.AttendanceRegister.sdc.service;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.model.User;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User validateLogin(String email, String rawPassword) {

        // Email check
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("Invalid email or password");
        }

        // Password check
        if (!user.getPassword().equals(rawPassword)) {
            throw new RuntimeException("Invalid email or password");
        }

        // Subscription expired
        if (user.getPaidTill() != null && user.getPaidTill().isBefore(LocalDate.now())) {
            user.setActive(false);
            userRepository.save(user);
            throw new RuntimeException("Your subscription expired. Please pay ₹5.");
        }

        // Subscription inactive
        if (!user.isActive()) {
            throw new RuntimeException("Your subscription is not active. Please pay ₹5.");
        }

        return user;
    }



    // Register new user
    public User registerUser(User user) {

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Make the new user inactive by default
        user.setActive(true);
        user.setPaidTill(LocalDate.now().plusDays(1000)); 

        return userRepository.save(user);
    }

    public String changePassword(String email, String oldPassword, String newPassword) {

        User user = userRepository.findByEmail(email);

        if (user == null) {
            return "User not found";
        }

        if (!user.getPassword().equals(oldPassword)) {
            return "Old password is incorrect";
        }

        user.setPassword(newPassword);
        userRepository.save(user);

        return "Password updated successfully";
    }


    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with ID: " + id);
        }
        userRepository.deleteById(id);
    }

    public User updateEmail(String id, String newEmail) {
        User user = getUserById(id);
        user.setEmail(newEmail);
        return userRepository.save(user);
    }

 // 👉 NEW: Activate user for custom number of days
    public User activateUser(String id, int days) {
        User user = getUserById(id);
        user.setActive(true);
        user.setPaidTill(LocalDate.now().plusDays(days));
        return userRepository.save(user);
    }


    // 👉 NEW: Deactivate user
    public User deactivateUser(String id) {
        User user = getUserById(id);
        user.setActive(false);
        return userRepository.save(user);
    }
}
