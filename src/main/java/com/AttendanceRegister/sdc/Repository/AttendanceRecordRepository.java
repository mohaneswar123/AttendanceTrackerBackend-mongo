package com.AttendanceRegister.sdc.Repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.AttendanceRecord;

public interface AttendanceRecordRepository extends MongoRepository<AttendanceRecord, String> {
    List<AttendanceRecord> findByUserId(String userId);
    boolean existsByUserIdAndSubjectIdAndDateAndClassNumber(String userId, String subjectId, String date, int classNumber);
    void deleteBySubjectIdAndDateAndClassNumber(String subjectId, String date, int classNumber);
    void deleteBySubjectIdAndUserId(String subjectId, String userId);

}
