package com.AttendanceRegister.sdc.Repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import com.AttendanceRegister.sdc.model.PomodoroSession;
import com.AttendanceRegister.sdc.model.PomodoroStatus;

public interface PomodoroSessionRepository extends MongoRepository<PomodoroSession, String> {
    Optional<PomodoroSession> findFirstByUserIdAndStatus(String userId, PomodoroStatus status);

    Optional<PomodoroSession> findFirstByUserIdOrderByStartedAtDesc(String userId);

    void deleteByUserId(String userId);

    // Each change below only applies while the session is still in the state the query
    // names, so repeated or simultaneous requests can't undo each other. They return the
    // number of sessions changed (0 or 1).

    @Query("{ '_id': ?0, 'status': 'ACTIVE' }")
    @Update("{ '$set': { 'status': 'COMPLETED', 'completedAt': ?1, 'pausedAt': null } }")
    long completeIfActive(String id, Instant completedAt);

    @Query("{ '_id': ?0, 'status': 'ACTIVE' }")
    @Update("{ '$set': { 'status': 'INTERRUPTED', 'pausedAt': null } }")
    long interruptIfActive(String id);

    @Query("{ '_id': ?0, 'status': 'ACTIVE', 'pausedAt': null }")
    @Update("{ '$set': { 'pausedAt': ?1 } }")
    long pauseIfRunning(String id, Instant pausedAt);

    @Query("{ '_id': ?0, 'status': 'ACTIVE', 'pausedAt': ?1 }")
    @Update("{ '$set': { 'pausedAt': null }, '$inc': { 'pausedMillis': ?2 } }")
    long resumeIfPaused(String id, Instant pausedAt, long pausedMillis);

    @Query("{ '_id': ?0, 'status': 'COMPLETED' }")
    @Update("{ '$set': { 'breakSkipped': true } }")
    long skipBreakIfCompleted(String id);
}
