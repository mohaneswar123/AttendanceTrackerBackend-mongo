package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.HabitEntryRepository;
import com.AttendanceRegister.sdc.Repository.HabitRepository;
import com.AttendanceRegister.sdc.dto.HabitEntryRequest;
import com.AttendanceRegister.sdc.dto.HabitEntryResponse;
import com.AttendanceRegister.sdc.dto.HabitRequest;
import com.AttendanceRegister.sdc.dto.HabitResponse;
import com.AttendanceRegister.sdc.dto.HabitSummaryResponse;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Habit;
import com.AttendanceRegister.sdc.model.HabitEntry;
import com.AttendanceRegister.sdc.model.HabitEntryStatus;
import com.AttendanceRegister.sdc.model.HabitGoal;
import com.AttendanceRegister.sdc.model.HabitSchedule;
import com.AttendanceRegister.sdc.model.TimetableColor;
import com.AttendanceRegister.sdc.util.Dates;
import com.AttendanceRegister.sdc.util.Times;

/**
 * Habits, and how well they are being kept.
 *
 * Two rules run through all of this.
 *
 * A skipped day is not a failure. Illness, exams and travel are why students abandon
 * habit apps after one bad day, so a SKIPPED day neither breaks a streak nor counts
 * against a completion rate: it leaves the sum entirely.
 *
 * A missed day is never written by the server. A due day that is past its grace period
 * with no entry already means the habit was not kept, and it breaks a streak exactly as
 * an explicit MISSED does. Writing rows for days nobody touched would need a nightly job,
 * and would grow the collection whether or not the app was ever opened.
 *
 * The grace period is what makes that fair: a day stays open for late logging until noon
 * the following day, so going to bed without opening the app does not cost a streak.
 */
@Service
public class HabitService {

    static final int MAX_HABITS = 30;
    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_ICON_LENGTH = 4;
    static final int MAX_REASON_LENGTH = 200;
    static final int MAX_UNIT_LENGTH = 12;
    static final int MAX_NOTE_LENGTH = 120;
    static final int MAX_TARGET = 1000;
    /** How far back a streak may reach, so one request cannot walk forever */
    static final int MAX_HISTORY_DAYS = 400;
    /** The window the "done %" card reports on */
    static final int RECENT_DAYS = 30;
    /** A day stays open for late logging until this time the next day */
    private static final LocalTime GRACE_UNTIL = LocalTime.NOON;
    private static final double POSITION_STEP = 1024;

    /** A week runs Monday to Sunday, which is also how the summary is framed */
    private static final DayOfWeek WEEK_START = DayOfWeek.MONDAY;

    private final HabitRepository habitRepository;
    private final HabitEntryRepository entryRepository;
    private final Clock clock;

