package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.UserActionRepository;
import com.AttendanceRegister.sdc.model.UserAction;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;

/**
 * The record of what a student has done, for an admin to look at.
 *
 * Two rules shape this. It is written only after the change has succeeded, so the log
 * never claims something that did not happen. And writing it can never break the thing
 * being recorded: if the log cannot be written the student's action still stands, because
 * losing an audit line is a far smaller problem than losing their work.
 */
@Service
public class UserAuditService {

    private static final Logger log = LoggerFactory.getLogger(UserAuditService.class);

    /** Enough to answer "what has this account been doing" without paging */
    public static final int MAX_ENTRIES = 200;

    /** How long an entry lives. Enforced by the database through a TTL index. */
    public static final int KEEP_FOR_DAYS = 180;

    private final UserActionRepository repository;
    private final Clock clock;

    public UserAuditService(UserActionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(String userId, UserActionFeature feature, UserActionType action, boolean byAdmin) {
        try {
            repository.save(new UserAction(userId, feature, action, byAdmin, clock.instant()));
        } catch (RuntimeException ex) {
            // The student's change has already been saved; only the log line is lost
            log.warn("Could not write a {} {} audit entry for user {}", feature, action, userId, ex);
        }
    }

    public List<UserAction> forUser(String userId, int limit) {
        return repository.findByUserIdOrderByAtDesc(userId, PageRequest.of(0, clamp(limit)));
    }

    /**
     * How many of each kind of action the account has on record, as
     * {@code {"TASK": {"CREATE": 12, "DELETE": 3}, ...}}. Features with nothing against
     * them are left out, so an empty map means the account has done nothing yet.
     */
    public Map<UserActionFeature, Map<UserActionType, Long>> counts(String userId) {
        Map<UserActionFeature, Map<UserActionType, Long>> totals = new EnumMap<>(UserActionFeature.class);
        for (UserAction entry : repository.findByUserId(userId)) {
            totals.computeIfAbsent(entry.getFeature(), f -> new EnumMap<>(UserActionType.class))
                    .merge(entry.getAction(), 1L, Long::sum);
        }
        return totals;
    }

    /** Called when an account is deleted, so nothing is left pointing at a user who is gone */
    public void deleteForUser(String userId) {
        repository.deleteByUserId(userId);
    }

    private static int clamp(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_ENTRIES);
    }
}
