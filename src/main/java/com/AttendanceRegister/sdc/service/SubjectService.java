package com.AttendanceRegister.sdc.service;

import java.util.List;

import org.springframework.stereotype.Service;
import com.AttendanceRegister.sdc.Repository.AttendanceRecordRepository;
import com.AttendanceRegister.sdc.Repository.SubjectRepository;
import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Subject;
import com.AttendanceRegister.sdc.model.TimetableColor;

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

    static final int MAX_NAME_LENGTH = 40;

    // ✅ Add a new subject for a user
    public Subject addSubject(String userId, String subjectName, TimetableColor color) {
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("User not found with ID: " + userId);
        }
        String name = validName(subjectName);
        requireNameFree(userId, name, null);

        Subject subject = new Subject(name, userId);
        subject.setColor(color);
        return subjectRepository.save(subject);
    }

    /**
     * Rename a subject, recolour it, or both.
     *
     * Attendance records point at the subject by id, so a rename carries through history
     * and reports on its own: nothing is rewritten and no history is lost.
     */
    public Subject updateSubject(String subjectId, String userId, String subjectName, TimetableColor color) {
        Subject subject = getOwned(subjectId, userId);

        if (subjectName != null) {
            String name = validName(subjectName);
            requireNameFree(userId, name, subjectId);
            subject.setName(name);
        }
        // Null means "leave the colour alone", so a rename does not reset it
        if (color != null) {
            subject.setColor(color);
        }
        return subjectRepository.save(subject);
    }

    private String validName(String subjectName) {
        if (subjectName == null || subjectName.isBlank()) {
            throw ApiException.badRequest("Subject name is required");
        }
        String name = subjectName.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw ApiException.badRequest("Subject name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    /**
     * Two subjects with the same name are indistinguishable everywhere they are shown, and
     * the attendance recorded against them cannot be told apart afterwards.
     */
    private void requireNameFree(String userId, String name, String exceptId) {
        boolean taken = subjectRepository.findByUserId(userId).stream()
                .filter(subject -> !subject.getId().equals(exceptId))
                .anyMatch(subject -> name.equalsIgnoreCase(subject.getName()));
        if (taken) {
            throw ApiException.badRequest("You already have a subject called \"" + name + "\"");
        }
    }

    private Subject getOwned(String subjectId, String userId) {
        return subjectRepository.findById(subjectId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Subject not found with ID: " + subjectId));
    }

    // ✅ Get all subjects for a user
    public List<Subject> getSubjectsByUser(String userId) {
        return subjectRepository.findByUserId(userId);
    }

    // ✅ Delete a user's subject together with its attendance records
    public void deleteSubjectForUser(String subjectId, String userId) {
        Subject subject = getOwned(subjectId, userId);

        attendanceRecordRepository.deleteBySubjectIdAndUserId(subject.getId(), userId);
        subjectRepository.deleteById(subject.getId());
    }
}
