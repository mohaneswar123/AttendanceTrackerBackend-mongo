package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.CalendarEventRepository;
import com.AttendanceRegister.sdc.dto.CalendarEventRequest;
import com.AttendanceRegister.sdc.dto.CalendarEventResponse;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.CalendarEvent;
import com.AttendanceRegister.sdc.util.Dates;

// Students' calendar entries. Dates and times are read and shown in one configured time
// zone (app.calendar.time-zone), never the server's own.
@Service
public class CalendarEventService {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_SEARCH_RESULTS = 100;
    private static final DateTimeFormatter HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm");
    // By date, then all-day entries before timed ones, then by start time
    private static final Sort CALENDAR_ORDER = Sort.by(
            Sort.Order.asc("eventDate"), Sort.Order.desc("allDay"), Sort.Order.asc("startAt"));

    private final CalendarEventRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final ZoneId zone;

    public CalendarEventService(CalendarEventRepository repository,
                                MongoTemplate mongoTemplate,
                                Clock clock,
                                @Value("${app.calendar.time-zone:Asia/Kolkata}") String timeZone) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.zone = zoneFromSetting(timeZone);
    }

    // A wrong setting stops the app at startup instead of storing entries at the wrong times
    static ZoneId zoneFromSetting(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException | NullPointerException ex) {
            throw new IllegalStateException(
                    "app.calendar.time-zone must be a time zone id such as Asia/Kolkata, not '" + timeZone + "'", ex);
        }
    }

    // Entries dated from..to (inclusive, either end optional) whose title contains q (optional)
    public List<CalendarEventResponse> getEvents(String userId, String from, String to, String q) {
        Criteria criteria = Criteria.where("userId").is(userId);
        String fromDate = isBlank(from) ? null : Dates.parseIsoDate(from, "from").toString();
        String toDate = isBlank(to) ? null : Dates.parseIsoDate(to, "to").toString();
        if (fromDate != null || toDate != null) {
            Criteria dates = criteria.and("eventDate");
            if (fromDate != null) dates.gte(fromDate);
            if (toDate != null) dates.lte(toDate);
        }
        boolean searching = !isBlank(q);
        if (searching) {
            // Pattern.quote so characters like "+" or "(" in the search are matched literally
            criteria.and("title").regex(Pattern.quote(q.trim()), "i");
        }

        Query query = Query.query(criteria).with(CALENDAR_ORDER);
        if (searching) {
            query.limit(MAX_SEARCH_RESULTS);
        }
        return mongoTemplate.find(query, CalendarEvent.class).stream().map(this::toResponse).toList();
    }

    public CalendarEventResponse getEvent(String userId, String eventId) {
        return toResponse(getOwnedEvent(userId, eventId));
    }

    public CalendarEventResponse createEvent(String userId, CalendarEventRequest request) {
        CalendarEvent event = new CalendarEvent();
        event.setUserId(userId);
        applyRequest(event, request);
        Instant now = clock.instant();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return toResponse(repository.save(event));
    }

    public CalendarEventResponse updateEvent(String userId, String eventId, CalendarEventRequest request) {
        CalendarEvent event = getOwnedEvent(userId, eventId);
        applyRequest(event, request);
        event.setUpdatedAt(clock.instant());
        return toResponse(repository.save(event));
    }

    public void deleteEvent(String userId, String eventId) {
        repository.delete(getOwnedEvent(userId, eventId));
    }

    // Validates the request and sets the title, type, date and times together, so
    // eventDate and startAt/endAt always describe the same day in the calendar zone
    private void applyRequest(CalendarEvent event, CalendarEventRequest request) {
        String title = validTitle(request.title());
        if (request.type() == null) {
            throw ApiException.badRequest("Choose Event or Reminder");
        }
        LocalDate date = Dates.parseIsoDate(request.date(), "Date");
        boolean allDay = Boolean.TRUE.equals(request.allDay());

        Instant startAt = null;
        Instant endAt = null;
        if (!allDay) {
            LocalTime start = parseTime(request.startTime(), "Start time");
            LocalTime end = parseTime(request.endTime(), "End time");
            if (start == null) {
                throw ApiException.badRequest(end == null
                        ? "Pick a start time, or make it an all-day entry"
                        : "Pick a start time first");
            }
            if (end != null && !end.isAfter(start)) {
                throw ApiException.badRequest("End time must be later the same day. Events can't run past midnight.");
            }
            startAt = ZonedDateTime.of(date, start, zone).toInstant();
            endAt = end == null ? null : ZonedDateTime.of(date, end, zone).toInstant();
        }

        event.setTitle(title);
        event.setType(request.type());
        event.setEventDate(date.toString());
        event.setAllDay(allDay);
        event.setStartAt(startAt);
        event.setEndAt(endAt);
    }

    CalendarEventResponse toResponse(CalendarEvent event) {
        return new CalendarEventResponse(
                event.getId(),
                event.getTitle(),
                event.getType(),
                event.getEventDate(),
                event.isAllDay(),
                localTime(event.getStartAt()),
                localTime(event.getEndAt()),
                event.getStartAt(),
                event.getEndAt(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }

    private String localTime(Instant instant) {
        return instant == null ? null : instant.atZone(zone).toLocalTime().format(HOURS_MINUTES);
    }

    // Other students' entries look the same as entries that don't exist
    private CalendarEvent getOwnedEvent(String userId, String eventId) {
        return repository.findById(eventId)
                .filter(event -> userId.equals(event.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Calendar entry not found"));
    }

    // "HH:mm" (seconds, if sent, are dropped); null when left empty
    private static LocalTime parseTime(String value, String label) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim()).truncatedTo(ChronoUnit.MINUTES);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(label + " must be a time such as 09:30");
        }
    }

    private static String validTitle(String title) {
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("Title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw ApiException.badRequest("Title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
