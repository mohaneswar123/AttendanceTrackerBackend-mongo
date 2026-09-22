package com.AttendanceRegister.sdc.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.controller.AdminController;
import com.AttendanceRegister.sdc.controller.AttendanceRecordController;
import com.AttendanceRegister.sdc.controller.PomodoroController;
import com.AttendanceRegister.sdc.controller.ResetController;
import com.AttendanceRegister.sdc.controller.SubjectController;
import com.AttendanceRegister.sdc.controller.TaskController;
import com.AttendanceRegister.sdc.controller.UserController;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.AttendanceRecord;
import com.AttendanceRegister.sdc.model.User;
import com.AttendanceRegister.sdc.service.AdminService;
import com.AttendanceRegister.sdc.service.AttendanceRecordService;
import com.AttendanceRegister.sdc.service.PomodoroService;
import com.AttendanceRegister.sdc.service.ResetService;
import com.AttendanceRegister.sdc.service.SubjectService;
import com.AttendanceRegister.sdc.service.TaskService;
import com.AttendanceRegister.sdc.service.UserService;
import com.jayway.jsonpath.JsonPath;

// Checks who may call which endpoint. Services are mocked, so no database is needed.
@WebMvcTest(controllers = {
        UserController.class, AdminController.class, SubjectController.class,
        AttendanceRecordController.class, ResetController.class,
        TaskController.class, PomodoroController.class })
@Import({ SecurityConfig.class, AccessGuard.class, TokenService.class })
class SecurityRulesTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private AdminService adminService;
    @MockitoBean
    private SubjectService subjectService;
    @MockitoBean
    private AttendanceRecordService attendanceService;
    @MockitoBean
    private ResetService resetService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private TaskService taskService;
    @MockitoBean
    private PomodoroService pomodoroService;

    private static RequestPostProcessor asUser(String userId) {
        return jwt().jwt(token -> token.subject(userId).claim(AccessGuard.ROLE_CLAIM, AccessGuard.ROLE_USER))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor asAdmin() {
        return jwt().jwt(token -> token.subject("admin1").claim(AccessGuard.ROLE_CLAIM, AccessGuard.ROLE_ADMIN))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private User activeUser(String userId) {
        User user = new User("Asha", "hash", "asha@example.com");
        user.setId(userId);
        user.setActive(true);
        user.setPaidTill(LocalDate.now().plusDays(30));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void dataEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/api/subjects/user/u1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotUseAdminEndpoints() throws Exception {
        mvc.perform(get("/api/users").with(asUser("u1"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/admin/activate/u1").param("days", "30").with(asUser("u1")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/u2").with(asUser("u1"))).andExpect(status().isForbidden());
    }

    @Test
    void adminsCanUseAdminEndpoints() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mvc.perform(get("/api/users").with(asAdmin())).andExpect(status().isOk());
    }

    @Test
    void usersCanOnlyReadTheirOwnData() throws Exception {
        activeUser("u1");
        when(subjectService.getSubjectsByUser("u1")).thenReturn(List.of());

        mvc.perform(get("/api/subjects/user/u1").with(asUser("u1"))).andExpect(status().isOk());
        mvc.perform(get("/api/subjects/user/u2").with(asUser("u1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/user/u2").with(asUser("u1"))).andExpect(status().isForbidden());
    }

    @Test
    void inactiveUsersAreToldToRenew() throws Exception {
        User user = activeUser("u1");
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "SUBSCRIPTION_INACTIVE", "Your subscription is not active."))
                .when(userService).requireActiveSubscription(user);

        mvc.perform(get("/api/subjects/user/u1").with(asUser("u1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
    }

    @Test
    void usersCannotDeleteAnotherUsersRecord() throws Exception {
        AttendanceRecord record = new AttendanceRecord("Present", "2026-09-01", 1, "u2", "s1");
        record.setId("r1");
        when(attendanceService.getRecord("r1")).thenReturn(record);

        mvc.perform(delete("/api/attendance/r1").with(asUser("u1"))).andExpect(status().isForbidden());
        verify(attendanceService, never()).deleteRecord(any());
    }

    @Test
    void loginTokenGrantsUserAccessOnly() throws Exception {
        User user = activeUser("u1");
        when(userService.validateLogin("asha@example.com", "secret1")).thenReturn(user);
        when(subjectService.getSubjectsByUser("u1")).thenReturn(List.of());

        String body = mvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("asha@example.com"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.token");

        mvc.perform(get("/api/subjects/user/u1").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void failedLoginReturnsCodeAndMessage() throws Exception {
        when(userService.validateLogin("asha@example.com", "wrong"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"));

        mvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void tasksAndPomodoroNeedAToken() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pomodoro/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCannotUseTasksOrPomodoro() throws Exception {
        mvc.perform(get("/api/tasks").with(asAdmin())).andExpect(status().isForbidden());
        mvc.perform(post("/api/pomodoro/start").with(asAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void tasksAlwaysBelongToTheSignedInStudent() throws Exception {
        activeUser("u1");
        when(taskService.getTasks("u1", "2026-09-22", null)).thenReturn(List.of());

        // A userId in the request is ignored; the student comes from the token
        mvc.perform(get("/api/tasks").param("from", "2026-09-22").param("userId", "u2").with(asUser("u1")))
                .andExpect(status().isOk());
        verify(taskService).getTasks("u1", "2026-09-22", null);
    }

    @Test
    void anotherStudentsTaskOrSessionIsNotFound() throws Exception {
        activeUser("u1");
        doThrow(ApiException.notFound("Task not found")).when(taskService).deleteTask("u1", "t9");
        when(pomodoroService.pause("u1", "s9")).thenThrow(ApiException.notFound("Focus session not found"));

        mvc.perform(delete("/api/tasks/t9").with(asUser("u1"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/pomodoro/s9/pause").with(asUser("u1"))).andExpect(status().isNotFound());
    }

    @Test
    void inactiveStudentsCannotUseTasksOrPomodoro() throws Exception {
        User user = activeUser("u1");
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "SUBSCRIPTION_INACTIVE", "Your subscription is not active."))
                .when(userService).requireActiveSubscription(user);

        mvc.perform(get("/api/tasks").with(asUser("u1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
        mvc.perform(get("/api/pomodoro/current").with(asUser("u1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/users/login")
                        .header("Origin", "https://attendanceinhand.netlify.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://attendanceinhand.netlify.app"));

        mvc.perform(options("/api/users/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
