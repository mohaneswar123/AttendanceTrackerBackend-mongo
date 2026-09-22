package com.AttendanceRegister.sdc.model;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One focus period of the Pomodoro timer. Not linked to tasks.
@Document(collection = "pomodoro_session_table")
public class PomodoroSession {

    @Id
    private String id;

    private String userId;

    // Focus length the student chose for this session
    private int durationSeconds;

    // Break length the student chose; 0 for sessions saved before times could be chosen
    private int breakSeconds;

    private PomodoroStatus status;

    private Instant startedAt;

    // When the current pause began; null while the timer runs
    private Instant pausedAt;

    // Total length of earlier, finished pauses
    private long pausedMillis;

    // When the focus time ran out; the break is timed from here
    private Instant completedAt;

    private boolean breakSkipped;

    public PomodoroSession() {}

    public PomodoroSession(String userId, int durationSeconds, int breakSeconds, Instant startedAt) {
        this.userId = userId;
        this.durationSeconds = durationSeconds;
        this.breakSeconds = breakSeconds;
        this.startedAt = startedAt;
        this.status = PomodoroStatus.ACTIVE;
    }

    public boolean isPaused() {
        return pausedAt != null;
    }

    // Focus time still to go at `now`, counting the current pause as not elapsed
    public long remainingMillis(Instant now) {
        Instant clockStoppedAt = isPaused() ? pausedAt : now;
        long focused = Duration.between(startedAt, clockStoppedAt).toMillis() - pausedMillis;
        return durationSeconds * 1000L - focused;
    }

    // When the focus time runs out if the timer isn't paused again
    public Instant endsAt() {
        return startedAt.plusSeconds(durationSeconds).plusMillis(pausedMillis);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getBreakSeconds() {
        return breakSeconds;
    }

    public void setBreakSeconds(int breakSeconds) {
        this.breakSeconds = breakSeconds;
    }

    public PomodoroStatus getStatus() {
        return status;
    }

    public void setStatus(PomodoroStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    public long getPausedMillis() {
        return pausedMillis;
    }

    public void setPausedMillis(long pausedMillis) {
        this.pausedMillis = pausedMillis;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isBreakSkipped() {
        return breakSkipped;
    }

    public void setBreakSkipped(boolean breakSkipped) {
        this.breakSkipped = breakSkipped;
    }
}
