package com.AttendanceRegister.sdc.model;

/** What an admin did. Stored in the audit log, so names are never reused for something else. */
public enum AdminActionType {
    /** Access granted for a number of days, counted from today */
    ACTIVATE,
    /** Days added on top of what the account already had */
    EXTEND,
    /** Access withdrawn */
    DEACTIVATE,
    /** A new password set for someone who had forgotten theirs */
    SET_PASSWORD,
    /** The account and everything it owned removed */
    DELETE
}
