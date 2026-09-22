package com.AttendanceRegister.sdc.dto;

// Returned by the login endpoints: the bearer token plus the signed-in account.
public record AuthResponse<T>(String token, T user) {}
