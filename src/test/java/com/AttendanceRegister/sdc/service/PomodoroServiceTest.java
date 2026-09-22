package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.PomodoroSessionRepository;
import com.AttendanceRegister.sdc.dto.PomodoroState;
import com.AttendanceRegister.sdc.dto.PomodoroState.Phase;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.PomodoroSession;
import com.AttendanceRegister.sdc.model.PomodoroStatus;

@ExtendWith(MockitoExtension.class)
class PomodoroServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-22T10:00:00Z");
    private static final int FOCUS = 1500;
    private static final int BREAK = 300;

    // A clock the test can move forward
    private static final class TestClock extends Clock {
        private Instant now = T0;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Mock
    private PomodoroSessionRepository repository;

    private final TestClock clock = new TestClock();
    private PomodoroService service;

    @BeforeEach
    void setUp() {
        service = new PomodoroService(repository, clock, FOCUS, BREAK);
    }

    private static PomodoroSession session(PomodoroStatus status) {
        PomodoroSession session = new PomodoroSession("u1", FOCUS, T0);
        session.setId("s1");
        session.setStatus(status);
        return session;
    }

    // lenient: some tests look the session up only one of these two ways
    private void givenActive(PomodoroSession session) {
        lenient().when(repository.findFirstByUserIdAndStatus("u1", PomodoroStatus.ACTIVE)).thenReturn(Optional.of(session));
        lenient().when(repository.findById(session.getId())).thenReturn(Optional.of(session));
    }

    private void givenNothingActive() {
        when(repository.findFirstByUserIdAndStatus("u1", PomodoroStatus.ACTIVE)).thenReturn(Optional.empty());
    }

    private void givenLatest(PomodoroSession session) {
        when(repository.findFirstByUserIdOrderByStartedAtDesc("u1")).thenReturn(Optional.ofNullable(session));
    }

    private static void assertApiError(Throwable thrown, HttpStatus status, String code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(status);
            assertThat(ex.getCode()).isEqualTo(code);
        });
    }

    @Test
    void idleWhenThereAreNoSessions() {
        givenNothingActive();
        givenLatest(null);

        PomodoroState state = service.current("u1");

        assertThat(state.phase()).isEqualTo(Phase.IDLE);
        assertThat(state.remainingSeconds()).isEqualTo(FOCUS);
    }

    @Test
    void startCreatesAFullFocusSession() {
        givenNothingActive();
        givenLatest(null);
        when(repository.insert(any(PomodoroSession.class))).thenAnswer(inv -> {
            PomodoroSession saved = inv.getArgument(0);
            saved.setId("s1");
            return saved;
        });

        PomodoroState state = service.start("u1");

        assertThat(state.phase()).isEqualTo(Phase.FOCUS);
        assertThat(state.sessionId()).isEqualTo("s1");
        assertThat(state.remainingSeconds()).isEqualTo(FOCUS);
        assertThat(state.paused()).isFalse();
    }

    @Test
    void startIsRefusedWhileFocusIsRunning() {
        givenActive(session(PomodoroStatus.ACTIVE));

        assertApiError(catchThrowable(() -> service.start("u1")), HttpStatus.CONFLICT, "FOCUS_ALREADY_RUNNING");
        verify(repository, never()).insert(any(PomodoroSession.class));
    }

    @Test
    void simultaneousStartIsRefusedByTheIndex() {
        givenNothingActive();
        givenLatest(null);
        when(repository.insert(any(PomodoroSession.class))).thenThrow(new DuplicateKeyException("one_active_session_per_user"));

        assertApiError(catchThrowable(() -> service.start("u1")), HttpStatus.CONFLICT, "FOCUS_ALREADY_RUNNING");
    }

    @Test
    void remainingTimeLeavesOutPauses() {
        PomodoroSession running = session(PomodoroStatus.ACTIVE);
        running.setPausedMillis(60_000);                 // paused for a minute earlier
        running.setPausedAt(T0.plusSeconds(300));        // and paused again at 5:00
        givenActive(running);
        clock.advance(Duration.ofSeconds(400));

        PomodoroState state = service.current("u1");

        // 400s since start, minus 60s of earlier pause and 100s of the current one
        assertThat(state.phase()).isEqualTo(Phase.FOCUS);
        assertThat(state.paused()).isTrue();
        assertThat(state.remainingSeconds()).isEqualTo(FOCUS - 240);
    }

    @Test
    void focusThatRanOutWhileAwayCompletesAtItsRealEnd() {
        PomodoroSession running = session(PomodoroStatus.ACTIVE);
        givenActive(running);
        clock.advance(Duration.ofSeconds(FOCUS + 100));   // came back 100s after it ended

        PomodoroSession completed = session(PomodoroStatus.COMPLETED);
        completed.setCompletedAt(T0.plusSeconds(FOCUS));
        givenLatest(completed);

        PomodoroState state = service.current("u1");

        verify(repository).completeIfActive("s1", T0.plusSeconds(FOCUS));
        assertThat(state.phase()).isEqualTo(Phase.BREAK);
        assertThat(state.remainingSeconds()).isEqualTo(BREAK - 100);
    }

    @Test
    void completingEarlyIsRefused() {
        givenActive(session(PomodoroStatus.ACTIVE));
        clock.advance(Duration.ofSeconds(FOCUS - 60));

        assertApiError(catchThrowable(() -> service.complete("u1", "s1")), HttpStatus.BAD_REQUEST, "FOCUS_NOT_FINISHED");
        verify(repository, never()).completeIfActive(anyString(), any());
    }

    @Test
    void completingASecondTimeChangesNothing() {
        PomodoroSession done = session(PomodoroStatus.COMPLETED);
        done.setCompletedAt(T0.plusSeconds(FOCUS));
        when(repository.findById("s1")).thenReturn(Optional.of(done));
        givenNothingActive();
        givenLatest(done);
        clock.advance(Duration.ofSeconds(FOCUS + 1));

        PomodoroState state = service.complete("u1", "s1");

        verify(repository, never()).completeIfActive(anyString(), any());
        assertThat(state.phase()).isEqualTo(Phase.BREAK);
    }

    @Test
    void breakEndsAfterBreakTimeAndCanBeSkipped() {
        PomodoroSession done = session(PomodoroStatus.COMPLETED);
        done.setCompletedAt(T0.plusSeconds(FOCUS));
        givenNothingActive();
        givenLatest(done);

        clock.advance(Duration.ofSeconds(FOCUS + BREAK + 1));
        assertThat(service.current("u1").phase()).isEqualTo(Phase.IDLE);

        done.setBreakSkipped(true);
        clock.advance(Duration.ofSeconds(-BREAK));
        assertThat(service.current("u1").phase()).isEqualTo(Phase.IDLE);
    }

    @Test
    void skipBreakMarksTheSession() {
        PomodoroSession done = session(PomodoroStatus.COMPLETED);
        when(repository.findById("s1")).thenReturn(Optional.of(done));
        givenNothingActive();
        givenLatest(null);

        service.skipBreak("u1", "s1");

        verify(repository).skipBreakIfCompleted("s1");
    }

    @Test
    void pauseAndResumeUseConditionalUpdates() {
        PomodoroSession running = session(PomodoroStatus.ACTIVE);
        givenActive(running);
        clock.advance(Duration.ofSeconds(60));
        service.pause("u1", "s1");
        verify(repository).pauseIfRunning("s1", T0.plusSeconds(60));

        running.setPausedAt(T0.plusSeconds(60));
        clock.advance(Duration.ofSeconds(30));
        service.resume("u1", "s1");
        verify(repository).resumeIfPaused("s1", T0.plusSeconds(60), 30_000);
    }

    @Test
    void anotherStudentsSessionIsNotFound() {
        PomodoroSession someoneElses = session(PomodoroStatus.ACTIVE);
        someoneElses.setUserId("u2");
        when(repository.findById("s1")).thenReturn(Optional.of(someoneElses));

        assertApiError(catchThrowable(() -> service.stop("u1", "s1")), HttpStatus.NOT_FOUND, "NOT_FOUND");
        verify(repository, never()).interruptIfActive(anyString());
    }
}
