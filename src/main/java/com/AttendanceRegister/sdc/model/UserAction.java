package com.AttendanceRegister.sdc.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One thing a student did, written after the change has succeeded.
 *
 * It records that something happened and to which part of the app, and nothing about what
 * the student wrote: no titles, no notes, no dates they chose. An admin looking at this
 * can see whether and how an account is being used without reading its contents.
 *
 * Entries expire on their own after {@code UserAuditService.KEEP_FOR_DAYS}, since the log
 * would otherwise grow for as long as the account lives.
 */
@Document(collection = "user_action_table")
public class UserAction {

    @Id
    private String id;

    /** Whose data was touched */
    private String userId;

    private UserActionFeature feature;

    private UserActionType action;

    /**
     * True when an admin did this on the student's behalf, so the log never credits the
     * student with something they did not do.
     */
    private boolean byAdmin;

    /** Indexed by the startup index component, which also expires old entries */
    private Instant at;

    public UserAction() {
    }

    public UserAction(String userId, UserActionFeature feature, UserActionType action, boolean byAdmin, Instant at) {
        this.userId = userId;
        this.feature = feature;
        this.action = action;
        this.byAdmin = byAdmin;
        this.at = at;
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

    public UserActionFeature getFeature() {
        return feature;
    }

    public void setFeature(UserActionFeature feature) {
        this.feature = feature;
    }

    public UserActionType getAction() {
        return action;
    }

    public void setAction(UserActionType action) {
        this.action = action;
    }

    public boolean isByAdmin() {
        return byAdmin;
    }

    public void setByAdmin(boolean byAdmin) {
        this.byAdmin = byAdmin;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }
}
