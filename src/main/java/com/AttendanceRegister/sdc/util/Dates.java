package com.AttendanceRegister.sdc.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.AttendanceRegister.sdc.exception.ApiException;

public final class Dates {

    private Dates() {}

    // Parses "YYYY-MM-DD", rejecting impossible dates such as 2026-02-30.
    public static LocalDate parseIsoDate(String value, String label) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw ApiException.badRequest(label + " must be a real date in YYYY-MM-DD format");
        }
    }
}