    public HabitService(HabitRepository habitRepository, HabitEntryRepository entryRepository, Clock clock) {
        this.habitRepository = habitRepository;
        this.entryRepository = entryRepository;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** True while the day can still be marked without counting as missed */
    private static boolean withinGrace(LocalDate day, LocalDateTime now) {
        return !now.isAfter(day.plusDays(1).atTime(GRACE_UNTIL));
    }

    // --- Reading

    /** Every habit the student has, archived ones last, each with its figures worked out */
    public List<HabitResponse> getHabits(String userId, boolean includeArchived) {
        Map<String, Map<LocalDate, HabitEntry>> byHabit = entriesByHabit(userId);
        LocalDate today = today();

        return habitRepository.findByUserIdOrderByPositionAsc(userId).stream()
                .filter(habit -> includeArchived || !habit.isArchived())
                .sorted(Comparator.comparing(Habit::isArchived).thenComparing(Habit::getPosition))
                .map(habit -> toResponse(habit, byHabit.getOrDefault(habit.getId(), Map.of()), now()))
                .toList();
    }

    /** The marked days in a range, for the month grid */
    public List<HabitEntryResponse> getEntries(String userId, String from, String to) {
        LocalDate start = Dates.parseIsoDate(from, "From");
        LocalDate end = Dates.parseIsoDate(to, "To");
        if (end.isBefore(start)) {
            throw ApiException.badRequest("The end date cannot be before the start date");
        }
        return entryRepository.findByUserIdAndDateBetween(userId, start, end).stream()
                .map(HabitService::toResponse)
                .toList();
    }

    // --- Writing

    public HabitResponse createHabit(String userId, HabitRequest request) {
        if (habitRepository.countByUserIdAndArchivedFalse(userId) >= MAX_HABITS) {
            throw ApiException.badRequest("You can keep " + MAX_HABITS
                    + " habits at a time. Archive one you have finished with first.");
        }
        Instant now = clock.instant();
        Habit habit = new Habit();
        habit.setUserId(userId);
        habit.setPosition(nextPosition(userId));
        habit.setCreatedAt(now);
        apply(habit, request, now);
        return toResponse(habitRepository.save(habit), Map.of(), now());
    }

    public HabitResponse updateHabit(String userId, String habitId, HabitRequest request) {
        Habit habit = getOwned(userId, habitId);
        apply(habit, request, clock.instant());
        return toResponse(habitRepository.save(habit), entriesFor(habitId), now());
    }

    /** Archiving keeps the history behind the streak; the habit just stops being due */
    public HabitResponse setArchived(String userId, String habitId, boolean archived) {
        Habit habit = getOwned(userId, habitId);
        Instant now = clock.instant();
        habit.setArchived(archived);
        habit.setArchivedAt(archived ? now : null);
        habit.setUpdatedAt(now);
        return toResponse(habitRepository.save(habit), entriesFor(habitId), now());
    }

    /**
     * Only a habit that was never marked can be deleted, because then there is no history
     * to lose. Everything else is archived instead, which is what the page offers.
     */
    public void deleteHabit(String userId, String habitId) {
        Habit habit = getOwned(userId, habitId);
        if (entryRepository.existsByHabitId(habit.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "HABIT_HAS_HISTORY",
                    "This habit has days recorded against it. Archive it instead, so the history is kept.");
        }
        habitRepository.delete(habit);
    }

    /** Mark one day done, missed or skipped. Marking the same day again replaces the mark. */
    public HabitResponse mark(String userId, String habitId, String date, HabitEntryRequest request) {
        Habit habit = getOwned(userId, habitId);
        LocalDate day = Dates.parseIsoDate(date, "Date");
        if (day.isAfter(today())) {
            throw ApiException.badRequest("You cannot mark a day that has not happened yet");
        }
        if (request == null || request.status() == null) {
            throw ApiException.badRequest("Choose done, missed or skipped");
        }

        Instant now = clock.instant();
        HabitEntry entry = entryRepository.findByHabitIdAndDate(habitId, day)
                .orElseGet(() -> new HabitEntry(userId, habitId, day, now));
        entry.setStatus(request.status());
        entry.setAmount(amountFor(habit, request));
        entry.setNote(trimToNull(request.note(), "Note", MAX_NOTE_LENGTH));
        entry.setUpdatedAt(now);
        entryRepository.save(entry);

        return toResponse(habit, entriesFor(habitId), now());
    }

    /** Undo a mark, leaving the day as though it was never touched */
    public HabitResponse clearMark(String userId, String habitId, String date) {
        Habit habit = getOwned(userId, habitId);
        LocalDate day = Dates.parseIsoDate(date, "Date");
        entryRepository.findByHabitIdAndDate(habitId, day).ifPresent(entryRepository::delete);
        return toResponse(habit, entriesFor(habitId), now());
    }

    // --- The week in review

    /**
     * The week that has just finished, for the panel shown from Sunday onward. Archived
     * habits are left out, because there is nothing to act on there.
     */
    public HabitSummaryResponse weeklySummary(String userId) {
        LocalDate lastWeekStart = weekStart(today()).minusWeeks(1);
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

        List<Habit> habits = habitRepository.findByUserIdOrderByPositionAsc(userId).stream()
                .filter(habit -> !habit.isArchived())
                .toList();
        Map<String, Map<LocalDate, HabitEntry>> byHabit = entriesByHabit(userId);

        String bestName = null;
        int bestRate = -1;
        String slippedName = null;
        int slippedRate = -1;
        Habit slipped = null;
        int doneTotal = 0;
        int dueTotal = 0;

        for (Habit habit : habits) {
            Week week = weekFigures(habit, byHabit.getOrDefault(habit.getId(), Map.of()),
                    lastWeekStart, lastWeekEnd, now());
            if (week.due() <= 0) {
                continue;
            }
            doneTotal += week.done();
            dueTotal += week.due();
            int rate = percentage(week.done(), week.due());
            if (rate > bestRate) {
                bestRate = rate;
                bestName = habit.getName();
            }
            if (rate < 100 && (slippedRate < 0 || rate < slippedRate)) {
                slippedRate = rate;
                slippedName = habit.getName();
                slipped = habit;
            }
        }

        return new HabitSummaryResponse(
                lastWeekStart.toString(),
                lastWeekEnd.toString(),
                dueTotal > 0 ? percentage(doneTotal, dueTotal) : -1,
                bestName,
                Math.max(bestRate, 0),
                slippedName,
                Math.max(slippedRate, 0),
                suggestion(habits, slipped, slippedRate, dueTotal));
    }

    /**
     * One thing worth trying next week, chosen by rule rather than guesswork, and always
     * about making the habit easier to keep rather than telling the student off.
     */
    private static String suggestion(List<Habit> habits, Habit slipped, int slippedRate, int dueTotal) {
        if (habits.isEmpty()) {
            return "Add a habit or two, and last week's figures will appear here.";
        }
        if (dueTotal == 0) {
            return "Nothing was due last week. Check which days each habit is set to run on.";
        }
        if (slipped == null) {
            return "Everything you planned got done. This is a good week to add one more habit.";
        }
        if (slipped.getReminderTime() == null) {
            return quoted(slipped) + " has no reminder time. Giving it one makes it easier to remember.";
        }
        if (slippedRate >= 70) {
            return quoted(slipped) + " was close. One more day would have finished the week.";
        }
        return switch (slipped.getSchedule()) {
            case DAILY -> quoted(slipped)
                    + " every day may be a lot to start with. Three days a week is easier to keep.";
            case WEEKDAYS -> quoted(slipped)
                    + " runs every weekday. Picking three days instead is easier to keep.";
            case CUSTOM_DAYS -> quoted(slipped) + " is set for " + slipped.getWeekdays().size()
                    + " days a week. Dropping one makes the rest more likely to happen.";
            case TIMES_PER_WEEK -> quoted(slipped) + " is set to " + slipped.getTimesPerWeek()
                    + " times a week. Try " + Math.max(1, slipped.getTimesPerWeek() - 1) + " and build back up.";
        };
    }

    private static String quoted(Habit habit) {
        return "\"" + habit.getName() + "\"";
    }

    // --- Working out the figures

    private HabitResponse toResponse(Habit habit, Map<LocalDate, HabitEntry> entries, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate thisWeekStart = weekStart(today);
        Week thisWeek = weekFigures(habit, entries, thisWeekStart, thisWeekStart.plusDays(6), now);
        Week lastWeek = weekFigures(habit, entries, thisWeekStart.minusWeeks(1), thisWeekStart.minusDays(1), now);
        Week recent = weekFigures(habit, entries, today.minusDays(RECENT_DAYS - 1), today, now);
        HabitEntry todayEntry = entries.get(today);

        return new HabitResponse(
                habit.getId(),
                habit.getName(),
                habit.getIcon(),
                habit.getColor(),
                habit.getCategory(),
                habit.getReason(),
                habit.getSchedule(),
                habit.getWeekdays(),
                habit.getTimesPerWeek(),
                habit.getGoal(),
                habit.getTarget(),
                habit.getUnit(),
                habit.getReminderTime(),
                habit.isArchived(),
                dueToday(habit, entries, today, thisWeek.done()),
                todayEntry == null ? null : toResponse(todayEntry),
                currentStreak(habit, entries, now),
                bestStreak(habit, entries, now),
                thisWeek.done(),
                thisWeek.due() > 0 ? percentage(thisWeek.done(), thisWeek.due()) : -1,
                lastWeek.due() > 0 ? percentage(lastWeek.done(), lastWeek.due()) : -1,
                recent.due() > 0 ? percentage(recent.done(), recent.due()) : -1,
                habit.getStartDate() == null ? null : habit.getStartDate().toString());
    }

    /**
     * Whether the habit still wants doing today. A "3 times a week" habit stops asking
     * once the week's three are in, rather than nagging for a fourth.
     */
    private static boolean dueToday(Habit habit, Map<LocalDate, HabitEntry> entries,
            LocalDate today, int doneThisWeek) {
        if (habit.isArchived()) {
            return false;
        }
        if (habit.getSchedule() == HabitSchedule.TIMES_PER_WEEK) {
            HabitEntry entry = entries.get(today);
            boolean doneToday = entry != null && entry.getStatus() == HabitEntryStatus.DONE;
            return doneToday || doneThisWeek < habit.getTimesPerWeek();
        }
        return habit.fallsOn(today.getDayOfWeek());
    }

    /** How many were done, and how many counted, over one week */
    private record Week(int done, int due) {
    }

    private static Week weekFigures(Habit habit, Map<LocalDate, HabitEntry> entries,
            LocalDate from, LocalDate to, LocalDateTime now) {
        int done = 0;
        int skipped = 0;
        int dueDays = 0;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            HabitEntry entry = entries.get(day);
            if (entry != null && entry.getStatus() == HabitEntryStatus.DONE) {
                done++;
            }
            if (entry != null && entry.getStatus() == HabitEntryStatus.SKIPPED) {
                skipped++;
            }
            // A day still open for late logging is not counted, so a week in progress is
            // judged on the days that have actually closed rather than reading low
            if (habit.getSchedule() != HabitSchedule.TIMES_PER_WEEK
                    && habit.fallsOn(day.getDayOfWeek())
                    && (entry != null || !withinGrace(day, now))) {
                dueDays++;
            }
        }

        if (habit.getSchedule() == HabitSchedule.TIMES_PER_WEEK) {
            int target = habit.getTimesPerWeek();
            // A week disrupted enough to be skipped is not counted against the student
            if (skipped > 0 && done < target) {
                return new Week(done, 0);
            }
            return new Week(Math.min(done, target), target);
        }
        // Skipped days leave the sum entirely, so they neither help nor hurt
        return new Week(done, Math.max(0, dueDays - skipped));
    }

