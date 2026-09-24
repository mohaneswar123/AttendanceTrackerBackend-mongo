package com.AttendanceRegister.sdc.Repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.TimetablePreference;

public interface TimetablePreferenceRepository extends MongoRepository<TimetablePreference, String> {
    Optional<TimetablePreference> findByUserId(String userId);

    void deleteByUserId(String userId);
}
