package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.CalendarEventRepository;
import com.AttendanceRegister.sdc.Repository.PomodoroSessionRepository;
import com.AttendanceRegister.sdc.Repository.TaskRepository;
import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.dto.RegisterRequest;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.User;
import com.AttendanceRegister.sdc.security.PasswordHasher;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResetService resetService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PomodoroSessionRepository pomodoroSessionRepository;

    @Mock
    private CalendarEventRepository calendarEventRepository;

    private final PasswordHasher passwordHasher = new PasswordHasher();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordHasher, resetService, taskRepository,
                pomodoroSessionRepository, calendarEventRepository);
    }

    private static User user(String storedPassword, boolean active, LocalDate paidTill) {
        User user = new User("Asha", storedPassword, "asha@example.com");
        user.setId("u1");
        user.setActive(active);
        user.setPaidTill(paidTill);
        return user;
    }

    private static RegisterRequest registration(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("Asha");
        request.setEmail("asha@example.com");
        request.setPassword(password);
        return request;
    }

    private static void assertApiError(Throwable thrown, HttpStatus status, String code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(status);
            assertThat(ex.getCode()).isEqualTo(code);
        });
    }

    @Test
    void registerStoresHashedPasswordAndStartsActive() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.registerUser(registration("secret1"));

        assertThat(saved.getPassword()).isNotEqualTo("secret1");
        assertThat(passwordHasher.matches("secret1", saved.getPassword())).isTrue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPaidTill()).isAfter(LocalDate.now());
    }

    @Test
    void registerAllowsNameUsedByAnotherStudent() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User first = userService.registerUser(registration("secret1"));
        RegisterRequest sameName = registration("secret2");
        sameName.setEmail("asha.k@example.com");
        User second = userService.registerUser(sameName);

        assertThat(second.getUsername()).isEqualTo(first.getUsername());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);

        Throwable thrown = catchThrowable(
                () -> userService.registerUser(registration("secret1")));

        assertApiError(thrown, HttpStatus.CONFLICT, "EMAIL_EXISTS");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRequiresPassword() {
        Throwable thrown = catchThrowable(
                () -> userService.registerUser(registration(" ")));

        assertApiError(thrown, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    @Test
    void loginAcceptsHashedPassword() {
        User stored = user(passwordHasher.hash("secret1"), true, LocalDate.now().plusDays(10));
        when(userRepository.findByEmail("asha@example.com")).thenReturn(stored);

        assertThat(userService.validateLogin("asha@example.com", "secret1")).isSameAs(stored);
    }

    @Test
    void loginAcceptsPasswordStoredBeforeHashing() {
        User stored = user("secret1", true, LocalDate.now().plusDays(10));
        when(userRepository.findByEmail("asha@example.com")).thenReturn(stored);

        assertThat(userService.validateLogin("asha@example.com", "secret1")).isSameAs(stored);
    }

    @Test
    void loginRejectsWrongPassword() {
        User stored = user(passwordHasher.hash("secret1"), true, LocalDate.now().plusDays(10));
        when(userRepository.findByEmail("asha@example.com")).thenReturn(stored);

        Throwable thrown = catchThrowable(
                () -> userService.validateLogin("asha@example.com", "wrong"));

        assertApiError(thrown, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    @Test
    void loginRejectsExpiredSubscriptionAndMarksUserInactive() {
        User stored = user(passwordHasher.hash("secret1"), true, LocalDate.now().minusDays(1));
        when(userRepository.findByEmail("asha@example.com")).thenReturn(stored);

        Throwable thrown = catchThrowable(
                () -> userService.validateLogin("asha@example.com", "secret1"));

        assertApiError(thrown, HttpStatus.FORBIDDEN, "SUBSCRIPTION_EXPIRED");
        assertThat(stored.isActive()).isFalse();
        verify(userRepository).save(stored);
    }

    @Test
    void loginRejectsDeactivatedUser() {
        User stored = user(passwordHasher.hash("secret1"), false, LocalDate.now().plusDays(10));
        when(userRepository.findByEmail("asha@example.com")).thenReturn(stored);

        Throwable thrown = catchThrowable(
                () -> userService.validateLogin("asha@example.com", "secret1"));

        assertApiError(thrown, HttpStatus.FORBIDDEN, "SUBSCRIPTION_INACTIVE");
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        User stored = user(passwordHasher.hash("secret1"), true, LocalDate.now().plusDays(10));

        Throwable thrown = catchThrowable(
                () -> userService.changePassword(stored, "wrong", "secret2"));

        assertApiError(thrown, HttpStatus.BAD_REQUEST, "WRONG_PASSWORD");
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordStoresNewHash() {
        User stored = user(passwordHasher.hash("secret1"), true, LocalDate.now().plusDays(10));

        userService.changePassword(stored, "secret1", "secret2");

        assertThat(passwordHasher.matches("secret2", stored.getPassword())).isTrue();
        verify(userRepository).save(stored);
    }

    @Test
    void deleteUserRemovesAllTheirData() {
        when(userRepository.existsById("u1")).thenReturn(true);

        userService.deleteUser("u1");

        verify(resetService).resetUserData("u1");
        verify(taskRepository).deleteByUserId("u1");
        verify(pomodoroSessionRepository).deleteByUserId("u1");
        verify(calendarEventRepository).deleteByUserId("u1");
        verify(userRepository).deleteById("u1");
    }

    @Test
    void updateEmailRejectsEmailOfAnotherAccount() {
        User stored = user("hash", true, LocalDate.now().plusDays(10));
        when(userRepository.findById("u1")).thenReturn(Optional.of(stored));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateEmail("u1", "taken@example.com"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EMAIL_EXISTS"));
    }
}
