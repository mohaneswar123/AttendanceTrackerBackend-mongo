package com.AttendanceRegister.sdc.Repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.TimetableMode;

public interface TimetableModeRepository extends MongoRepository<TimetableMode, String> {
    List<TimetableMode> findByUserIdOrderByPositionAsc(String userId);

    long countByUserId(String userId);

    void deleteByUserId(String userId);
}
