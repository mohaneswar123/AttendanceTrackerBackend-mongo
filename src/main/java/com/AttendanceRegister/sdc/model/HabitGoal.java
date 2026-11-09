package com.AttendanceRegister.sdc.model;

// What counts as doing the habit once
public enum HabitGoal {
    /** Did it or didn't: "Revise notes" */
    YES_NO,
    /** A number to reach: "Drink 8 glasses of water" */
    COUNT,
    /** Minutes to reach: "Study 60 min" */
    DURATION
}
