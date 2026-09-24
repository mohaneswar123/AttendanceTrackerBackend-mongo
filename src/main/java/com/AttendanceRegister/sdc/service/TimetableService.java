package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.TimetableActivityRepository;
import com.AttendanceRegister.sdc.Repository.TimetableModeRepository;
import com.AttendanceRegister.sdc.Repository.TimetablePreferenceRepository;
import com.AttendanceRegister.sdc.dto.CopyDayRequest;
import com.AttendanceRegister.sdc.dto.TimetableActivityRequest;
import com.AttendanceRegister.sdc.dto.TimetableActivityResponse;
import com.AttendanceRegister.sdc.dto.TimetableModeRequest;
import com.AttendanceRegister.sdc.dto.TimetableModeResponse;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.TimetableActivity;
import com.AttendanceRegister.sdc.model.TimetableMode;
import com.AttendanceRegister.sdc.model.TimetablePreference;
import com.AttendanceRegister.sdc.util.Times;

// Reusable weekly routines grouped into modes. Two activities may never overlap within
// the same mode and day; the server decides that, whatever the page does first.
@Service
public class TimetableService {

    static final int MAX_MODES = 20;
    static final int MAX_ACTIVITIES_PER_DAY = 40;
    static final int MAX_NAME_LENGTH = 40;
    static final int MAX_ICON_LENGTH = 4;
    static final int MAX_TITLE_LENGTH = 100;
    private static final double POSITION_STEP = 1024;

