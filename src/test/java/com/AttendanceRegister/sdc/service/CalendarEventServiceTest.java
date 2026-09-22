package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.CalendarEventRepository;
import com.AttendanceRegister.sdc.dto.CalendarEventRequest;
import com.AttendanceRegister.sdc.dto.CalendarEventResponse;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.CalendarEvent;
import com.AttendanceRegister.sdc.model.CalendarEventType;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Mock
    private CalendarEventRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    private CalendarEventService service;

    @BeforeEach
    void setUp() {
        service = new CalendarEventService(repository, mongoTemplate, Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata");
    }

    private static CalendarEventRequest timed(String date, String start, String end) {
        return new CalendarEventRequest("DSA exam", CalendarEventType.EVENT, date, false, start, end);
    }

    // Creates the entry and returns what was saved
    private CalendarEvent create(CalendarEventRequest request) {
        ArgumentCaptor<CalendarEvent> saved = ArgumentCaptor.forClass(CalendarEvent.class);
        when(repository.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));
        service.createEvent("u1", request);
        return saved.getValue();
    }

    private static CalendarEvent stored(String id, String userId) {
        CalendarEvent event = new CalendarEvent();
        event.setId(id);
        event.setUserId(userId);
        event.setTitle("Lab");
        event.setType(CalendarEventType.EVENT);
        event.setEventDate("2026-09-20");
        event.setAllDay(true);
        event.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        return event;
    }

    private static void assertBadRequest(Throwable thrown, String messagePart) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains(messagePart);
        });
    }

    private Query listQuery(String from, String to, String q) {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(query.capture(), eq(CalendarEvent.class))).thenReturn(List.of());
        service.getEvents("u1", from, to, q);
        return query.getValue();
    }

    // --- Saving dates and times

    @Test
    void timedEntryIsStoredAsTheInstantOfThatIndiaTime() {
        CalendarEvent saved = create(timed("2026-09-25", "10:00", "11:30"));

        assertThat(saved.getStartAt()).isEqualTo(Instant.parse("2026-09-25T04:30:00Z"));
        assertThat(saved.getEndAt()).isEqualTo(Instant.parse("2026-09-25T06:00:00Z"));
        assertThat(saved.getEventDate()).isEqualTo("2026-09-25");
        assertThat(saved.isAllDay()).isFalse();
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);

        CalendarEventResponse response = service.toResponse(saved);
        assertThat(response.date()).isEqualTo("2026-09-25");
        assertThat(response.startTime()).isEqualTo("10:00");
        assertThat(response.endTime()).isEqualTo("11:30");
    }

    @Test
    void startTimeAlwaysFallsOnTheEntrysDate() {
        // 00:15 in India is still the previous day in UTC, but the entry stays on the 25th
        CalendarEvent justAfterMidnight = create(timed("2026-09-25", "00:15", null));
        assertThat(justAfterMidnight.getStartAt()).isEqualTo(Instant.parse("2026-09-24T18:45:00Z"));
        assertThat(justAfterMidnight.getEventDate()).isEqualTo("2026-09-25");
        assertThat(justAfterMidnight.getStartAt().atZone(INDIA).toLocalDate()).hasToString("2026-09-25");
        assertThat(service.toResponse(justAfterMidnight).startTime()).isEqualTo("00:15");

        CalendarEvent lastMinute = create(timed("2026-09-25", "23:59", null));
        assertThat(lastMinute.getStartAt().atZone(INDIA).toLocalDate()).hasToString("2026-09-25");
        assertThat(lastMinute.getEndAt()).isNull();
    }

    @Test
    void allDayEntriesIgnoreAnyTimes() {
        CalendarEvent saved = create(new CalendarEventRequest(
                "Holiday", CalendarEventType.REMINDER, "2026-10-02", true, "10:00", "09:00"));

        assertThat(saved.isAllDay()).isTrue();
        assertThat(saved.getStartAt()).isNull();
        assertThat(saved.getEndAt()).isNull();
        assertThat(saved.getEventDate()).isEqualTo("2026-10-02");
        assertThat(saved.getType()).isEqualTo(CalendarEventType.REMINDER);
        assertThat(service.toResponse(saved).startTime()).isNull();
    }

    @Test
    void titleIsTrimmedAndLimited() {
        CalendarEvent saved = create(new CalendarEventRequest("  Viva  ", CalendarEventType.EVENT, "2026-09-25", true, null, null));
        assertThat(saved.getTitle()).isEqualTo("Viva");

        assertBadRequest(catchThrowable(() -> service.createEvent("u1",
                new CalendarEventRequest(" ", CalendarEventType.EVENT, "2026-09-25", true, null, null))), "Title is required");
        assertBadRequest(catchThrowable(() -> service.createEvent("u1",
                new CalendarEventRequest("x".repeat(201), CalendarEventType.EVENT, "2026-09-25", true, null, null))), "at most 200");
    }

    @Test
    void invalidEntriesAreRejected() {
        assertBadRequest(catchThrowable(() -> service.createEvent("u1",
                new CalendarEventRequest("Exam", null, "2026-09-25", true, null, null))), "Event or Reminder");
        assertBadRequest(catchThrowable(() -> service.createEvent("u1", timed("2026-02-30", "10:00", null))), "real date");
        assertBadRequest(catchThrowable(() -> service.createEvent("u1", timed("2026-09-25", "25:00", null))), "Start time");
        assertBadRequest(catchThrowable(() -> service.createEvent("u1", timed("2026-09-25", "10:00", "7pm"))), "End time");
        assertBadRequest(catchThrowable(() -> service.createEvent("u1", timed("2026-09-25", null, null))), "Pick a start time");
        verify(repository, never()).save(any());
    }

    @Test
    void endTimeNeedsAStartTimeAndMustBeLaterTheSameDay() {
        assertBadRequest(catchThrowable(() -> service.createEvent("u1", timed("2026-09-25", null, "11:00"))),
                "Pick a start time first");
        assertBadRequest(catchThrowable(() -> service.createEvent("u1", timed("2026-09-25", "10:00", "10:00"))),
                "can't run past midnight");
        assertBadRequest(catchThrowable(() -> service.createEvent("u1", timed("2026-09-25", "23:00", "01:00"))),
                "can't run past midnight");
        verify(repository, never()).save(any());
    }

    @Test
    void zoneSettingMustBeARealZone() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        assertThatThrownBy(() -> new CalendarEventService(repository, mongoTemplate, clock, "Mars/Olympus"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.calendar.time-zone");
    }

    // --- Ownership, update and delete

    @Test
    void updateRecomputesDateAndTimesTogether() {
        CalendarEvent existing = stored("e1", "u1");
        when(repository.findById("e1")).thenReturn(Optional.of(existing));
        when(repository.save(any(CalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CalendarEventResponse updated = service.updateEvent("u1", "e1", timed("2026-09-26", "14:00", "15:00"));

        assertThat(updated.date()).isEqualTo("2026-09-26");
        assertThat(updated.allDay()).isFalse();
        assertThat(updated.startAt()).isEqualTo(Instant.parse("2026-09-26T08:30:00Z"));
        assertThat(updated.createdAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(updated.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void anotherStudentsEntryIsNotFound() {
        when(repository.findById("e9")).thenReturn(Optional.of(stored("e9", "u2")));
        when(repository.findById("missing")).thenReturn(Optional.empty());

        for (Runnable call : List.<Runnable>of(
                () -> service.getEvent("u1", "e9"),
                () -> service.updateEvent("u1", "e9", timed("2026-09-26", "14:00", null)),
                () -> service.deleteEvent("u1", "e9"),
                () -> service.getEvent("u1", "missing"))) {
            assertThat(catchThrowable(call::run)).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRemovesOnlyThatEntry() {
        CalendarEvent event = stored("e1", "u1");
        when(repository.findById("e1")).thenReturn(Optional.of(event));

        service.deleteEvent("u1", "e1");

        verify(repository).delete(event);
        verify(repository, never()).deleteByUserId(anyString());
        verify(repository, never()).deleteAll();
    }

    // --- Listing and search

    @Test
    void listWithBothDatesIsInclusiveAndSorted() {
        Query query = listQuery("2026-09-01", "2026-09-30", null);

        Document filter = query.getQueryObject();
        assertThat(filter.get("userId")).isEqualTo("u1");
        Document dates = (Document) filter.get("eventDate");
        assertThat(dates.get("$gte")).isEqualTo("2026-09-01");
        assertThat(dates.get("$lte")).isEqualTo("2026-09-30");
        assertThat(query.getSortObject()).isEqualTo(new Document("eventDate", 1).append("allDay", -1).append("startAt", 1));
        assertThat(query.getLimit()).isZero();
    }

    @Test
    void listWithOneOrNoDates() {
        Document fromOnly = (Document) listQuery("2026-09-22", null, null).getQueryObject().get("eventDate");
        assertThat(fromOnly).containsEntry("$gte", "2026-09-22").doesNotContainKey("$lte");

        Document toOnly = (Document) listQuery(null, "2026-09-22", null).getQueryObject().get("eventDate");
        assertThat(toOnly).containsEntry("$lte", "2026-09-22").doesNotContainKey("$gte");

        assertThat(listQuery(null, null, null).getQueryObject()).isEqualTo(new Document("userId", "u1"));
    }

    @Test
    void searchMatchesTitleLiterallyIgnoringCaseAndIsCapped() {
        Query query = listQuery(null, null, "  A+B (x) ");

        Object title = query.getQueryObject().get("title");
        String pattern;
        boolean ignoresCase;
        if (title instanceof Pattern regex) {
            pattern = regex.pattern();
            ignoresCase = (regex.flags() & Pattern.CASE_INSENSITIVE) != 0;
        } else if (title instanceof BsonRegularExpression regex) {
            pattern = regex.getPattern();
            ignoresCase = regex.getOptions().contains("i");
        } else {
            fail("title should be a regular expression but was " + title);
            return;
        }
        assertThat(ignoresCase).isTrue();
        Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        assertThat(compiled.matcher("Revise a+b (X) notes").find()).isTrue();
        assertThat(compiled.matcher("aab x").find()).isFalse();
        assertThat(query.getLimit()).isEqualTo(CalendarEventService.MAX_SEARCH_RESULTS);
    }

    @Test
    void listRejectsImpossibleDates() {
        assertBadRequest(catchThrowable(() -> service.getEvents("u1", "2026-13-01", null, null)), "real date");
        verify(mongoTemplate, never()).find(any(Query.class), eq(CalendarEvent.class));
    }
}