    /** How one day, or one week, went as far as a streak is concerned */
    private enum Verdict {
        MET,
        /** Neither breaks the streak nor extends it */
        NEUTRAL,
        BROKEN
    }

    private static Verdict dayVerdict(Map<LocalDate, HabitEntry> entries, LocalDate day, LocalDateTime now) {
        HabitEntry entry = entries.get(day);
        if (entry == null) {
            // Still open for late logging, so nothing can be concluded from it yet
            return withinGrace(day, now) ? Verdict.NEUTRAL : Verdict.BROKEN;
        }
        return switch (entry.getStatus()) {
            case DONE -> Verdict.MET;
            case SKIPPED -> Verdict.NEUTRAL;
            case MISSED -> Verdict.BROKEN;
        };
    }

    private static Verdict weekVerdict(Habit habit, Map<LocalDate, HabitEntry> entries,
            LocalDate weekStart, LocalDateTime now) {
        LocalDate weekEnd = weekStart.plusDays(6);
        int done = 0;
        int skipped = 0;
        for (LocalDate day = weekStart; !day.isAfter(weekEnd); day = day.plusDays(1)) {
            HabitEntry entry = entries.get(day);
            if (entry == null) {
                continue;
            }
            if (entry.getStatus() == HabitEntryStatus.DONE) {
                done++;
            } else if (entry.getStatus() == HabitEntryStatus.SKIPPED) {
                skipped++;
            }
        }
        if (done >= habit.getTimesPerWeek()) {
            return Verdict.MET;
        }
        // The week is still running, or the student told us to leave it alone
        if (withinGrace(weekEnd, now) || skipped > 0) {
            return Verdict.NEUTRAL;
        }
        return Verdict.BROKEN;
    }

