package com.AttendanceRegister.sdc.Repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.CalendarEvent;

// Filtered listing (date range and search) is built with MongoTemplate in CalendarEventService
public interface CalendarEventRepository extends MongoRepository<CalendarEvent, String> {
    void deleteByUserId(String userId);
}
