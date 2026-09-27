package com.AttendanceRegister.sdc.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One thing an admin did to an account. Written after the change succeeds, so the log
 * only holds what actually happened.
 *
 * The target's email is stored as text rather than looked up through targetUserId,
 * because the account may be deleted and the entry still has to say who it was.
 */
@Document(collection = "admin_action_table")
public class AdminAction {

    @Id
    private String id;

    /** Who did it. The admin's email, since admin accounts are few and created by hand. */
    private String adminEmail;

    private AdminActionType action;

    private String targetUserId;

    /** The account's email at the time, kept even after the account is gone */
    private String targetEmail;

    /** What changed, in words: "30 days, paid till 27 Oct 2026" */
    private String detail;

    /** Indexed because the log is always read newest first */
    @Indexed
    private Instant createdAt;

    public AdminAction() {
    }

    public AdminAction(String adminEmail, AdminActionType action, String targetUserId, String targetEmail,
            String detail, Instant createdAt) {
        this.adminEmail = adminEmail;
        this.action = action;
        this.targetUserId = targetUserId;
        this.targetEmail = targetEmail;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public AdminActionType getAction() {
        return action;
    }

    public void setAction(AdminActionType action) {
        this.action = action;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetEmail() {
        return targetEmail;
    }

    public void setTargetEmail(String targetEmail) {
        this.targetEmail = targetEmail;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
