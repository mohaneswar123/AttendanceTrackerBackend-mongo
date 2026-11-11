package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.HabitEntryRepository;
import com.AttendanceRegister.sdc.Repository.HabitRepository;
import com.AttendanceRegister.sdc.dto.HabitEntryRequest;
import com.AttendanceRegister.sdc.dto.HabitProgressResponse;
import com.AttendanceRegister.sdc.dto.HabitRequest;
import com.AttendanceRegister.sdc.dto.HabitResponse;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Habit;
import com.AttendanceRegister.sdc.model.HabitCategory;
import com.AttendanceRegister.sdc.model.HabitEntry;
import com.AttendanceRegister.sdc.model.HabitEntryStatus;
import com.AttendanceRegister.sdc.model.HabitGoal;
import com.AttendanceRegister.sdc.model.HabitSchedule;
import com.AttendanceRegister.sdc.model.TimetableColor;
import com.AttendanceRegister.sdc.service.HabitService.ProgressRange;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    // A Wednesday, so "weekdays" and "times a week" both have room either side of it,
    // and an evening clock, so yesterday is past its grace period in most of these
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final LocalDateTime EVENING = TODAY.atTime(18, 0);
    private static final Instant NOW = EVENING.toInstant(ZoneOffset.UTC);
    private static final String USER = "u1";
    private static final String HABIT = "h1";

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitEntryRepository entryRepository;

    private HabitService service;

    @BeforeEach
    void setUp() {
        service = new HabitService(habitRepository, entryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** The same service seen from a different moment, for the grace period */
    private HabitService at(LocalDateTime moment) {
        return new HabitService(habitRepository, entryRepository,
                Clock.fixed(moment.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    // --- Fixtures

    private static Habit habit(HabitSchedule schedule) {
        Habit habit = new Habit();
        habit.setId(HABIT);
        habit.setUserId(USER);
        habit.setName("Revise notes");
        habit.setIcon("📘");
        habit.setColor(TimetableColor.VIOLET);
        habit.setCategory(HabitCategory.STUDY);
        habit.setSchedule(schedule);
        habit.setGoal(HabitGoal.YES_NO);
        habit.setTarget(1);
        // Far enough back that the walk covers everything the tests set up
        habit.setCreatedAt(TODAY.minusDays(60).atStartOfDay(ZoneOffset.UTC).toInstant());
        return habit;
    }

    private static HabitEntry entry(LocalDate date, HabitEntryStatus status) {
        HabitEntry entry = new HabitEntry(USER, HABIT, date, NOW);
        entry.setStatus(status);
        entry.setAmount(status == HabitEntryStatus.DONE ? 1 : 0);
        return entry;
    }

    /** Marks the given offsets from today, newest offset 0 */
    private static List<HabitEntry> days(HabitEntryStatus status, int... offsets) {
        List<HabitEntry> entries = new ArrayList<>();
        for (int offset : offsets) {
            entries.add(entry(TODAY.minusDays(offset), status));
        }
        return entries;
    }

    private HabitResponse only(Habit habit, List<HabitEntry> entries) {
        when(habitRepository.findByUserIdOrderByPositionAsc(USER)).thenReturn(List.of(habit));
        when(entryRepository.findByUserId(USER)).thenReturn(entries);
        return service.getHabits(USER, true).get(0);
    }

    private static HabitRequest request(HabitSchedule schedule, Set<DayOfWeek> weekdays, Integer timesPerWeek,
            HabitGoal goal, Integer target) {
        return new HabitRequest("Read 20 pages", "📖", TimetableColor.CYAN, HabitCategory.READING,
                "Keeps me off my phone", schedule, weekdays, timesPerWeek, goal, target, "pages", "21:00", null);
    }

    // --- Streaks, daily

    @Test
    void aRunOfDoneDaysIsTheStreak() {
        HabitResponse habit = only(habit(HabitSchedule.DAILY), days(HabitEntryStatus.DONE, 0, 1, 2, 3));

        assertThat(habit.currentStreak()).isEqualTo(4);
        assertThat(habit.bestStreak()).isEqualTo(4);
    }

    @Test
    void anUnmarkedTodayDoesNotBreakTheStreakBecauseTheDayIsNotOver() {
        HabitResponse habit = only(habit(HabitSchedule.DAILY), days(HabitEntryStatus.DONE, 1, 2, 3));

        assertThat(habit.currentStreak()).isEqualTo(3);
    }

    @Test
    void anUnmarkedDayInThePastBreaksIt() {
        // Yesterday was never marked, so the run before it does not reach today
        HabitResponse habit = only(habit(HabitSchedule.DAILY), days(HabitEntryStatus.DONE, 0, 2, 3, 4));

        assertThat(habit.currentStreak()).isEqualTo(1);
        assertThat(habit.bestStreak()).isEqualTo(3);
    }

    @Test
    void aMissedDayBreaksIt() {
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 0, 1, 3, 4));
        entries.addAll(days(HabitEntryStatus.MISSED, 2));

        HabitResponse habit = only(habit(HabitSchedule.DAILY), entries);

        assertThat(habit.currentStreak()).isEqualTo(2);
        assertThat(habit.bestStreak()).isEqualTo(2);
    }

    @Test
    void aSkippedDayCarriesTheStreakAcrossWithoutAddingToIt() {
        // This is the whole reason the third state exists
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 0, 1, 3, 4));
        entries.addAll(days(HabitEntryStatus.SKIPPED, 2));

        HabitResponse habit = only(habit(HabitSchedule.DAILY), entries);

        assertThat(habit.currentStreak()).isEqualTo(4);
        assertThat(habit.bestStreak()).isEqualTo(4);
    }

    @Test
    void theBestStreakSurvivesALaterBreak() {
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 5, 6, 7, 8, 9));
        entries.addAll(days(HabitEntryStatus.MISSED, 4));
        entries.addAll(days(HabitEntryStatus.DONE, 0, 1));

        HabitResponse habit = only(habit(HabitSchedule.DAILY), entries);

        assertThat(habit.currentStreak()).isEqualTo(2);
        assertThat(habit.bestStreak()).isEqualTo(5);
    }

    // --- Streaks, chosen weekdays

    @Test
    void onlyTheChosenDaysCountTowardsTheStreak() {
        Habit habit = habit(HabitSchedule.CUSTOM_DAYS);
        // Today is a Wednesday
        habit.setWeekdays(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        // Wed, Mon and the Friday before: the untouched Tuesday in between is not due
        HabitResponse response = only(habit, days(HabitEntryStatus.DONE, 0, 2, 5));

        assertThat(response.currentStreak()).isEqualTo(3);
    }

    @Test
    void aMissedChosenDayStillBreaksIt() {
        Habit habit = habit(HabitSchedule.CUSTOM_DAYS);
        habit.setWeekdays(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        // Monday two days ago was never marked
        HabitResponse response = only(habit, days(HabitEntryStatus.DONE, 0, 5));

        assertThat(response.currentStreak()).isEqualTo(1);
    }

    // --- Streaks, x times a week

    @Test
    void aTimesPerWeekStreakCountsWeeksNotDays() {
        Habit habit = habit(HabitSchedule.TIMES_PER_WEEK);
        habit.setTimesPerWeek(3);
        // This week has its three already (Mon, Tue, Wed), and so did last week
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 0, 1, 2));
        entries.addAll(days(HabitEntryStatus.DONE, 7, 8, 9));

        HabitResponse response = only(habit, entries);

        assertThat(response.currentStreak()).isEqualTo(2);
    }

    @Test
    void anUnfinishedWeekDoesNotBreakTheWeeklyStreak() {
        Habit habit = habit(HabitSchedule.TIMES_PER_WEEK);
        habit.setTimesPerWeek(3);
        // Only one done so far this week, but the week is still running
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 0));
        entries.addAll(days(HabitEntryStatus.DONE, 7, 8, 9));

        HabitResponse response = only(habit, entries);

        assertThat(response.currentStreak()).isEqualTo(1);
    }

    @Test
    void aWeekThatMissedItsTargetBreaksTheWeeklyStreak() {
        Habit habit = habit(HabitSchedule.TIMES_PER_WEEK);
        habit.setTimesPerWeek(3);
        // Last week managed two of three; the week before managed three
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 7, 8));
        entries.addAll(days(HabitEntryStatus.DONE, 14, 15, 16));

        HabitResponse response = only(habit, entries);

        assertThat(response.currentStreak()).isZero();
        assertThat(response.bestStreak()).isEqualTo(1);
    }

    @Test
    void aWeekTheStudentSkippedDoesNotBreakTheWeeklyStreak() {
        Habit habit = habit(HabitSchedule.TIMES_PER_WEEK);
        habit.setTimesPerWeek(3);
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 0, 1, 2));
        entries.addAll(days(HabitEntryStatus.SKIPPED, 8));
        entries.addAll(days(HabitEntryStatus.DONE, 14, 15, 16));

        HabitResponse response = only(habit, entries);

        assertThat(response.currentStreak()).isEqualTo(2);
    }

    // --- Due today

    @Test
    void aTimesPerWeekHabitStopsAskingOnceTheWeekIsDone() {
        Habit habit = habit(HabitSchedule.TIMES_PER_WEEK);
        habit.setTimesPerWeek(2);
        // Monday and Tuesday are in, so Wednesday does not need a third
        HabitResponse response = only(habit, days(HabitEntryStatus.DONE, 1, 2));

        assertThat(response.dueToday()).isFalse();
        assertThat(response.doneThisWeek()).isEqualTo(2);
    }

    @Test
    void itStillShowsOnTheDayItWasDone() {
        Habit habit = habit(HabitSchedule.TIMES_PER_WEEK);
        habit.setTimesPerWeek(1);
        // Done today and the week is finished, but the student should still see the tick
        HabitResponse response = only(habit, days(HabitEntryStatus.DONE, 0));

        assertThat(response.dueToday()).isTrue();
    }

    @Test
    void aCustomDaysHabitIsOnlyDueOnItsDays() {
        Habit habit = habit(HabitSchedule.CUSTOM_DAYS);
        habit.setWeekdays(Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));

        assertThat(only(habit, List.of()).dueToday()).isFalse();
    }

    @Test
    void weekdaysMeansMondayToFriday() {
        Habit habit = habit(HabitSchedule.WEEKDAYS);

        // Today is a Wednesday, so it is due; the weekend is not
        assertThat(only(habit, List.of()).dueToday()).isTrue();
        assertThat(habit.fallsOn(DayOfWeek.SATURDAY)).isFalse();
        assertThat(habit.fallsOn(DayOfWeek.SUNDAY)).isFalse();
        assertThat(habit.fallsOn(DayOfWeek.MONDAY)).isTrue();
    }

    @Test
    void anArchivedHabitIsNeverDue() {
        Habit habit = habit(HabitSchedule.DAILY);
        habit.setArchived(true);

        assertThat(only(habit, List.of()).dueToday()).isFalse();
    }

    // --- Completion rate

    @Test
    void theRateCountsDoneAgainstTheDaysThatWereDue() {
        // Today is Wednesday, so Mon, Tue and Wed have come round; two of them were done
        HabitResponse habit = only(habit(HabitSchedule.DAILY), days(HabitEntryStatus.DONE, 0, 2));

        // Thursday onwards has not happened, so it is 2 of 3 rather than 2 of 7
        assertThat(habit.thisWeekRate()).isEqualTo(67);
    }

    @Test
    void skippedDaysLeaveTheRateAlone() {
        Habit daily = habit(HabitSchedule.DAILY);
        // Monday done, Tuesday skipped, Wednesday done: two out of the two that counted
        List<HabitEntry> entries = new ArrayList<>(days(HabitEntryStatus.DONE, 0, 2));
        entries.addAll(days(HabitEntryStatus.SKIPPED, 1));

        assertThat(only(daily, entries).thisWeekRate()).isEqualTo(100);
    }

    @Test
    void aDayThatHasNotComeRoundYetIsNotHeldAgainstTheStudent() {
        Habit habit = habit(HabitSchedule.CUSTOM_DAYS);
        // Saturday is still to come this week, so there is nothing to judge yet
        habit.setWeekdays(Set.of(DayOfWeek.SATURDAY));

        assertThat(only(habit, List.of()).thisWeekRate()).isEqualTo(-1);
    }

    @Test
    void aWeekWhereEveryDueDayWasSkippedHasNothingToReport() {
        Habit habit = habit(HabitSchedule.CUSTOM_DAYS);
        habit.setWeekdays(Set.of(DayOfWeek.MONDAY));
        // The only due day this week was skipped, so the week reads as "nothing due"
        HabitResponse response = only(habit, days(HabitEntryStatus.SKIPPED, 2));

        assertThat(response.thisWeekRate()).isEqualTo(-1);
    }

    // --- Marking

    @Test
    void markingADayStoresWhatWasDone() {
        Habit habit = habit(HabitSchedule.DAILY);
        habit.setGoal(HabitGoal.COUNT);
        habit.setTarget(8);
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit));
        when(entryRepository.findByHabitIdAndDate(HABIT, TODAY)).thenReturn(Optional.empty());

        service.mark(USER, HABIT, TODAY.toString(), new HabitEntryRequest(HabitEntryStatus.DONE, 6, null));

        ArgumentCaptor<HabitEntry> saved = ArgumentCaptor.forClass(HabitEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(HabitEntryStatus.DONE);
        assertThat(saved.getValue().getAmount()).isEqualTo(6);
        assertThat(saved.getValue().getDate()).isEqualTo(TODAY);
    }

    @Test
    void aSkipKeepsItsNote() {
        Habit habit = habit(HabitSchedule.DAILY);
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit));
        when(entryRepository.findByHabitIdAndDate(HABIT, TODAY)).thenReturn(Optional.empty());

        service.mark(USER, HABIT, TODAY.toString(),
                new HabitEntryRequest(HabitEntryStatus.SKIPPED, null, "  Exam week  "));

        ArgumentCaptor<HabitEntry> saved = ArgumentCaptor.forClass(HabitEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("Exam week");
    }

    @Test
    void aNoteCanBeLeftOnAnyKindOfDay() {
        Habit habit = habit(HabitSchedule.DAILY);
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit));
        when(entryRepository.findByHabitIdAndDate(HABIT, TODAY)).thenReturn(Optional.empty());

        service.mark(USER, HABIT, TODAY.toString(),
                new HabitEntryRequest(HabitEntryStatus.DONE, null, "Finally got through it"));

        ArgumentCaptor<HabitEntry> saved = ArgumentCaptor.forClass(HabitEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("Finally got through it");
    }

    @Test
    void markingTheSameDayAgainReplacesTheMark() {
        Habit habit = habit(HabitSchedule.DAILY);
        HabitEntry existing = entry(TODAY, HabitEntryStatus.MISSED);
        existing.setId("e1");
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit));
        when(entryRepository.findByHabitIdAndDate(HABIT, TODAY)).thenReturn(Optional.of(existing));

        service.mark(USER, HABIT, TODAY.toString(), new HabitEntryRequest(HabitEntryStatus.DONE, null, null));

        ArgumentCaptor<HabitEntry> saved = ArgumentCaptor.forClass(HabitEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("e1");
        assertThat(saved.getValue().getStatus()).isEqualTo(HabitEntryStatus.DONE);
    }

    @Test
    void aDayThatHasNotHappenedCannotBeMarked() {
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit(HabitSchedule.DAILY)));

        Throwable thrown = catchThrowable(() -> service.mark(USER, HABIT, TODAY.plusDays(1).toString(),
                new HabitEntryRequest(HabitEntryStatus.DONE, null, null)));

        assertThat(thrown).isInstanceOf(ApiException.class)
                .hasMessageContaining("has not happened yet");
        verify(entryRepository, never()).save(any());
    }

    // --- Archiving and deleting

    @Test
    void archivingKeepsTheHabitAndItsHistory() {
        Habit habit = habit(HabitSchedule.DAILY);
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit));
        when(habitRepository.save(any(Habit.class))).thenAnswer(call -> call.getArgument(0));
        when(entryRepository.findByHabitId(HABIT)).thenReturn(days(HabitEntryStatus.DONE, 0, 1));

        HabitResponse response = service.setArchived(USER, HABIT, true);

        assertThat(response.archived()).isTrue();
        assertThat(response.bestStreak()).isEqualTo(2);
        verify(entryRepository, never()).deleteByHabitId(anyString());
    }

    @Test
    void aHabitWithHistoryIsRefusedRatherThanDeleted() {
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit(HabitSchedule.DAILY)));
        when(entryRepository.existsByHabitId(HABIT)).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.deleteHabit(USER, HABIT));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ApiException) thrown).getCode()).isEqualTo("HABIT_HAS_HISTORY");
        verify(habitRepository, never()).delete(any());
    }

    @Test
    void aHabitThatWasNeverMarkedCanBeDeleted() {
        Habit habit = habit(HabitSchedule.DAILY);
        when(habitRepository.findByIdAndUserId(HABIT, USER)).thenReturn(Optional.of(habit));
        when(entryRepository.existsByHabitId(HABIT)).thenReturn(false);

        service.deleteHabit(USER, HABIT);

        verify(habitRepository).delete(habit);
    }

    // --- The grace period for late logging

    @Test
    void yesterdayCanStillBeMarkedUntilNoonToday() {
        when(habitRepository.findByUserIdOrderByPositionAsc(USER)).thenReturn(List.of(habit(HabitSchedule.DAILY)));
        when(entryRepository.findByUserId(USER)).thenReturn(days(HabitEntryStatus.DONE, 2, 3, 4));

        // Half past nine in the morning: yesterday is unmarked but still open, so the
        // run from the day before is not broken yet
        HabitResponse morning = at(TODAY.atTime(9, 30)).getHabits(USER, true).get(0);

        assertThat(morning.currentStreak()).isEqualTo(3);
    }

    @Test
    void onceNoonHasPassedAnUnmarkedYesterdayCountsAsMissed() {
        when(habitRepository.findByUserIdOrderByPositionAsc(USER)).thenReturn(List.of(habit(HabitSchedule.DAILY)));
        when(entryRepository.findByUserId(USER)).thenReturn(days(HabitEntryStatus.DONE, 2, 3, 4));

        HabitResponse afternoon = at(TODAY.atTime(12, 1)).getHabits(USER, true).get(0);

        assertThat(afternoon.currentStreak()).isZero();
        assertThat(afternoon.bestStreak()).isEqualTo(3);
    }

    @Test
    void aDayStillInGraceIsNotCountedAgainstTheRate() {
        when(habitRepository.findByUserIdOrderByPositionAsc(USER)).thenReturn(List.of(habit(HabitSchedule.DAILY)));
        // Monday done, Tuesday not marked, and it is Wednesday morning
        when(entryRepository.findByUserId(USER)).thenReturn(days(HabitEntryStatus.DONE, 2));

        HabitResponse morning = at(TODAY.atTime(9, 30)).getHabits(USER, true).get(0);

        // Only Monday has closed, and it was done
        assertThat(morning.thisWeekRate()).isEqualTo(100);
    }

    // --- Setting one up

    @Test
    void aCustomDaysHabitNeedsAtLeastOneDay() {
        Throwable thrown = catchThrowable(() -> service.createHabit(USER,
                request(HabitSchedule.CUSTOM_DAYS, Set.of(), null, HabitGoal.YES_NO, null)));

        assertThat(thrown).isInstanceOf(ApiException.class).hasMessageContaining("at least one day");
    }

    @Test
    void aTimesPerWeekHabitNeedsOneToSeven() {
        Throwable thrown = catchThrowable(() -> service.createHabit(USER,
                request(HabitSchedule.TIMES_PER_WEEK, null, 8, HabitGoal.YES_NO, null)));

        assertThat(thrown).isInstanceOf(ApiException.class).hasMessageContaining("between 1 and 7");
    }

    @Test
    void aCountHabitNeedsATarget() {
        Throwable thrown = catchThrowable(() -> service.createHabit(USER,
                request(HabitSchedule.DAILY, null, null, HabitGoal.COUNT, 0)));

        assertThat(thrown).isInstanceOf(ApiException.class).hasMessageContaining("Target must be between");
    }

    @Test
    void aYesNoHabitNeedsNoTargetAndKeepsNoUnit() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(call -> call.getArgument(0));

        HabitResponse response = service.createHabit(USER,
                request(HabitSchedule.DAILY, null, null, HabitGoal.YES_NO, null));

        assertThat(response.target()).isEqualTo(1);
        assertThat(response.unit()).isNull();
    }

    @Test
    void aReminderIsOptionalAndIsStoredAsHoursAndMinutes() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(call -> call.getArgument(0));

        HabitRequest withReminder = request(HabitSchedule.DAILY, null, null, HabitGoal.YES_NO, null);
        assertThat(service.createHabit(USER, withReminder).reminderTime()).isEqualTo("21:00");

        HabitRequest withoutReminder = new HabitRequest("Sleep by 12", "🌙", TimetableColor.SLATE,
                HabitCategory.SLEEP, null, HabitSchedule.DAILY, null, null, HabitGoal.YES_NO, null, null, null, null);
        assertThat(service.createHabit(USER, withoutReminder).reminderTime()).isNull();
    }

    @Test
    void anImpossibleReminderTimeIsRefused() {
        HabitRequest bad = new HabitRequest("Sleep by 12", "🌙", TimetableColor.SLATE, HabitCategory.SLEEP,
                null, HabitSchedule.DAILY, null, null, HabitGoal.YES_NO, null, null, "25:00", null);

        Throwable thrown = catchThrowable(() -> service.createHabit(USER, bad));

        assertThat(thrown).isInstanceOf(ApiException.class).hasMessageContaining("Reminder time");
    }

    @Test
    void thereIsALimitOnHowManyHabitsAreKeptAtOnce() {
        when(habitRepository.countByUserIdAndArchivedFalse(USER)).thenReturn((long) HabitService.MAX_HABITS);

        Throwable thrown = catchThrowable(() -> service.createHabit(USER,
                request(HabitSchedule.DAILY, null, null, HabitGoal.YES_NO, null)));

        assertThat(thrown).isInstanceOf(ApiException.class).hasMessageContaining("Archive one");
    }

    @Test
    void anotherStudentsHabitIsNotFound() {
        when(habitRepository.findByIdAndUserId(HABIT, "someone-else")).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.setArchived("someone-else", HABIT, true));

        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Progress over a week, a month and a year

    private HabitProgressResponse progress(Habit habit, List<HabitEntry> entries, ProgressRange range) {
        when(habitRepository.findByUserIdOrderByPositionAsc(USER)).thenReturn(List.of(habit));
        when(entryRepository.findByUserId(USER)).thenReturn(entries);
        return service.progress(USER, range);
    }

    @Test
    void theWeekRangeStartsOnMondayAndIsCutIntoSevenDays() {
        // TODAY is a Wednesday, so Monday and Tuesday are behind it
        HabitProgressResponse week = progress(habit(HabitSchedule.DAILY),
                days(HabitEntryStatus.DONE, 0, 1, 2), ProgressRange.WEEK);

        assertThat(week.from()).isEqualTo(TODAY.minusDays(2).toString());
        assertThat(week.to()).isEqualTo(TODAY.toString());
        assertThat(week.buckets()).hasSize(7);
        assertThat(week.buckets().get(0).label()).isEqualTo("Mon");
        assertThat(week.done()).isEqualTo(3);
        assertThat(week.rate()).isEqualTo(100);
    }

    @Test
    void daysStillToComeAreInTheWeekButAskForNothing() {
        HabitProgressResponse week = progress(habit(HabitSchedule.DAILY),
                days(HabitEntryStatus.DONE, 0, 1, 2), ProgressRange.WEEK);

        // Thursday onward: drawn, so the week reads as a week, but not counted against anyone
        assertThat(week.buckets().subList(3, 7))
                .allSatisfy(bucket -> {
                    assertThat(bucket.due()).isZero();
                    assertThat(bucket.rate()).isEqualTo(-1);
                });
    }

    @Test
    void aMonthIsCutIntoWeeksAndAYearIntoMonths() {
        Habit daily = habit(HabitSchedule.DAILY);
        List<HabitEntry> entries = days(HabitEntryStatus.DONE, 0, 1, 2);

        assertThat(progress(daily, entries, ProgressRange.MONTH).buckets())
                .extracting(HabitProgressResponse.Bucket::label)
                .startsWith("Week 1", "Week 2");
        assertThat(progress(daily, entries, ProgressRange.YEAR).buckets())
                .hasSize(12)
                .extracting(HabitProgressResponse.Bucket::label)
                .startsWith("Jan")
                .endsWith("Dec");
    }

    @Test
    void theMonthStartsOnTheFirstAndTheYearOnTheFirstOfJanuary() {
        Habit daily = habit(HabitSchedule.DAILY);
        List<HabitEntry> entries = days(HabitEntryStatus.DONE, 0);

        assertThat(progress(daily, entries, ProgressRange.MONTH).from())
                .isEqualTo(TODAY.withDayOfMonth(1).toString());
        assertThat(progress(daily, entries, ProgressRange.YEAR).from())
                .isEqualTo(TODAY.withDayOfYear(1).toString());
    }

    /**
     * The reason the sum is done week by week rather than over the whole stretch at once.
     * Three a week owes three for each week that has closed, not three for the month.
     */
    @Test
    void aTimesPerWeekHabitOwesItsTargetEveryWeekOfTheRange() {
        Habit threeAWeek = habit(HabitSchedule.TIMES_PER_WEEK);
        threeAWeek.setTimesPerWeek(3);
        // November 2026 starts on a Sunday, and TODAY is Wednesday the 11th, so the month so
        // far touches three Monday weeks: the 1st on its own, the 2nd to the 8th, and the
        // 9th to the 11th. Three a week over three weeks is nine owed.
        HabitProgressResponse month = progress(threeAWeek,
                days(HabitEntryStatus.DONE, 0, 1, 2, 7, 8, 9), ProgressRange.MONTH);

        assertThat(month.due()).isEqualTo(9);
        // Three in the week of the 9th and three in the week of the 2nd; the 1st had none
        assertThat(month.done()).isEqualTo(6);
        assertThat(month.rate()).isEqualTo(67);
    }

    /**
     * The same marks over the same days, read as a week instead of a month, owe three
     * rather than nine. The target follows the stretch being asked about.
     */
    @Test
    void theSameHabitOwesLessOverAShorterStretch() {
        Habit threeAWeek = habit(HabitSchedule.TIMES_PER_WEEK);
        threeAWeek.setTimesPerWeek(3);
        HabitProgressResponse week = progress(threeAWeek,
                days(HabitEntryStatus.DONE, 0, 1, 2, 7, 8, 9), ProgressRange.WEEK);

        assertThat(week.due()).isEqualTo(3);
        assertThat(week.done()).isEqualTo(3);
    }

    @Test
    void aSkippedDayLeavesTheSumRatherThanCountingAgainstTheStudent() {
        HabitProgressResponse week = progress(habit(HabitSchedule.DAILY),
                List.of(entry(TODAY, HabitEntryStatus.DONE),
                        entry(TODAY.minusDays(1), HabitEntryStatus.SKIPPED),
                        entry(TODAY.minusDays(2), HabitEntryStatus.DONE)),
                ProgressRange.WEEK);

        assertThat(week.done()).isEqualTo(2);
        assertThat(week.due()).isEqualTo(2);
        assertThat(week.rate()).isEqualTo(100);
    }

    @Test
    void nothingDueReadsAsMinusOneRatherThanZeroPerCent() {
        // CUSTOM_DAYS is the schedule that reads the chosen days; WEEKDAYS is always Mon-Fri
        Habit onFridays = habit(HabitSchedule.CUSTOM_DAYS);
        onFridays.setWeekdays(Set.of(DayOfWeek.FRIDAY));
        // TODAY is a Wednesday, so this week's Friday has not happened yet
        HabitProgressResponse week = progress(onFridays, List.of(), ProgressRange.WEEK);

        assertThat(week.due()).isZero();
        assertThat(week.rate()).isEqualTo(-1);
    }

    /**
     * A year started in January but the habit started in November. Billing the habit for
     * the ten months it did not exist would report 1% to a student who has kept it every
     * day since they made it.
     */
    @Test
    void aHabitOwesNothingForTheTimeBeforeItExisted() {
        Habit madeYesterday = habit(HabitSchedule.DAILY);
        madeYesterday.setCreatedAt(TODAY.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        madeYesterday.setStartDate(TODAY.minusDays(1));

        HabitProgressResponse year = progress(madeYesterday,
                days(HabitEntryStatus.DONE, 0, 1), ProgressRange.YEAR);

        assertThat(year.due()).isEqualTo(2);
        assertThat(year.done()).isEqualTo(2);
        assertThat(year.rate()).isEqualTo(100);
    }

    @Test
    void aDaySizedPieceLeavesOutTheTimesPerWeekHabitsAndSaysSo() {
        Habit daily = habit(HabitSchedule.DAILY);
        Habit threeAWeek = habit(HabitSchedule.TIMES_PER_WEEK);
        threeAWeek.setId("h2");
        threeAWeek.setName("Gym");
        threeAWeek.setTimesPerWeek(3);

        when(habitRepository.findByUserIdOrderByPositionAsc(USER)).thenReturn(List.of(daily, threeAWeek));
        when(entryRepository.findByUserId(USER)).thenReturn(days(HabitEntryStatus.DONE, 0, 1, 2));

        HabitProgressResponse week = service.progress(USER, ProgressRange.WEEK);

        // Monday owed one thing, the daily habit, not one plus a whole week of gym
        assertThat(week.buckets().get(0).due()).isEqualTo(1);
        assertThat(week.buckets().get(0).rate()).isEqualTo(100);
        assertThat(week.bucketNote()).contains("times a week");
        // The headline still counts both: three days of revision, and three gym sessions owed
        assertThat(week.due()).isEqualTo(6);
    }

    @Test
    void thereIsNoNoteWhenEveryHabitFitsThePieces() {
        HabitProgressResponse week = progress(habit(HabitSchedule.DAILY),
                days(HabitEntryStatus.DONE, 0), ProgressRange.WEEK);

        assertThat(week.bucketNote()).isNull();
    }

    /**
     * The month's pieces are whole Monday weeks clipped to the month, so they add up to the
     * month's own figure. That is the reason they are not simply "the 1st to the 7th": a
     * seven-day block starting on the 1st straddles two weeks, and a "3 times a week" habit
     * would then be charged its three twice for the one piece.
     */
    @Test
    void theMonthsPiecesAddUpToTheMonth() {
        Habit daily = habit(HabitSchedule.DAILY);
        Habit threeAWeek = habit(HabitSchedule.TIMES_PER_WEEK);
        threeAWeek.setId("h2");
        threeAWeek.setTimesPerWeek(3);

        when(habitRepository.findByUserIdOrderByPositionAsc(USER)).thenReturn(List.of(daily, threeAWeek));
        when(entryRepository.findByUserId(USER)).thenReturn(days(HabitEntryStatus.DONE, 0, 1, 2));

        HabitProgressResponse month = service.progress(USER, ProgressRange.MONTH);

        assertThat(month.bucketNote()).isNull();
        assertThat(month.buckets().stream().mapToInt(HabitProgressResponse.Bucket::due).sum())
                .isEqualTo(month.due());
        assertThat(month.buckets().stream().mapToInt(HabitProgressResponse.Bucket::done).sum())
                .isEqualTo(month.done());
    }

    @Test
    void piecesThatHaveNotHappenedYetAskForNothing() {
        HabitProgressResponse year = progress(habit(HabitSchedule.DAILY),
                days(HabitEntryStatus.DONE, 0), ProgressRange.YEAR);

        // TODAY is in November, so December is drawn but owes nothing
        assertThat(year.buckets()).hasSize(12);
        assertThat(year.buckets().get(11).label()).isEqualTo("Dec");
        assertThat(year.buckets().get(11).due()).isZero();
        assertThat(year.buckets().get(11).rate()).isEqualTo(-1);
    }

    @Test
    void archivedHabitsAreLeftOutOfProgress() {
        Habit archived = habit(HabitSchedule.DAILY);
        archived.setArchived(true);

        HabitProgressResponse week = progress(archived, days(HabitEntryStatus.DONE, 0), ProgressRange.WEEK);

        // Nothing was owed at all, which is how an archived habit reads: it is not due
        assertThat(week.due()).isZero();
        assertThat(week.rate()).isEqualTo(-1);
    }
}