    /** Units from oldest to newest, so both streaks come out of one walk */
    private static List<Verdict> verdicts(Habit habit, Map<LocalDate, HabitEntry> entries, LocalDateTime now) {
        List<Verdict> out = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        LocalDate earliest = earliestDay(habit, entries, today);

        if (habit.getSchedule() == HabitSchedule.TIMES_PER_WEEK) {
            for (LocalDate week = weekStart(earliest); !week.isAfter(weekStart(today)); week = week.plusWeeks(1)) {
                out.add(weekVerdict(habit, entries, week, now));
            }
            return out;
        }
        for (LocalDate day = earliest; !day.isAfter(today); day = day.plusDays(1)) {
            if (habit.fallsOn(day.getDayOfWeek())) {
                out.add(dayVerdict(entries, day, now));
            }
        }
        return out;
    }

    private static int currentStreak(Habit habit, Map<LocalDate, HabitEntry> entries, LocalDateTime now) {
        List<Verdict> verdicts = verdicts(habit, entries, now);
        int streak = 0;
        for (int i = verdicts.size() - 1; i >= 0; i--) {
            Verdict verdict = verdicts.get(i);
            if (verdict == Verdict.MET) {
                streak++;
            } else if (verdict == Verdict.BROKEN) {
                break;
            }
        }
        return streak;
    }

