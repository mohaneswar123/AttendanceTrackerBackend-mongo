package com.AttendanceRegister.sdc.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.AttendanceRecordRepository;
import com.AttendanceRegister.sdc.Repository.SubjectRepository;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.AttendanceRecord;
import com.AttendanceRegister.sdc.util.Dates;

@Service
public class AttendanceRecordService {

    private static final Set<String> STATUSES = Set.of("Present", "Absent", "No Class");
    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 3;

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SubjectRepository subjectRepository;

    public AttendanceRecordService(
            AttendanceRecordRepository attendanceRecordRepository,
            SubjectRepository subjectRepository
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.subjectRepository = subjectRepository;
    }

    // ✅ Add a new attendance record; classNumber is the class length in hours
    public AttendanceRecord addRecord(String userId, String subjectId, String status, String date, int classNumber) {
        requireSubjectOfUser(subjectId, userId);
        validateStatus(status);
        validateDate(date);
        validateHours(classNumber);

        AttendanceRecord record = new AttendanceRecord(status, date, classNumber, userId, subjectId);
        return attendanceRecordRepository.save(record);
    }

    // ✅ Get all attendance records for a user
    public List<AttendanceRecord> getRecordsByUser(String userId) {
        return attendanceRecordRepository.findByUserId(userId);
    }

    public AttendanceRecord getRecord(String id) {
        return attendanceRecordRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Record not found with ID: " + id));
    }

    // ✅ Update a record; fields left out of the request keep their current value
    public AttendanceRecord updateRecord(AttendanceRecord existing, AttendanceRecord updated) {
        if (updated.getStatus() != null) {
            validateStatus(updated.getStatus());
            existing.setStatus(updated.getStatus());
        }
        if (updated.getDate() != null) {
            validateDate(updated.getDate());
            existing.setDate(updated.getDate());
        }
        if (updated.getSubjectId() != null) {
            requireSubjectOfUser(updated.getSubjectId(), existing.getUserId());
            existing.setSubjectId(updated.getSubjectId());
        }
        if (updated.getClassNumber() != 0) {
            validateHours(updated.getClassNumber());
            existing.setClassNumber(updated.getClassNumber());
        }
        return attendanceRecordRepository.save(existing);
    }

    // ✅ Delete a record
    public void deleteRecord(AttendanceRecord record) {
        attendanceRecordRepository.deleteById(record.getId());
    }

    private void requireSubjectOfUser(String subjectId, String userId) {
        subjectRepository.findById(subjectId)
                .filter(subject -> userId.equals(subject.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Subject not found with ID: " + subjectId));
    }

    private static void validateStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("Status must be Present, Absent or No Class");
        }
    }

    private static void validateDate(String date) {
        Dates.parseIsoDate(date, "Date");
    }

    private static void validateHours(int hours) {
        if (hours < MIN_HOURS || hours > MAX_HOURS) {
            throw ApiException.badRequest("Class length must be between " + MIN_HOURS + " and " + MAX_HOURS + " hours");
        }
    }
}
