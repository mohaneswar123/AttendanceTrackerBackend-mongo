package com.AttendanceRegister.sdc.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.HabitEntry;

public interface HabitEntryRepository extends MongoRepository<HabitEntry, String> {

    List<HabitEntry> findByUserId(String userId);

    List<HabitEntry> findByUserIdAndDateBetween(String userId, LocalDate from, LocalDate to);

    List<HabitEntry> findByHabitId(String habitId);

    Optional<HabitEntry> findByHabitIdAndDate(String habitId, LocalDate date);

    boolean existsByHabitId(String habitId);

    void deleteByHabitId(String habitId);

    void deleteByUserId(String userId);
}
