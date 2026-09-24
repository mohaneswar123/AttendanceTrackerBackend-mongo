package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.TimetableActivityRepository;
import com.AttendanceRegister.sdc.Repository.TimetableModeRepository;
import com.AttendanceRegister.sdc.Repository.TimetablePreferenceRepository;
import com.AttendanceRegister.sdc.dto.CopyDayRequest;
import com.AttendanceRegister.sdc.dto.TimetableActivityRequest;
import com.AttendanceRegister.sdc.dto.TimetableActivityResponse;
import com.AttendanceRegister.sdc.dto.TimetableModeRequest;
import com.AttendanceRegister.sdc.dto.TimetableModeResponse;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.ActivityCategory;
import com.AttendanceRegister.sdc.model.TimetableActivity;
import com.AttendanceRegister.sdc.model.TimetableColor;
import com.AttendanceRegister.sdc.model.TimetableMode;
import com.AttendanceRegister.sdc.model.TimetablePreference;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Mock
    private TimetableModeRepository modeRepository;
    @Mock
    private TimetableActivityRepository activityRepository;
    @Mock
    private TimetablePreferenceRepository preferenceRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    private TimetableService service;

    @BeforeEach
    void setUp() {
        service = new TimetableService(modeRepository, activityRepository, preferenceRepository, mongoTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- helpers

    private static TimetableMode mode(String id, String userId, double position) {
        TimetableMode mode = new TimetableMode();
        mode.setId(id);
        mode.setUserId(userId);
        mode.setName("College Mode");
        mode.setIcon("🎓");
        mode.setColor(TimetableColor.VIOLET);
        mode.setPosition(position);
        return mode;
    }

    private static TimetableActivity activity(String id, String title, int start, int end) {
        TimetableActivity activity = new TimetableActivity();
        activity.setId(id);
        activity.setUserId("u1");
        activity.setModeId("m1");
        activity.setDayOfWeek(DayOfWeek.MONDAY);
        activity.setTitle(title);
        activity.setStartMinutes(start);
        activity.setEndMinutes(end);
        return activity;
    }

    private static TimetableActivityRequest activityRequest(DayOfWeek day, String title, String start, String end) {
        return new TimetableActivityRequest(day, title, ActivityCategory.CLASS, start, end);
    }

    private void givenModes(String userId, TimetableMode... modes) {
        lenient().when(modeRepository.findByUserIdOrderByPositionAsc(userId)).thenReturn(List.of(modes));
        for (TimetableMode mode : modes) {
            lenient().when(modeRepository.findById(mode.getId())).thenReturn(Optional.of(mode));
        }
        lenient().when(activityRepository.countByModeId(anyString())).thenReturn(0L);
    }

    private void givenActiveModeId(String userId, String activeModeId) {
        TimetablePreference preference = new TimetablePreference();
        preference.setUserId(userId);
        preference.setActiveModeId(activeModeId);
        lenient().when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
    }

    private void givenDay(String modeId, DayOfWeek day, TimetableActivity... activities) {
        lenient().when(activityRepository.findByModeIdAndDayOfWeekOrderByStartMinutesAsc(modeId, day))
                .thenReturn(List.of(activities));
    }

    private void savesActivities() {
        lenient().when(activityRepository.save(any(TimetableActivity.class))).thenAnswer(call -> {
            TimetableActivity saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId("new");
            return saved;
        });
    }

    // The single field the active mode is written to
    private String activeIdWrittenTo(String userId) {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).upsert(query.capture(), update.capture(), eq(TimetablePreference.class));
        assertThat(query.getValue().getQueryObject().get("userId")).isEqualTo(userId);
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        return (String) set.get("activeModeId");
    }

    private static void assertApiError(Throwable thrown, HttpStatus status, String code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(status);
            assertThat(ex.getCode()).isEqualTo(code);
        });
    }

    // --- Modes

    @Test
    void theFirstModeBecomesActive() {
        givenModes("u1");
        when(modeRepository.save(any(TimetableMode.class))).thenAnswer(call -> {
            TimetableMode saved = call.getArgument(0);
            saved.setId("m1");
            return saved;
        });

        TimetableModeResponse created = service.createMode("u1",
                new TimetableModeRequest("  College Mode  ", "🎓", TimetableColor.VIOLET));

        assertThat(created.name()).isEqualTo("College Mode");
        assertThat(created.active()).isTrue();
        assertThat(activeIdWrittenTo("u1")).isEqualTo("m1");
    }

    @Test
    void laterModesDoNotStealTheActiveOne() {
        givenModes("u1", mode("m1", "u1", 1024));
        givenActiveModeId("u1", "m1");
        when(modeRepository.save(any(TimetableMode.class))).thenAnswer(call -> {
            TimetableMode saved = call.getArgument(0);
            saved.setId("m2");
            return saved;
        });

        TimetableModeResponse created = service.createMode("u1", new TimetableModeRequest("Home", "🏠", TimetableColor.CYAN));

        assertThat(created.active()).isFalse();
        assertThat(created.id()).isEqualTo("m2");
        verify(mongoTemplate, never()).upsert(any(Query.class), any(UpdateDefinition.class), eq(TimetablePreference.class));
    }

    @Test
    void activatingWritesOnlyTheOneField() {
        givenModes("u1", mode("m1", "u1", 1024), mode("m2", "u1", 2048));
        givenActiveModeId("u1", "m1");

        service.activateMode("u1", "m2");

        assertThat(activeIdWrittenTo("u1")).isEqualTo("m2");
        verify(modeRepository, never()).save(any());
        verify(modeRepository, never()).saveAll(any());
    }

    @Test
    void deletingTheActiveModeRemovesItsWeekAndPromotesTheNext() {
        TimetableMode first = mode("m1", "u1", 1024);
        TimetableMode second = mode("m2", "u1", 2048);
        when(modeRepository.findById("m1")).thenReturn(Optional.of(first));
        givenActiveModeId("u1", "m1");
        // The list is only read after the delete, so only m2 is left by then
        when(modeRepository.findByUserIdOrderByPositionAsc("u1")).thenReturn(List.of(second));
        lenient().when(activityRepository.countByModeId(anyString())).thenReturn(0L);

        service.deleteMode("u1", "m1");

        verify(activityRepository).deleteByModeId("m1");
        verify(modeRepository).delete(first);
        assertThat(activeIdWrittenTo("u1")).isEqualTo("m2");
    }

    @Test
    void deletingTheLastModeClearsTheActiveId() {
        TimetableMode only = mode("m1", "u1", 1024);
        when(modeRepository.findById("m1")).thenReturn(Optional.of(only));
        givenActiveModeId("u1", "m1");
        when(modeRepository.findByUserIdOrderByPositionAsc("u1")).thenReturn(List.of());

        assertThat(service.deleteMode("u1", "m1")).isEmpty();
        assertThat(activeIdWrittenTo("u1")).isNull();
    }

    @Test
    void anActiveIdPointingAtADeletedModeFallsBackToTheFirst() {
        givenModes("u1", mode("m1", "u1", 1024), mode("m2", "u1", 2048));
        givenActiveModeId("u1", "gone");

        List<TimetableModeResponse> modes = service.getModes("u1");

        assertThat(modes).extracting(TimetableModeResponse::active).containsExactly(true, false);
        verify(mongoTemplate, never()).upsert(any(Query.class), any(UpdateDefinition.class), eq(TimetablePreference.class));
    }

    @Test
    void modeDetailsAreChecked() {
        givenModes("u1");
        assertApiError(catchThrowable(() -> service.createMode("u1", new TimetableModeRequest(" ", "🎓", TimetableColor.VIOLET))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertApiError(catchThrowable(() -> service.createMode("u1", new TimetableModeRequest("x".repeat(41), "🎓", TimetableColor.VIOLET))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertApiError(catchThrowable(() -> service.createMode("u1", new TimetableModeRequest("College", "", TimetableColor.VIOLET))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertApiError(catchThrowable(() -> service.createMode("u1", new TimetableModeRequest("College", "🎓", null))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        verify(modeRepository, never()).save(any());
    }

    @Test
    void atMostTwentyModes() {
        TimetableMode[] twenty = new TimetableMode[TimetableService.MAX_MODES];
        for (int i = 0; i < twenty.length; i++) twenty[i] = mode("m" + i, "u1", (i + 1) * 1024);
        givenModes("u1", twenty);

        assertApiError(catchThrowable(() -> service.createMode("u1", new TimetableModeRequest("One more", "🎓", TimetableColor.ROSE))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    @Test
    void anotherStudentsModeIsNotFound() {
        when(modeRepository.findById("m9")).thenReturn(Optional.of(mode("m9", "u2", 1024)));

        for (Runnable call : List.<Runnable>of(
                () -> service.getActivities("u1", "m9"),
                () -> service.activateMode("u1", "m9"),
                () -> service.deleteMode("u1", "m9"),
                () -> service.createActivity("u1", "m9", activityRequest(DayOfWeek.MONDAY, "Class", "08:00", "09:00")))) {
            assertApiError(catchThrowable(call::run), HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        verify(activityRepository, never()).save(any());
        verify(mongoTemplate, never()).upsert(any(Query.class), any(UpdateDefinition.class), eq(TimetablePreference.class));
    }

    // --- Activities and overlaps

    @Test
    void overlappingActivitiesAreRefusedAndNameTheClash() {
        givenModes("u1", mode("m1", "u1", 1024));
        givenDay("m1", DayOfWeek.MONDAY, activity("a1", "College", 8 * 60, 9 * 60));

        Throwable thrown = catchThrowable(() ->
                service.createActivity("u1", "m1", activityRequest(DayOfWeek.MONDAY, "Lab", "08:30", "09:30")));

        assertApiError(thrown, HttpStatus.CONFLICT, "ACTIVITY_OVERLAP");
        assertThat(thrown).hasMessage("Overlaps \"College\" (8:00 AM – 9:00 AM)");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void activitiesMayTouchEdges() {
        givenModes("u1", mode("m1", "u1", 1024));
        givenDay("m1", DayOfWeek.MONDAY, activity("a1", "College", 8 * 60, 9 * 60));
        savesActivities();

        TimetableActivityResponse saved = service.createActivity("u1", "m1",
                activityRequest(DayOfWeek.MONDAY, "Library", "09:00", "10:00"));

        assertThat(saved.startTime()).isEqualTo("09:00");
        assertThat(saved.endTime()).isEqualTo("10:00");
        assertThat(saved.startMinutes()).isEqualTo(540);
        assertThat(saved.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void theSameTimesAreFineOnAnotherDay() {
        givenModes("u1", mode("m1", "u1", 1024));
        givenDay("m1", DayOfWeek.MONDAY, activity("a1", "College", 8 * 60, 9 * 60));
        givenDay("m1", DayOfWeek.TUESDAY);
        savesActivities();

        assertThat(service.createActivity("u1", "m1", activityRequest(DayOfWeek.TUESDAY, "College", "08:00", "09:00")))
                .isNotNull();
    }

    @Test
    void editingAnActivityIgnoresItself() {
        TimetableActivity existing = activity("a1", "College", 8 * 60, 9 * 60);
        when(activityRepository.findById("a1")).thenReturn(Optional.of(existing));
        givenDay("m1", DayOfWeek.MONDAY, existing);
        savesActivities();

        TimetableActivityResponse saved = service.updateActivity("u1", "a1",
                activityRequest(DayOfWeek.MONDAY, "College classes", "08:00", "09:00"));

        assertThat(saved.title()).isEqualTo("College classes");
    }

    @Test
    void editingStillCollidesWithOtherActivities() {
        TimetableActivity first = activity("a1", "College", 8 * 60, 9 * 60);
        TimetableActivity second = activity("a2", "Gym", 10 * 60, 11 * 60);
        when(activityRepository.findById("a2")).thenReturn(Optional.of(second));
        givenDay("m1", DayOfWeek.MONDAY, first, second);

        Throwable thrown = catchThrowable(() ->
                service.updateActivity("u1", "a2", activityRequest(DayOfWeek.MONDAY, "Gym", "08:30", "09:30")));

        assertApiError(thrown, HttpStatus.CONFLICT, "ACTIVITY_OVERLAP");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void activityTimesAreChecked() {
        givenModes("u1", mode("m1", "u1", 1024));
        givenDay("m1", DayOfWeek.MONDAY);

        assertApiError(catchThrowable(() -> service.createActivity("u1", "m1",
                activityRequest(DayOfWeek.MONDAY, "Class", "25:00", "26:00"))), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertApiError(catchThrowable(() -> service.createActivity("u1", "m1",
                activityRequest(DayOfWeek.MONDAY, "Class", "09:00", "09:00"))), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertThat(catchThrowable(() -> service.createActivity("u1", "m1",
                activityRequest(DayOfWeek.MONDAY, "Sleep", "22:00", "06:00"))))
                .hasMessageContaining("can't run past midnight");
        assertApiError(catchThrowable(() -> service.createActivity("u1", "m1",
                activityRequest(null, "Class", "09:00", "10:00"))), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertApiError(catchThrowable(() -> service.createActivity("u1", "m1",
                activityRequest(DayOfWeek.MONDAY, "x".repeat(101), "09:00", "10:00"))), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void atMostFortyActivitiesInADay() {
        givenModes("u1", mode("m1", "u1", 1024));
        TimetableActivity[] full = new TimetableActivity[TimetableService.MAX_ACTIVITIES_PER_DAY];
        for (int i = 0; i < full.length; i++) full[i] = activity("a" + i, "Slot " + i, i, i + 1);
        givenDay("m1", DayOfWeek.MONDAY, full);

        assertApiError(catchThrowable(() -> service.createActivity("u1", "m1",
                activityRequest(DayOfWeek.MONDAY, "One more", "23:00", "23:30"))), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    @Test
    void anotherStudentsActivityIsNotFound() {
        TimetableActivity someoneElses = activity("a9", "Class", 8 * 60, 9 * 60);
        someoneElses.setUserId("u2");
        when(activityRepository.findById("a9")).thenReturn(Optional.of(someoneElses));

        assertApiError(catchThrowable(() -> service.deleteActivity("u1", "a9")), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertApiError(catchThrowable(() -> service.updateActivity("u1", "a9",
                activityRequest(DayOfWeek.MONDAY, "Class", "08:00", "09:00"))), HttpStatus.NOT_FOUND, "NOT_FOUND");
        verify(activityRepository, never()).delete(any());
    }

    // --- Copying a day

    @Test
    @SuppressWarnings("unchecked")
    void copyingADayReplacesTheTargetDays() {
        givenModes("u1", mode("m1", "u1", 1024));
        givenDay("m1", DayOfWeek.MONDAY, activity("a1", "College", 8 * 60, 9 * 60), activity("a2", "Gym", 18 * 60, 19 * 60));
        when(activityRepository.findByModeId("m1")).thenReturn(List.of());

        service.copyDay("u1", "m1", new CopyDayRequest(DayOfWeek.MONDAY, List.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)));

        verify(activityRepository).deleteByModeIdAndDayOfWeek("m1", DayOfWeek.TUESDAY);
        verify(activityRepository).deleteByModeIdAndDayOfWeek("m1", DayOfWeek.WEDNESDAY);
        ArgumentCaptor<List<TimetableActivity>> copies = ArgumentCaptor.forClass(List.class);
        verify(activityRepository, org.mockito.Mockito.times(2)).saveAll(copies.capture());
        assertThat(copies.getAllValues().get(0))
                .extracting(TimetableActivity::getDayOfWeek, TimetableActivity::getTitle, TimetableActivity::getStartMinutes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DayOfWeek.TUESDAY, "College", 480),
                        org.assertj.core.groups.Tuple.tuple(DayOfWeek.TUESDAY, "Gym", 1080));
        assertThat(copies.getAllValues().get(0)).allSatisfy(copy -> assertThat(copy.getId()).isNull());
    }

    @Test
    void copyingNeedsRealTargetDays() {
        givenModes("u1", mode("m1", "u1", 1024));

        assertApiError(catchThrowable(() -> service.copyDay("u1", "m1", new CopyDayRequest(DayOfWeek.MONDAY, List.of()))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertApiError(catchThrowable(() -> service.copyDay("u1", "m1",
                new CopyDayRequest(DayOfWeek.MONDAY, List.of(DayOfWeek.TUESDAY, DayOfWeek.MONDAY)))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        verify(activityRepository, never()).saveAll(any());
    }

    @Test
    void theWeekIsSortedByDayThenStartTime() {
        givenModes("u1", mode("m1", "u1", 1024));
        TimetableActivity mondayLate = activity("a1", "Gym", 18 * 60, 19 * 60);
        TimetableActivity mondayEarly = activity("a2", "College", 8 * 60, 9 * 60);
        TimetableActivity sunday = activity("a3", "Revision", 10 * 60, 11 * 60);
        sunday.setDayOfWeek(DayOfWeek.SUNDAY);
        when(activityRepository.findByModeId("m1")).thenReturn(List.of(sunday, mondayLate, mondayEarly));

        assertThat(service.getActivities("u1", "m1")).extracting(TimetableActivityResponse::title)
                .containsExactly("College", "Gym", "Revision");
    }
}
