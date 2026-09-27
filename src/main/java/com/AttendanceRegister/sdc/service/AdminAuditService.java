package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.AdminActionRepository;
import com.AttendanceRegister.sdc.model.AdminAction;
import com.AttendanceRegister.sdc.model.AdminActionType;
import com.AttendanceRegister.sdc.model.User;

/**
 * The record of what admins have done to accounts.
 *
 * Entries are written after the change has succeeded, so the log never claims something
 * that did not happen, and they are never edited or deleted — including when the account
 * itself is deleted, which is the case the log exists for.
 */
@Service
public class AdminAuditService {

    /** Enough to answer "what happened recently" without paging */
    public static final int MAX_ENTRIES = 200;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final AdminActionRepository repository;
    private final Clock clock;

    public AdminAuditService(AdminActionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(String adminEmail, AdminActionType action, User target, String detail) {
        repository.save(new AdminAction(
                adminEmail,
                action,
                target.getId(),
                target.getEmail(),
                detail,
                clock.instant()));
    }

    /** "30 days, paid till 27 Oct 2026" */
    public static String accessDetail(int days, LocalDate paidTill) {
        return days + (days == 1 ? " day" : " days") + ", paid till " + paidTill.format(DATE);
    }

    public List<AdminAction> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, clamp(limit)));
    }

    public List<AdminAction> forUser(String userId, int limit) {
        return repository.findByTargetUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, clamp(limit)));
    }

    private static int clamp(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_ENTRIES);
    }
}
