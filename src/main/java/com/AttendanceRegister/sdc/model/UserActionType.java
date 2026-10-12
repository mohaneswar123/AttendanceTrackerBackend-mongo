package com.AttendanceRegister.sdc.model;

/**
 * What happened to the thing. Deliberately only three, because the log records that
 * something was done and not what it said.
 */
public enum UserActionType {
    /** Something new was added */
    CREATE,
    /** Something that already existed was changed */
    UPDATE,
    /** Something was removed */
    DELETE
}
