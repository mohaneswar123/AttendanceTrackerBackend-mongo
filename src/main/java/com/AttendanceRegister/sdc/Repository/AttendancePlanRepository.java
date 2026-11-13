package com.AttendanceRegister.sdc.Repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.AttendancePlan;

public interface AttendancePlanRepository extends MongoRepository<AttendancePlan, String> {

    Optional<AttendancePlan> findByUserId(String userId);

    void deleteByUserId(String userId);
}
