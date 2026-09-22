package com.AttendanceRegister.sdc.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.User;
import com.AttendanceRegister.sdc.service.UserService;

// Per-account checks the URL rules in SecurityConfig can't express: a user may
// only touch their own data, and only while their subscription is active.
// Admins may act on any account.
@Component
public class AccessGuard {

    public static final String ROLE_CLAIM = "role";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private final UserRepository userRepository;
    private final UserService userService;

    public AccessGuard(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    public boolean isAdmin(Jwt jwt) {
        return ROLE_ADMIN.equals(jwt.getClaimAsString(ROLE_CLAIM));
    }

    // The signed-in user's account. A token for a deleted account counts as signed out.
    public User currentUser(Jwt jwt) {
        return userRepository.findById(jwt.getSubject())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND",
                        "Your account no longer exists"));
    }

    public void requireSelfOrAdmin(Jwt jwt, String userId) {
        if (!isAdmin(jwt) && !jwt.getSubject().equals(userId)) {
            throw ApiException.forbidden();
        }
    }

    public void requireActiveSelfOrAdmin(Jwt jwt, String userId) {
        requireSelfOrAdmin(jwt, userId);
        if (!isAdmin(jwt)) {
            userService.requireActiveSubscription(currentUser(jwt));
        }
    }

    // For student-only features (tasks, Pomodoro): the signed-in student, whose
    // subscription must be active. The student always comes from the token, never the request.
    public User requireActiveStudent(Jwt jwt) {
        if (isAdmin(jwt)) {
            throw ApiException.forbidden();
        }
        User user = currentUser(jwt);
        userService.requireActiveSubscription(user);
        return user;
    }
}
