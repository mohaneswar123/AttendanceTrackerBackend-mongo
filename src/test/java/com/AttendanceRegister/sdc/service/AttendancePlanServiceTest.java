package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.AttendancePlanRepository;
import com.AttendanceRegister.sdc.Repository.SubjectRepository;
import com.AttendanceRegister.sdc.dto.PlanDayRequest;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.AttendancePlan;
import com.AttendanceRegister.sdc.model.AttendancePlan.PlannedClass;
import com.AttendanceRegister.sdc.model.Subject;

@ExtendWith(MockitoExtension.class)
class AttendancePlanServiceTest {

    private static final String USER = "u1";

    @Mock
    private AttendancePlanRepository planRepository;

    @Mock
    private SubjectRepository subjectRepository;

    private AttendancePlanService service;

    @BeforeEach
    void setUp() {
        service = new AttendancePlanService(planRepository, subjectRepository);
    }

    private Subject subject(String id) {
        Subject subject = new Subject("Maths", USER);
        subject.setId(id);
        return subject;
    }

    private static PlanDayRequest day(PlanDayRequest.Entry... entries) {
        return new PlanDayRequest(List.of(entries));
    }

    private static PlanDayRequest.Entry entry(String subjectId, int hours) {
        return new PlanDayRequest.Entry(subjectId, hours);
    }

    @Test
    void aDayIsWrittenWholeAndReadBack() {
        when(subjectRepository.findByUserId(USER)).thenReturn(List.of(subject("s1"), subject("s2")));
        when(planRepository.findByUserId(USER)).thenReturn(Optional.empty());

        // The same subject twice in one day is two classes, not a mistake
        Map<String, List<PlannedClass>> plan = service.setDay(USER, "monday",
                day(entry("s1", 1), entry("s2", 2), entry("s1", 1)));

        ArgumentCaptor<AttendancePlan> saved = ArgumentCaptor.forClass(AttendancePlan.class);
        verify(planRepository).save(saved.capture());
        List<PlannedClass> monday = saved.getValue().getDays().get("MONDAY");
        assertThat(monday).extracting(PlannedClass::getSubjectId).containsExactly("s1", "s2", "s1");
        assertThat(monday).extracting(PlannedClass::getHours).containsExactly(1, 2, 1);
        assertThat(plan).containsKey("MONDAY");
    }

    @Test
    void settingADayReplacesItRatherThanAppending() {
        AttendancePlan existing = new AttendancePlan(USER);
        existing.getDays().put("MONDAY", List.of(new PlannedClass("s1", 1), new PlannedClass("s1", 1)));
        when(planRepository.findByUserId(USER)).thenReturn(Optional.of(existing));
        when(subjectRepository.findByUserId(USER)).thenReturn(List.of(subject("s1"), subject("s2")));

        service.setDay(USER, "MONDAY", day(entry("s2", 3)));

        ArgumentCaptor<AttendancePlan> saved = ArgumentCaptor.forClass(AttendancePlan.class);
        verify(planRepository).save(saved.capture());
        assertThat(saved.getValue().getDays().get("MONDAY"))
                .extracting(PlannedClass::getSubjectId).containsExactly("s2");
    }

    @Test
    void anEmptyListClearsTheDay() {
        AttendancePlan existing = new AttendancePlan(USER);
        existing.getDays().put("FRIDAY", List.of(new PlannedClass("s1", 1)));
        when(planRepository.findByUserId(USER)).thenReturn(Optional.of(existing));
        when(subjectRepository.findByUserId(USER)).thenReturn(List.of(subject("s1")));

        service.setDay(USER, "FRIDAY", new PlanDayRequest(List.of()));

        ArgumentCaptor<AttendancePlan> saved = ArgumentCaptor.forClass(AttendancePlan.class);
        verify(planRepository).save(saved.capture());
        assertThat(saved.getValue().getDays()).doesNotContainKey("FRIDAY");
    }

    @Test
    void someoneElsesSubjectCannotBePlanned() {
        when(subjectRepository.findByUserId(USER)).thenReturn(List.of(subject("s1")));

        Throwable thrown = catchThrowable(() -> service.setDay(USER, "MONDAY", day(entry("not-mine", 1))));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(planRepository, never()).save(any());
    }

    @Test
    void hoursOutsideTheFormsRangeAreRefused() {
        when(subjectRepository.findByUserId(USER)).thenReturn(List.of(subject("s1")));

        Throwable thrown = catchThrowable(() -> service.setDay(USER, "MONDAY", day(entry("s1", 4))));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aMadeUpWeekdayIsRefusedOutLoud() {
        Throwable thrown = catchThrowable(() -> service.setDay(USER, "SOMEDAY", day()));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void readingThePlanLeavesOutDeletedSubjects() {
        AttendancePlan existing = new AttendancePlan(USER);
        existing.getDays().put("MONDAY", List.of(new PlannedClass("gone", 1), new PlannedClass("s1", 2)));
        existing.getDays().put("TUESDAY", List.of(new PlannedClass("gone", 1)));
        when(planRepository.findByUserId(USER)).thenReturn(Optional.of(existing));
        when(subjectRepository.findByUserId(USER)).thenReturn(List.of(subject("s1")));

        Map<String, List<PlannedClass>> plan = service.getPlan(USER);

        assertThat(plan.get("MONDAY")).extracting(PlannedClass::getSubjectId).containsExactly("s1");
        // A day left with nothing real in it is not a day worth returning
        assertThat(plan).doesNotContainKey("TUESDAY");
    }

    @Test
    void deletingASubjectPrunesItFromEveryDay() {
        AttendancePlan existing = new AttendancePlan(USER);
        existing.getDays().put("MONDAY",
                new java.util.ArrayList<>(List.of(new PlannedClass("s1", 1), new PlannedClass("s2", 2))));
        existing.getDays().put("TUESDAY", new java.util.ArrayList<>(List.of(new PlannedClass("s1", 1))));
        when(planRepository.findByUserId(USER)).thenReturn(Optional.of(existing));

        service.removeSubjectFromPlan(USER, "s1");

        ArgumentCaptor<AttendancePlan> saved = ArgumentCaptor.forClass(AttendancePlan.class);
        verify(planRepository).save(saved.capture());
        assertThat(saved.getValue().getDays().get("MONDAY"))
                .extracting(PlannedClass::getSubjectId).containsExactly("s2");
        assertThat(saved.getValue().getDays()).doesNotContainKey("TUESDAY");
    }

    @Test
    void aStudentWithNoPlanGetsAnEmptyWeekNotAnError() {
        when(planRepository.findByUserId(USER)).thenReturn(Optional.empty());

        assertThat(service.getPlan(USER)).isEmpty();
    }
}
