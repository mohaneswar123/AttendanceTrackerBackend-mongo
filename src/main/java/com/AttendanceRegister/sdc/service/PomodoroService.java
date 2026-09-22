package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.PomodoroSessionRepository;
import com.AttendanceRegister.sdc.dto.PomodoroState;
import com.AttendanceRegister.sdc.dto.PomodoroState.Phase;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.PomodoroSession;
import com.AttendanceRegister.sdc.model.PomodoroStatus;

// The Pomodoro timer: a focus period, then a break timed from when the focus ended.
// All state is on the server, so the timer survives refresh, navigation and device changes.
@Service
public class PomodoroService {

    // How early the client may report the end, to allow for timer and network lag
    private static final long COMPLETE_TOLERANCE_MILLIS = 5_000;
    // Limits on the lengths a student can choose, in minutes
    static final int MAX_FOCUS_MINUTES = 120;
    static final int MAX_BREAK_MINUTES = 30;

    private final PomodoroSessionRepository sessionRepository;
    private final Clock clock;
    private final int focusSeconds;
    private final int breakSeconds;

    public PomodoroService(PomodoroSessionRepository sessionRepository,
                           Clock clock,
                           @Value("${app.pomodoro.focus-seconds:1500}") int focusSeconds,
                           @Value("${app.pomodoro.break-seconds:300}") int breakSeconds) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.focusSeconds = focusSeconds;
        this.breakSeconds = breakSeconds;
    }

    public PomodoroState current(String userId) {
        Instant now = clock.instant();
        Optional<PomodoroSession> active = sessionRepository.findFirstByUserIdAndStatus(userId, PomodoroStatus.ACTIVE);
        if (active.isPresent()) {
            PomodoroSession session = active.get();
            if (session.isPaused() || session.remainingMillis(now) > 0) {
                return focusState(session, now);
            }
            // The time ran out while nobody was watching: finish it when it really ended
            sessionRepository.completeIfActive(session.getId(), session.endsAt());
        }
        return breakOrIdle(userId, now);
    }

    // focusMinutes and breakMinutes are the student's choice; null means the default length
    public PomodoroState start(String userId, Integer focusMinutes, Integer breakMinutes) {
        int focusLength = chosenSeconds(focusMinutes, MAX_FOCUS_MINUTES, focusSeconds, "Focus time");
        int breakLength = chosenSeconds(breakMinutes, MAX_BREAK_MINUTES, breakSeconds, "Break time");
        if (current(userId).phase() == Phase.FOCUS) {
            throw focusAlreadyRunning();
        }
        Instant now = clock.instant();
        PomodoroSession session = new PomodoroSession(userId, focusLength, breakLength, now);
        try {
            session = sessionRepository.insert(session);
        } catch (DuplicateKeyException ex) {
            // Another request started one at the same moment; the unique index caught it
            throw focusAlreadyRunning();
        }
        return focusState(session, now);
    }

    public PomodoroState pause(String userId, String sessionId) {
        PomodoroSession session = getOwnedSession(userId, sessionId);
        Instant now = clock.instant();
        if (session.getStatus() == PomodoroStatus.ACTIVE && !session.isPaused() && session.remainingMillis(now) > 0) {
            sessionRepository.pauseIfRunning(sessionId, now);
        }
        return current(userId);
    }

    public PomodoroState resume(String userId, String sessionId) {
        PomodoroSession session = getOwnedSession(userId, sessionId);
        if (session.getStatus() == PomodoroStatus.ACTIVE && session.isPaused()) {
            long pausedFor = Duration.between(session.getPausedAt(), clock.instant()).toMillis();
            sessionRepository.resumeIfPaused(sessionId, session.getPausedAt(), Math.max(0, pausedFor));
        }
        return current(userId);
    }

    // The Reset button: the session is dropped without counting
    public PomodoroState stop(String userId, String sessionId) {
        getOwnedSession(userId, sessionId);
        sessionRepository.interruptIfActive(sessionId);
        return current(userId);
    }

    // Safe to repeat: only the request that finds the session still ACTIVE completes it
    public PomodoroState complete(String userId, String sessionId) {
        PomodoroSession session = getOwnedSession(userId, sessionId);
        if (session.getStatus() == PomodoroStatus.ACTIVE) {
            Instant now = clock.instant();
            if (session.isPaused() || session.remainingMillis(now) > COMPLETE_TOLERANCE_MILLIS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FOCUS_NOT_FINISHED", "The focus time isn't over yet");
            }
            Instant end = session.endsAt();
            sessionRepository.completeIfActive(sessionId, now.isBefore(end) ? now : end);
        }
        return current(userId);
    }

    public PomodoroState skipBreak(String userId, String sessionId) {
        getOwnedSession(userId, sessionId);
        sessionRepository.skipBreakIfCompleted(sessionId);
        return current(userId);
    }

    private PomodoroState breakOrIdle(String userId, Instant now) {
        return sessionRepository.findFirstByUserIdOrderByStartedAtDesc(userId)
                .filter(last -> last.getStatus() == PomodoroStatus.COMPLETED && !last.isBreakSkipped())
                .filter(last -> last.getCompletedAt() != null)
                .map(last -> {
                    int breakLength = last.getBreakSeconds() > 0 ? last.getBreakSeconds() : breakSeconds;
                    long breakLeft = breakLength * 1000L - Duration.between(last.getCompletedAt(), now).toMillis();
                    return breakLeft > 0
                            ? new PomodoroState(Phase.BREAK, last.getId(), toSeconds(breakLeft), breakLength, false)
                            : null;
                })
                .orElseGet(() -> new PomodoroState(Phase.IDLE, null, focusSeconds, focusSeconds, false));
    }

    private static PomodoroState focusState(PomodoroSession session, Instant now) {
        long remaining = Math.max(0, session.remainingMillis(now));
        return new PomodoroState(Phase.FOCUS, session.getId(), toSeconds(remaining),
                session.getDurationSeconds(), session.isPaused());
    }

    // Other students' sessions look the same as sessions that don't exist
    private PomodoroSession getOwnedSession(String userId, String sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(session -> userId.equals(session.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Focus session not found"));
    }

    private static int chosenSeconds(Integer minutes, int maxMinutes, int defaultSeconds, String label) {
        if (minutes == null) {
            return defaultSeconds;
        }
        if (minutes < 1 || minutes > maxMinutes) {
            throw ApiException.badRequest(label + " must be between 1 and " + maxMinutes + " minutes");
        }
        return minutes * 60;
    }

    // Rounds up, so a fresh timer shows 25:00 rather than 24:59
    private static long toSeconds(long millis) {
        return (millis + 999) / 1000;
    }

    private static ApiException focusAlreadyRunning() {
        return new ApiException(HttpStatus.CONFLICT, "FOCUS_ALREADY_RUNNING", "A focus session is already running");
    }
}
