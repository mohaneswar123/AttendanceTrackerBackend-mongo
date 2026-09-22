package com.AttendanceRegister.sdc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.AttendanceRegister.sdc.dto.PomodoroState;
import com.AttendanceRegister.sdc.security.AccessGuard;
import com.AttendanceRegister.sdc.service.PomodoroService;

// The signed-in student's Pomodoro timer. Every action returns the timer's new state.
@RestController
@RequestMapping("/api/pomodoro")
public class PomodoroController {

    private final PomodoroService pomodoroService;
    private final AccessGuard accessGuard;

    public PomodoroController(PomodoroService pomodoroService, AccessGuard accessGuard) {
        this.pomodoroService = pomodoroService;
        this.accessGuard = accessGuard;
    }

    // ✅ Idle, focusing or on a break, and how long is left
    @GetMapping("/current")
    public ResponseEntity<PomodoroState> current(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(pomodoroService.current(studentId(jwt)));
    }

    // ✅ Start a focus session
    @PostMapping("/start")
    public ResponseEntity<PomodoroState> start(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(pomodoroService.start(studentId(jwt)));
    }

    @PutMapping("/{sessionId}/pause")
    public ResponseEntity<PomodoroState> pause(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        return ResponseEntity.ok(pomodoroService.pause(studentId(jwt), sessionId));
    }

    @PutMapping("/{sessionId}/resume")
    public ResponseEntity<PomodoroState> resume(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        return ResponseEntity.ok(pomodoroService.resume(studentId(jwt), sessionId));
    }

    // ✅ Reset: drop the session without counting it
    @PutMapping("/{sessionId}/stop")
    public ResponseEntity<PomodoroState> stop(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        return ResponseEntity.ok(pomodoroService.stop(studentId(jwt), sessionId));
    }

    // ✅ The countdown reached zero; safe to repeat
    @PutMapping("/{sessionId}/complete")
    public ResponseEntity<PomodoroState> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        return ResponseEntity.ok(pomodoroService.complete(studentId(jwt), sessionId));
    }

    @PutMapping("/{sessionId}/skip-break")
    public ResponseEntity<PomodoroState> skipBreak(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        return ResponseEntity.ok(pomodoroService.skipBreak(studentId(jwt), sessionId));
    }

    private String studentId(Jwt jwt) {
        return accessGuard.requireActiveStudent(jwt).getId();
    }
}
