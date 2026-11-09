package com.AttendanceRegister.sdc.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.Habit;

public interface HabitRepository extends MongoRepository<Habit, String> {

    List<Habit> findByUserIdOrderByPositionAsc(String userId);

    Optional<Habit> findByIdAndUserId(String id, String userId);

    long countByUserIdAndArchivedFalse(String userId);

    void deleteByUserId(String userId);
}
