package com.AttendanceRegister.sdc.service;

import java.util.List;

import org.springframework.stereotype.Service;
import com.AttendanceRegister.sdc.Repository.AttendanceRecordRepository;
import com.AttendanceRegister.sdc.Repository.SubjectRepository;
import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Subject;

@Service
public class SubjectService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    public SubjectService(SubjectRepository subjectRepository, UserRepository userRepository, AttendanceRecordRepository attendanceRecordRepository) {
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    // ✅ Add a new subject for a user
    public Subject addSubject(String userId, String subjectName) {
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("User not found with ID: " + userId);
        }
        if (subjectName == null || subjectName.isBlank()) {
            throw ApiException.badRequest("Subject name is required");
        }
        return subjectRepository.save(new Subject(subjectName.trim(), userId));
    }

    // ✅ Get all subjects for a user
    public List<Subject> getSubjectsByUser(String userId) {
        return subjectRepository.findByUserId(userId);
    }

    // ✅ Delete a user's subject together with its attendance records
    public void deleteSubjectForUser(String subjectId, String userId) {
        Subject subject = subjectRepository.findById(subjectId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Subject not found with ID: " + subjectId));

        attendanceRecordRepository.deleteBySubjectIdAndUserId(subject.getId(), userId);
        subjectRepository.deleteById(subject.getId());
    }
}
