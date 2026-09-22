package com.AttendanceRegister.sdc.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.PomodoroSessionRepository;
import com.AttendanceRegister.sdc.Repository.TaskRepository;
import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.dto.RegisterRequest;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.User;
import com.AttendanceRegister.sdc.security.PasswordHasher;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final ResetService resetService;
    private final TaskRepository taskRepository;
    private final PomodoroSessionRepository pomodoroSessionRepository;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher, ResetService resetService,
                       TaskRepository taskRepository, PomodoroSessionRepository pomodoroSessionRepository) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.resetService = resetService;
        this.taskRepository = taskRepository;
        this.pomodoroSessionRepository = pomodoroSessionRepository;
    }

    public User validateLogin(String email, String rawPassword) {
        User user = email == null ? null : userRepository.findByEmail(email);
        if (user == null || !passwordHasher.matches(rawPassword, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        requireActiveSubscription(user);
        return user;
    }

    // Throws 403 SUBSCRIPTION_EXPIRED / SUBSCRIPTION_INACTIVE unless the user may use the app today.
    public void requireActiveSubscription(User user) {
        if (user.getPaidTill() != null && user.getPaidTill().isBefore(LocalDate.now())) {
            if (user.isActive()) {
                user.setActive(false);
                userRepository.save(user);
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "SUBSCRIPTION_EXPIRED",
                    "Your subscription expired. Please pay ₹5.");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SUBSCRIPTION_INACTIVE",
                    "Your subscription is not active. Please pay ₹5.");
        }
    }

    public User registerUser(RegisterRequest request) {
        String username = trimToNull(request.getUsername());
        String email = trimToNull(request.getEmail());
        if (username == null || email == null) {
            throw ApiException.badRequest("Name and email are required");
        }
        String passwordHash = passwordHasher.hash(request.getPassword());

        // Names needn't be unique; accounts are told apart by email
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email already exists");
        }

        User user = new User(username, passwordHash, email);
        // New accounts start active for 1000 days; admins can change that later
        user.setActive(true);
        user.setPaidTill(LocalDate.now().plusDays(1000));
        return userRepository.save(user);
    }

    public void changePassword(User user, String oldPassword, String newPassword) {
        if (!passwordHasher.matches(oldPassword, user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Old password is incorrect");
        }
        user.setPassword(passwordHasher.hash(newPassword));
        userRepository.save(user);
    }

    // Admin: replaces a user's password, e.g. when they have forgotten it
    public void setPassword(String userId, String newPassword) {
        User user = getUserById(userId);
        user.setPassword(passwordHasher.hash(newPassword));
        userRepository.save(user);
    }

    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found with ID: " + id));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Deletes the user together with their subjects, attendance, tasks and focus sessions
    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw ApiException.notFound("User not found with ID: " + id);
        }
        resetService.resetUserData(id);
        taskRepository.deleteByUserId(id);
        pomodoroSessionRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    public User updateEmail(String id, String newEmail) {
        User user = getUserById(id);
        String email = trimToNull(newEmail);
        if (email == null || !email.contains("@")) {
            throw ApiException.badRequest("Enter a valid email address");
        }
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email already exists");
        }
        user.setEmail(email);
        return userRepository.save(user);
    }

    public User activateUser(String id, int days) {
        if (days < 0) {
            throw ApiException.badRequest("Days must be 0 or more");
        }
        User user = getUserById(id);
        user.setActive(true);
        user.setPaidTill(LocalDate.now().plusDays(days));
        return userRepository.save(user);
    }

    public User deactivateUser(String id) {
        User user = getUserById(id);
        user.setActive(false);
        return userRepository.save(user);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
