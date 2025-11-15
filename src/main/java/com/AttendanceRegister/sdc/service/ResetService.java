package com.AttendanceRegister.sdc.service;

import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.AttendanceRecordRepository;
import com.AttendanceRegister.sdc.Repository.SubjectRepository;

@Service
public class ResetService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SubjectRepository subjectRepository;

    public ResetService(AttendanceRecordRepository attendanceRecordRepository,
                        SubjectRepository subjectRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.subjectRepository = subjectRepository;
    }

    // ✅ Delete all attendance records and subjects of a user
    public void resetUserData(String userId) {
        var attendanceRecords = attendanceRecordRepository.findByUserId(userId);
        var subjects = subjectRepository.findByUserId(userId);

        attendanceRecordRepository.deleteAll(attendanceRecords);
        subjectRepository.deleteAll(subjects);
    }
}