    private static int bestStreak(Habit habit, Map<LocalDate, HabitEntry> entries, LocalDateTime now) {
        int best = 0;
        int run = 0;
        for (Verdict verdict : verdicts(habit, entries, now)) {
            if (verdict == Verdict.MET) {
                run++;
                best = Math.max(best, run);
            } else if (verdict == Verdict.BROKEN) {
                run = 0;
            }
        }
        return best;
    }

    /** Where to start walking: when the habit began, floored so one habit cannot be unbounded */
    private static LocalDate earliestDay(Habit habit, Map<LocalDate, HabitEntry> entries, LocalDate today) {
        LocalDate floor = today.minusDays(MAX_HISTORY_DAYS);
        LocalDate started = habit.getStartDate() != null
                ? habit.getStartDate()
                : habit.getCreatedAt() == null
                        ? floor
                        : habit.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate earliestEntry = entries.keySet().stream().min(LocalDate::compareTo).orElse(started);
        LocalDate earliest = started.isBefore(earliestEntry) ? started : earliestEntry;
        return earliest.isBefore(floor) ? floor : earliest;
    }

    private static int percentage(int done, int outOf) {
        if (outOf <= 0) {
            return -1;
        }
        return Math.round((done * 100f) / outOf);
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.minusDays((date.getDayOfWeek().getValue() - WEEK_START.getValue() + 7) % 7);
    }

    // --- Plumbing

    private Map<String, Map<LocalDate, HabitEntry>> entriesByHabit(String userId) {
        Map<String, Map<LocalDate, HabitEntry>> byHabit = new HashMap<>();
        for (HabitEntry entry : entryRepository.findByUserId(userId)) {
            byHabit.computeIfAbsent(entry.getHabitId(), id -> new HashMap<>()).put(entry.getDate(), entry);
        }
        return byHabit;
    }

    private Map<LocalDate, HabitEntry> entriesFor(String habitId) {
        Map<LocalDate, HabitEntry> byDate = new HashMap<>();
        for (HabitEntry entry : entryRepository.findByHabitId(habitId)) {
            byDate.put(entry.getDate(), entry);
        }
        return byDate;
    }

    private double nextPosition(String userId) {
        return habitRepository.findByUserIdOrderByPositionAsc(userId).stream()
                .mapToDouble(Habit::getPosition)
                .max()
                .orElse(0) + POSITION_STEP;
    }

