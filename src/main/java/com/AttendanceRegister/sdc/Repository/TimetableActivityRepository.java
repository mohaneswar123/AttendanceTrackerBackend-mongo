package com.AttendanceRegister.sdc.Repository;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.TimetableActivity;

public interface TimetableActivityRepository extends MongoRepository<TimetableActivity, String> {
    // Days are stored by name, so the service sorts by the weekday's own order
    List<TimetableActivity> findByModeId(String modeId);

    List<TimetableActivity> findByModeIdAndDayOfWeekOrderByStartMinutesAsc(String modeId, DayOfWeek dayOfWeek);

    long countByModeId(String modeId);

    void deleteByModeId(String modeId);

    void deleteByModeIdAndDayOfWeek(String modeId, DayOfWeek dayOfWeek);

    void deleteByUserId(String userId);
}
