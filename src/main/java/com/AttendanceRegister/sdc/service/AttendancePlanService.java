package com.AttendanceRegister.sdc.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.AttendancePlanRepository;
import com.AttendanceRegister.sdc.Repository.SubjectRepository;
import com.AttendanceRegister.sdc.dto.PlanDayRequest;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.AttendancePlan;
import com.AttendanceRegister.sdc.model.AttendancePlan.PlannedClass;
import com.AttendanceRegister.sdc.model.Subject;

/**
 * The weekly class plan behind the Record checklist.
 *
 * A day is always written whole: the request's list replaces whatever the day held, so
 * editing is one idempotent operation rather than a dance of add/remove calls that can be
 * left half-done. Reads tidy out subjects that no longer exist rather than erroring,
 * because a plan that outlives one of its subjects is a fact of life, not a fault.
 */
@Service
public class AttendancePlanService {

    /** Matches the 1/2/3 hr choice on the record form; reports weight hours the same way */
    static final int MIN_HOURS = 1;
    static final int MAX_HOURS = 3;

    /** More classes than this in one day is a typo, not a timetable */
    static final int MAX_CLASSES_PER_DAY = 12;

    private final AttendancePlanRepository planRepository;
    private final SubjectRepository subjectRepository;

    public AttendancePlanService(AttendancePlanRepository planRepository, SubjectRepository subjectRepository) {
        this.planRepository = planRepository;
        this.subjectRepository = subjectRepository;
    }

    /**
     * The whole week, keyed "MONDAY".."SUNDAY". Days with no classes are absent from the
     * map. Entries whose subject has since been deleted are filtered out of the answer,
     * so the page never has to render a class with no name.
     */
    public Map<String, List<PlannedClass>> getPlan(String userId) {
        AttendancePlan plan = planRepository.findByUserId(userId).orElse(null);
        if (plan == null) {
            return Map.of();
        }
        Set<String> subjectIds = subjectRepository.findByUserId(userId).stream()
                .map(Subject::getId)
                .collect(Collectors.toSet());
        return keepExisting(plan.getDays(), subjectIds);
    }

    /** The plan with any deleted subject's classes left out, day-emptying included */
    private static Map<String, List<PlannedClass>> keepExisting(
            Map<String, List<PlannedClass>> days, Set<String> subjectIds) {
        Map<String, List<PlannedClass>> kept = new HashMap<>();
        days.forEach((day, classes) -> {
            List<PlannedClass> remaining = classes.stream()
                    .filter(planned -> subjectIds.contains(planned.getSubjectId()))
                    .toList();
            if (!remaining.isEmpty()) {
                kept.put(day, remaining);
            }
        });
        return kept;
    }

    /** Replaces one weekday of the plan; an empty or null list clears the day. */
    public Map<String, List<PlannedClass>> setDay(String userId, String weekday, PlanDayRequest request) {
        String day = parseWeekday(weekday);
        List<PlanDayRequest.Entry> entries = request == null || request.classes() == null
                ? List.of()
                : request.classes();

        if (entries.size() > MAX_CLASSES_PER_DAY) {
            throw ApiException.badRequest("A day can hold at most " + MAX_CLASSES_PER_DAY + " classes");
        }

        // Every entry must name a subject this student actually owns
        Set<String> owned = subjectRepository.findByUserId(userId).stream()
                .map(Subject::getId)
                .collect(Collectors.toSet());

        List<PlannedClass> classes = new ArrayList<>();
        for (PlanDayRequest.Entry entry : entries) {
            if (entry.subjectId() == null || !owned.contains(entry.subjectId())) {
                throw ApiException.notFound("Subject not found");
            }
            int hours = entry.hours() == null ? 1 : entry.hours();
            if (hours < MIN_HOURS || hours > MAX_HOURS) {
                throw ApiException.badRequest(
                        "Hours must be between " + MIN_HOURS + " and " + MAX_HOURS);
            }
            classes.add(new PlannedClass(entry.subjectId(), hours));
        }

        AttendancePlan plan = planRepository.findByUserId(userId)
                .orElseGet(() -> new AttendancePlan(userId));
        if (classes.isEmpty()) {
            plan.getDays().remove(day);
        } else {
            plan.getDays().put(day, classes);
        }
        planRepository.save(plan);
        // Answer from the document in hand rather than re-reading what was just written
        return keepExisting(plan.getDays(), owned);
    }

    /** Called when a subject is deleted, so the plan does not keep classes for a ghost */
    public void removeSubjectFromPlan(String userId, String subjectId) {
        planRepository.findByUserId(userId).ifPresent(plan -> {
            boolean changed = false;
            for (List<PlannedClass> classes : new ArrayList<>(plan.getDays().values())) {
                changed |= classes.removeIf(planned -> subjectId.equals(planned.getSubjectId()));
            }
            if (changed) {
                plan.getDays().values().removeIf(List::isEmpty);
                planRepository.save(plan);
            }
        });
    }

    private static String parseWeekday(String weekday) {
        try {
            return DayOfWeek.valueOf(weekday.trim().toUpperCase()).name();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw ApiException.badRequest("Weekday must be MONDAY to SUNDAY");
        }
    }
}