    // Another student's habit looks the same as one that does not exist
    private Habit getOwned(String userId, String habitId) {
        return habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> ApiException.notFound("Habit not found"));
    }

    private void apply(Habit habit, HabitRequest request, Instant now) {
        if (request == null) {
            throw ApiException.badRequest("Nothing to save");
        }
        habit.setName(requireText(request.name(), "Name", MAX_NAME_LENGTH));
        habit.setIcon(requireText(request.icon(), "Icon", MAX_ICON_LENGTH));
        habit.setColor(request.color() == null ? TimetableColor.VIOLET : request.color());
        if (request.category() == null) {
            throw ApiException.badRequest("Choose a category");
        }
        habit.setCategory(request.category());
        habit.setReason(trimToNull(request.reason(), "Reason", MAX_REASON_LENGTH));

        habit.setSchedule(requireSchedule(request));
        habit.setWeekdays(weekdaysFor(request));
        habit.setTimesPerWeek(timesPerWeekFor(request));

        habit.setGoal(requireGoal(request));
        habit.setTarget(targetFor(request));
        habit.setUnit(habit.getGoal() == HabitGoal.COUNT
                ? trimToNull(request.unit(), "Unit", MAX_UNIT_LENGTH)
                : null);

        habit.setReminderTime(reminderFor(request));
        habit.setStartDate(request.startDate() == null || request.startDate().isBlank()
                ? (habit.getStartDate() == null ? now.atZone(ZoneOffset.UTC).toLocalDate() : habit.getStartDate())
                : Dates.parseIsoDate(request.startDate(), "Start date"));
        habit.setUpdatedAt(now);
    }

    private static HabitSchedule requireSchedule(HabitRequest request) {
        if (request.schedule() == null) {
            throw ApiException.badRequest("Choose how often this habit should happen");
        }
        return request.schedule();
    }

    private static Set<DayOfWeek> weekdaysFor(HabitRequest request) {
        if (request.schedule() != HabitSchedule.CUSTOM_DAYS) {
            return new LinkedHashSet<>();
        }
        Set<DayOfWeek> days = request.weekdays() == null ? Set.of() : request.weekdays();
        if (days.isEmpty()) {
            throw ApiException.badRequest("Choose at least one day of the week");
        }
        // Kept in week order, so Mon/Wed/Fri always reads back the same way
        return new LinkedHashSet<>(EnumSet.copyOf(days));
    }

    private static int timesPerWeekFor(HabitRequest request) {
        if (request.schedule() != HabitSchedule.TIMES_PER_WEEK) {
            return 0;
        }
        int times = request.timesPerWeek() == null ? 0 : request.timesPerWeek();
        if (times < 1 || times > 7) {
            throw ApiException.badRequest("Choose between 1 and 7 times a week");
        }
        return times;
    }

    private static HabitGoal requireGoal(HabitRequest request) {
        if (request.goal() == null) {
            throw ApiException.badRequest("Choose what counts as doing this habit");
        }
        return request.goal();
    }

    private static int targetFor(HabitRequest request) {
        if (request.goal() == HabitGoal.YES_NO) {
            return 1;
        }
        int target = request.target() == null ? 0 : request.target();
        String label = request.goal() == HabitGoal.DURATION ? "Minutes" : "Target";
        if (target < 1 || target > MAX_TARGET) {
            throw ApiException.badRequest(label + " must be between 1 and " + MAX_TARGET);
        }
        return target;
    }

    private static String reminderFor(HabitRequest request) {
        String value = request.reminderTime();
        if (value == null || value.isBlank()) {
            return null;
        }
        return Times.format(Times.parseMinutes(value, "Reminder time"));
    }

    /** How much of the goal the mark represents, so a count habit can record 6 of 8 */
    private static int amountFor(Habit habit, HabitEntryRequest request) {
        if (request.status() != HabitEntryStatus.DONE) {
            return request.amount() == null ? 0 : Math.max(0, request.amount());
        }
        if (habit.getGoal() == HabitGoal.YES_NO) {
            return 1;
        }
        int amount = request.amount() == null ? habit.getTarget() : request.amount();
        if (amount < 0 || amount > MAX_TARGET) {
            throw ApiException.badRequest("Amount must be between 0 and " + MAX_TARGET);
        }
        return amount;
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

    private static String trimToNull(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ApiException.badRequest(label + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static HabitEntryResponse toResponse(HabitEntry entry) {
        return new HabitEntryResponse(
                entry.getHabitId(),
                entry.getDate().toString(),
                entry.getStatus(),
                entry.getAmount(),
                entry.getNote());
    }
}