    private final TimetableModeRepository modeRepository;
    private final TimetableActivityRepository activityRepository;
    private final TimetablePreferenceRepository preferenceRepository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public TimetableService(TimetableModeRepository modeRepository,
                            TimetableActivityRepository activityRepository,
                            TimetablePreferenceRepository preferenceRepository,
                            MongoTemplate mongoTemplate,
                            Clock clock) {
        this.modeRepository = modeRepository;
        this.activityRepository = activityRepository;
        this.preferenceRepository = preferenceRepository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    // --- Modes

    public List<TimetableModeResponse> getModes(String userId) {
        List<TimetableMode> modes = modeRepository.findByUserIdOrderByPositionAsc(userId);
        String activeId = activeModeId(userId, modes);
        return modes.stream().map(mode -> toResponse(mode, activeId)).toList();
    }

    public TimetableModeResponse createMode(String userId, TimetableModeRequest request) {
        List<TimetableMode> existing = modeRepository.findByUserIdOrderByPositionAsc(userId);
        if (existing.size() >= MAX_MODES) {
            throw ApiException.badRequest("You can have at most " + MAX_MODES + " modes");
        }

        Instant now = clock.instant();
        TimetableMode mode = new TimetableMode();
        mode.setUserId(userId);
        applyRequest(mode, request);
        mode.setPosition(existing.isEmpty() ? POSITION_STEP : existing.get(existing.size() - 1).getPosition() + POSITION_STEP);
        mode.setCreatedAt(now);
        mode.setUpdatedAt(now);
        TimetableMode saved = modeRepository.save(mode);

        // The student's first mode becomes the active one
        if (existing.isEmpty()) {
            setActiveMode(userId, saved.getId());
            return toResponse(saved, saved.getId());
        }
        return toResponse(saved, activeModeId(userId, null));
    }

    public TimetableModeResponse updateMode(String userId, String modeId, TimetableModeRequest request) {
        TimetableMode mode = getOwnedMode(userId, modeId);
        applyRequest(mode, request);
        mode.setUpdatedAt(clock.instant());
        TimetableMode saved = modeRepository.save(mode);
        return toResponse(saved, activeModeId(userId, null));
    }

    // One update of one field, so simultaneous activations simply leave the last writer's
    // choice; there is never a moment with two active modes
    public List<TimetableModeResponse> activateMode(String userId, String modeId) {
        getOwnedMode(userId, modeId);
        setActiveMode(userId, modeId);
        return getModes(userId);
    }

    public List<TimetableModeResponse> deleteMode(String userId, String modeId) {
        TimetableMode mode = getOwnedMode(userId, modeId);
        activityRepository.deleteByModeId(mode.getId());
        modeRepository.delete(mode);

        // Hand the active flag to the first remaining mode, or to nothing at all
        if (modeId.equals(rawActiveModeId(userId))) {
            List<TimetableMode> remaining = modeRepository.findByUserIdOrderByPositionAsc(userId);
            setActiveMode(userId, remaining.isEmpty() ? null : remaining.get(0).getId());
        }
        return getModes(userId);
    }

    // --- Activities

    public List<TimetableActivityResponse> getActivities(String userId, String modeId) {
        getOwnedMode(userId, modeId);
        return activityRepository.findByModeId(modeId).stream()
                .sorted(Comparator.comparing(TimetableActivity::getDayOfWeek)
                        .thenComparing(TimetableActivity::getStartMinutes))
                .map(TimetableService::toResponse)
                .toList();
    }

    public TimetableActivityResponse createActivity(String userId, String modeId, TimetableActivityRequest request) {
        getOwnedMode(userId, modeId);
        DayOfWeek day = requireDay(request.dayOfWeek());
        int[] times = validTimes(request);

        List<TimetableActivity> sameDay = activityRepository.findByModeIdAndDayOfWeekOrderByStartMinutesAsc(modeId, day);
        if (sameDay.size() >= MAX_ACTIVITIES_PER_DAY) {
            throw ApiException.badRequest("You can have at most " + MAX_ACTIVITIES_PER_DAY + " activities in a day");
        }
        requireNoOverlap(sameDay, times[0], times[1], null);

        Instant now = clock.instant();
        TimetableActivity activity = new TimetableActivity();
        activity.setUserId(userId);
        activity.setModeId(modeId);
        activity.setCreatedAt(now);
        apply(activity, day, request, times, now);
        return toResponse(activityRepository.save(activity));
    }

    public TimetableActivityResponse updateActivity(String userId, String activityId, TimetableActivityRequest request) {
        TimetableActivity activity = getOwnedActivity(userId, activityId);
        DayOfWeek day = requireDay(request.dayOfWeek());
        int[] times = validTimes(request);

        List<TimetableActivity> sameDay =
                activityRepository.findByModeIdAndDayOfWeekOrderByStartMinutesAsc(activity.getModeId(), day);
        // The activity being edited can't clash with itself
        requireNoOverlap(sameDay, times[0], times[1], activity.getId());

        apply(activity, day, request, times, clock.instant());
        return toResponse(activityRepository.save(activity));
    }

    public void deleteActivity(String userId, String activityId) {
        activityRepository.delete(getOwnedActivity(userId, activityId));
    }

    // Replaces each target day with a copy of the source day
    public List<TimetableActivityResponse> copyDay(String userId, String modeId, CopyDayRequest request) {
        getOwnedMode(userId, modeId);
        DayOfWeek fromDay = requireDay(request.fromDay());
        if (request.toDays() == null || request.toDays().isEmpty()) {
            throw ApiException.badRequest("Choose at least one day to copy to");
        }
        List<DayOfWeek> toDays = new ArrayList<>(new LinkedHashSet<>(request.toDays()));
        if (toDays.contains(null)) {
            throw ApiException.badRequest("Choose a day of the week");
        }
        if (toDays.contains(fromDay)) {
            throw ApiException.badRequest("A day can't be copied onto itself");
        }

        List<TimetableActivity> source = activityRepository.findByModeIdAndDayOfWeekOrderByStartMinutesAsc(modeId, fromDay);
        Instant now = clock.instant();
        for (DayOfWeek day : toDays) {
            activityRepository.deleteByModeIdAndDayOfWeek(modeId, day);
            List<TimetableActivity> copies = source.stream().map(original -> {
                TimetableActivity copy = new TimetableActivity();
                copy.setUserId(userId);
                copy.setModeId(modeId);
                copy.setDayOfWeek(day);
                copy.setTitle(original.getTitle());
                copy.setCategory(original.getCategory());
                copy.setStartMinutes(original.getStartMinutes());
                copy.setEndMinutes(original.getEndMinutes());
                copy.setCreatedAt(now);
                copy.setUpdatedAt(now);
                return copy;
            }).toList();
            activityRepository.saveAll(copies);
        }
        return getActivities(userId, modeId);
    }

    // --- The active mode

    private String rawActiveModeId(String userId) {
        return preferenceRepository.findByUserId(userId)
                .map(TimetablePreference::getActiveModeId)
                .orElse(null);
    }

    // The stored id, or the first mode when it is missing or points at a deleted mode
    private String activeModeId(String userId, List<TimetableMode> knownModes) {
        String stored = rawActiveModeId(userId);
        List<TimetableMode> modes = knownModes != null ? knownModes : modeRepository.findByUserIdOrderByPositionAsc(userId);
        if (stored != null && modes.stream().anyMatch(mode -> mode.getId().equals(stored))) {
            return stored;
        }
        return modes.isEmpty() ? null : modes.get(0).getId();
    }

    // A single upsert of a single field; the unique index on userId keeps it to one document
    private void setActiveMode(String userId, String modeId) {
        mongoTemplate.upsert(
                Query.query(Criteria.where("userId").is(userId)),
                new Update().set("activeModeId", modeId).set("updatedAt", clock.instant()),
                TimetablePreference.class);
    }

    // --- Validation and mapping

    private void applyRequest(TimetableMode mode, TimetableModeRequest request) {
        mode.setName(requireText(request.name(), "Mode name", MAX_NAME_LENGTH));
        mode.setIcon(requireText(request.icon(), "Icon", MAX_ICON_LENGTH));
        if (request.color() == null) {
            throw ApiException.badRequest("Choose a colour");
        }
        mode.setColor(request.color());
    }

    private static void apply(TimetableActivity activity, DayOfWeek day, TimetableActivityRequest request,
                              int[] times, Instant now) {
        activity.setDayOfWeek(day);
        activity.setTitle(requireText(request.title(), "Activity name", MAX_TITLE_LENGTH));
        activity.setCategory(request.category());
        activity.setStartMinutes(times[0]);
        activity.setEndMinutes(times[1]);
        activity.setUpdatedAt(now);
    }

    // { start, end } in minutes, checked to be a real span inside one day
    private static int[] validTimes(TimetableActivityRequest request) {
        int start = Times.parseMinutes(request.startTime(), "Start time");
        int end = Times.parseMinutes(request.endTime(), "End time");
        if (end <= start) {
            throw ApiException.badRequest("End time must be later the same day. Activities can't run past midnight.");
        }
        return new int[] { start, end };
    }

    private static void requireNoOverlap(List<TimetableActivity> sameDay, int start, int end, String ignoreId) {
        Optional<TimetableActivity> clash = sameDay.stream()
                .filter(other -> !other.getId().equals(ignoreId))
                // Touching edges are fine: 8:00-9:00 and 9:00-10:00 both fit
                .filter(other -> other.getStartMinutes() < end && other.getEndMinutes() > start)
                .findFirst();
        if (clash.isPresent()) {
            TimetableActivity other = clash.get();
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVITY_OVERLAP",
                    "Overlaps \"" + other.getTitle() + "\" ("
                            + Times.format12Hour(other.getStartMinutes()) + " – "
                            + Times.format12Hour(other.getEndMinutes()) + ")");
        }
    }

