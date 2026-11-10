package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.AttendanceRecordRepository;
import com.AttendanceRegister.sdc.Repository.SubjectRepository;
import com.AttendanceRegister.sdc.Repository.UserRepository;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Subject;
import com.AttendanceRegister.sdc.model.TimetableColor;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    private static final String USER = "u1";
    private static final String OTHER_USER = "u2";
    private static final String SUBJECT = "s1";

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    private SubjectService service;

    @BeforeEach
    void setUp() {
        service = new SubjectService(subjectRepository, userRepository, attendanceRecordRepository);
    }

    private static Subject subject(String id, String name, String userId) {
        Subject subject = new Subject(name, userId);
        subject.setId(id);
        return subject;
    }

    private void userExists() {
        when(userRepository.existsById(USER)).thenReturn(true);
    }

    private void owns(Subject... subjects) {
        when(subjectRepository.findByUserId(USER)).thenReturn(List.of(subjects));
    }

    private Subject saved() {
        return captureSaved();
    }

    private Subject captureSaved() {
        org.mockito.ArgumentCaptor<Subject> captor = org.mockito.ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- Adding

    @Test
    void aSubjectIsSavedTrimmedWithTheColourItWasGiven() {
        userExists();
        owns();
        when(subjectRepository.save(any(Subject.class))).thenAnswer(call -> call.getArgument(0));

        service.addSubject(USER, "  Mathematics  ", TimetableColor.CYAN);

        assertThat(saved().getName()).isEqualTo("Mathematics");
        assertThat(saved().getColor()).isEqualTo(TimetableColor.CYAN);
    }

    @Test
    void aSubjectMayBeAddedWithoutAColour() {
        userExists();
        owns();
        when(subjectRepository.save(any(Subject.class))).thenAnswer(call -> call.getArgument(0));

        service.addSubject(USER, "Physics", null);

        // Null is allowed: the page works a colour out from the name, as it did for every
        // subject before colours could be chosen
        assertThat(saved().getColor()).isNull();
    }

    @Test
    void aBlankNameIsRefused() {
        userExists();

        Throwable thrown = catchThrowable(() -> service.addSubject(USER, "   ", null));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void anOverlongNameIsRefused() {
        userExists();

        Throwable thrown = catchThrowable(
                () -> service.addSubject(USER, "x".repeat(SubjectService.MAX_NAME_LENGTH + 1), null));

        assertThat(thrown).isInstanceOf(ApiException.class);
        verify(subjectRepository, never()).save(any());
    }

    /**
     * Two subjects with the same name cannot be told apart on any screen, and the
     * attendance recorded against them cannot be separated afterwards.
     */
    @Test
    void aSecondSubjectWithTheSameNameIsRefusedWhateverTheCase() {
        userExists();
        owns(subject(SUBJECT, "Mathematics", USER));

        Throwable thrown = catchThrowable(() -> service.addSubject(USER, "mathematics", null));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(thrown).hasMessageContaining("already have a subject");
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void addingForAUserThatDoesNotExistIsNotFound() {
        when(userRepository.existsById(USER)).thenReturn(false);

        Throwable thrown = catchThrowable(() -> service.addSubject(USER, "Physics", null));

        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Renaming and recolouring

    @Test
    void aSubjectCanBeRenamed() {
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SUBJECT, "Maths", USER)));
        owns(subject(SUBJECT, "Maths", USER));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(call -> call.getArgument(0));

        service.updateSubject(SUBJECT, USER, "Mathematics", null);

        assertThat(saved().getName()).isEqualTo("Mathematics");
    }

    /**
     * Records point at the subject by id, so renaming rewrites nothing. This is the test
     * that would fail if someone ever "fixed" a rename by rewriting the records.
     */
    @Test
    void renamingTouchesNoAttendanceRecords() {
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SUBJECT, "Maths", USER)));
        owns(subject(SUBJECT, "Maths", USER));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(call -> call.getArgument(0));

        service.updateSubject(SUBJECT, USER, "Mathematics", null);

        verify(attendanceRecordRepository, never()).deleteBySubjectIdAndUserId(any(), any());
        verify(attendanceRecordRepository, never()).saveAll(any());
    }

    @Test
    void aColourCanBeChangedOnItsOwn() {
        Subject existing = subject(SUBJECT, "Maths", USER);
        existing.setColor(TimetableColor.CYAN);
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(existing));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(call -> call.getArgument(0));

        service.updateSubject(SUBJECT, USER, null, TimetableColor.ROSE);

        assertThat(saved().getName()).isEqualTo("Maths");
        assertThat(saved().getColor()).isEqualTo(TimetableColor.ROSE);
    }

    /** A rename must not quietly throw away the colour the student picked */
    @Test
    void renamingKeepsTheColourItAlreadyHad() {
        Subject existing = subject(SUBJECT, "Maths", USER);
        existing.setColor(TimetableColor.EMERALD);
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(existing));
        owns(existing);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(call -> call.getArgument(0));

        service.updateSubject(SUBJECT, USER, "Mathematics", null);

        assertThat(saved().getColor()).isEqualTo(TimetableColor.EMERALD);
    }

    @Test
    void aSubjectKeepsItsOwnNameWhenOnlyItsColourChanges() {
        Subject existing = subject(SUBJECT, "Maths", USER);
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(existing));
        owns(existing, subject("s2", "Physics", USER));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(call -> call.getArgument(0));

        // Renaming it to what it is already called is not a clash with itself
        service.updateSubject(SUBJECT, USER, "Maths", TimetableColor.AMBER);

        assertThat(saved().getName()).isEqualTo("Maths");
    }

    @Test
    void renamingOntoAnotherSubjectsNameIsRefused() {
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SUBJECT, "Maths", USER)));
        owns(subject(SUBJECT, "Maths", USER), subject("s2", "Physics", USER));

        Throwable thrown = catchThrowable(() -> service.updateSubject(SUBJECT, USER, "physics", null));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(thrown).hasMessageContaining("already have a subject");
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void anotherStudentsSubjectIsNotFoundRatherThanForbidden() {
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SUBJECT, "Maths", OTHER_USER)));
        lenient().when(subjectRepository.findByUserId(USER)).thenReturn(List.of());

        Throwable thrown = catchThrowable(() -> service.updateSubject(SUBJECT, USER, "Mine now", null));

        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(subjectRepository, never()).save(any());
    }

    // --- Deleting

    @Test
    void deletingASubjectTakesItsAttendanceWithIt() {
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SUBJECT, "Maths", USER)));

        service.deleteSubjectForUser(SUBJECT, USER);

        verify(attendanceRecordRepository).deleteBySubjectIdAndUserId(SUBJECT, USER);
        verify(subjectRepository).deleteById(SUBJECT);
    }

    @Test
    void anotherStudentsSubjectCannotBeDeleted() {
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SUBJECT, "Maths", OTHER_USER)));

        Throwable thrown = catchThrowable(() -> service.deleteSubjectForUser(SUBJECT, USER));

        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(subjectRepository, never()).deleteById(any());
        verify(attendanceRecordRepository, never()).deleteBySubjectIdAndUserId(any(), any());
    }
}
