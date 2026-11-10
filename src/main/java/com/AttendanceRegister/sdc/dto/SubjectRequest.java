package com.AttendanceRegister.sdc.dto;

import com.AttendanceRegister.sdc.model.TimetableColor;

/**
 * A subject's name and the colour the student gave it.
 *
 * A null colour means "leave it alone" on an edit, so renaming a subject does not quietly
 * reset the colour it was given.
 */
public record SubjectRequest(String name, TimetableColor color) {
}
