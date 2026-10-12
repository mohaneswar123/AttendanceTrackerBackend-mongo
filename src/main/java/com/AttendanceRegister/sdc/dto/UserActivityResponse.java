package com.AttendanceRegister.sdc.dto;

import java.util.List;
import java.util.Map;

import com.AttendanceRegister.sdc.model.UserAction;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;

/**
 * What an account has been doing: the totals per feature, and the most recent entries.
 *
 * {@code keptForDays} is sent so the page can say how far back the log goes, rather than
 * implying it holds everything the account has ever done.
 */
public record UserActivityResponse(
        Map<UserActionFeature, Map<UserActionType, Long>> counts,
        List<UserAction> entries,
        int keptForDays) {
}