    private static DayOfWeek requireDay(DayOfWeek day) {
        if (day == null) {
            throw ApiException.badRequest("Choose a day of the week");
        }
        return day;
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(label + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ApiException.badRequest(label + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    // Other students' modes and activities look the same as ones that don't exist
    private TimetableMode getOwnedMode(String userId, String modeId) {
        return modeRepository.findById(modeId)
                .filter(mode -> userId.equals(mode.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Timetable mode not found"));
    }

    private TimetableActivity getOwnedActivity(String userId, String activityId) {
        return activityRepository.findById(activityId)
                .filter(activity -> userId.equals(activity.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Activity not found"));
    }

    private TimetableModeResponse toResponse(TimetableMode mode, String activeModeId) {
        return new TimetableModeResponse(
                mode.getId(),
                mode.getName(),
                mode.getIcon(),
                mode.getColor(),
                mode.getId().equals(activeModeId),
                activityRepository.countByModeId(mode.getId()),
                mode.getCreatedAt(),
                mode.getUpdatedAt());
    }

    private static TimetableActivityResponse toResponse(TimetableActivity activity) {
        return new TimetableActivityResponse(
                activity.getId(),
                activity.getModeId(),
                activity.getDayOfWeek(),
                activity.getTitle(),
                activity.getCategory(),
                Times.format(activity.getStartMinutes()),
                Times.format(activity.getEndMinutes()),
                activity.getStartMinutes(),
                activity.getEndMinutes(),
                activity.getCreatedAt(),
                activity.getUpdatedAt());
    }
}
