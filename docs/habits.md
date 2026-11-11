# Habits

Things a student is trying to do regularly, and how well they are managing. Read
[README.md](README.md) first for the rules every feature shares.

## Documents

**`habit_table`** (`model/Habit.java`)

| Field | Notes |
|---|---|
| `id`, `userId` | |
| `name` | Required, at most 60 characters |
| `icon` | A short emoji, at most 4 characters |
| `color` | The shared six-colour palette (`TimetableColor`), reused rather than copied |
| `category` | `STUDY`, `HEALTH`, `SLEEP`, `READING` or `PERSONAL` |
| `reason` | Optional. Why it matters, at most 200 characters |
| `schedule` | `DAILY`, `WEEKDAYS` (Mon–Fri), `CUSTOM_DAYS` or `TIMES_PER_WEEK` |
| `weekdays` | Only for `CUSTOM_DAYS`; at least one day |
| `timesPerWeek` | Only for `TIMES_PER_WEEK`; 1 to 7 |
| `goal` | `YES_NO`, `COUNT` or `DURATION` |
| `target` | The number to reach, or minutes. 1 for `YES_NO` |
| `unit` | What the count is in, such as "glasses". Only for `COUNT` |
| `reminderTime` | `"HH:mm"`, or null when the student turned it off |
| `startDate` | The first day it counts from, so a streak cannot start before it existed |
| `archived`, `archivedAt` | Archived habits stop being due and keep their history |
| `position` | Order in the list |

At most 30 un-archived habits per student.

**`habit_entry_table`** (`model/HabitEntry.java`) holds `userId`, `habitId`, `date`,
`status` (`DONE`, `MISSED` or `SKIPPED`), `amount` and an optional `note`. A unique index
on `(habitId, date)` means a day can never hold two contradictory answers; marking the
same day again replaces the mark.

## Endpoints

Every one needs a signed-in student with an active subscription. Another student's habit
answers 404, the same as one that does not exist.

| Method and path | What it does |
|---|---|
| `GET /api/habits?includeArchived=` | Every habit with its figures worked out |
| `GET /api/habits/entries?from=&to=` | The marked days in a range, for the grid |
| `GET /api/habits/progress?range=WEEK\|MONTH\|YEAR` | How the habits went over that stretch — see below. Anything else is 400. |
| `POST /api/habits` | Create |
| `PUT /api/habits/{id}` | Edit |
| `PUT /api/habits/{id}/archive`, `/unarchive` | Stop or resume being due |
| `DELETE /api/habits/{id}` | Only when nothing was ever marked; otherwise 409 `HABIT_HAS_HISTORY` |
| `PUT /api/habits/{id}/entries/{date}` | Mark a day `{status, amount, note}` |
| `DELETE /api/habits/{id}/entries/{date}` | Undo a mark |

## Progress over a week, a month or a year

`GET /api/habits/progress` answers with one headline figure and the same stretch cut into
pieces:

```
{ range, from, to, done, due, rate, buckets: [...], bucketNote }
```

A `rate` of -1 means nothing was due, which reads differently from 0%.

**The sum is done one week at a time.** A "3 times a week" habit owes three a week, not
three a month, so the only way its target scales with the stretch is to walk the stretch
week by week and add up. For a habit tied to particular days the two come to the same
number.

**Nothing is owed before the habit existed.** A habit made in November is not billed for
the ten months of the year before it, which would otherwise report 1% to a student who has
kept it every day since they made it.

**The pieces get coarser as the stretch gets longer:** days for a week, whole Monday weeks
for a month, months for a year. Two consequences fall out of that:

- A piece one day long cannot judge a "times a week" habit, so those habits sit out the
  week's day-sized pieces. `bucketNote` says so when it happens, and it is null otherwise.
  The headline still counts every habit.
- The month's pieces are Monday weeks clipped to the month, not seven-day blocks from the
  1st. A block starting on the 1st straddles two weeks, and a "3 times a week" habit would
  then be charged its three twice for the one piece. Clipped this way the pieces add up to
  the month exactly. A year's months cannot tile into weeks, so a week spanning two months
  is counted in both and only the headline is exact.

A piece never reaches past today: the rest of the month is drawn, so the shape of the
stretch is visible, but it asks for nothing.

## The three states

A day is `DONE`, `MISSED` or `SKIPPED`, and the third is the point of the feature.

Illness, exams and travel are why students give up on habit apps after one bad day, so a
**skipped day neither breaks a streak nor counts against a completion rate**. It leaves
the sum entirely: a week where three days were due and one was skipped is judged out of
two, not three.

## The grace period

**The server never writes a missed day.** A due day in the past with no entry already
means the habit was not kept, and it breaks a streak exactly as an explicit `MISSED`
does. Writing rows for days nobody touched would need a nightly job, and would grow the
collection whether or not the app was ever opened.

What makes that fair is the grace period: **a day stays open for late logging until noon
the following day**. Going to bed without opening the app does not cost a streak, and a
day still in grace is left out of the completion rate rather than counted as a failure.

## Streaks

Both streaks come out of one walk over the habit's history, which is capped at 400 days.
Each unit is judged `MET`, `NEUTRAL` (carries the streak across without extending it) or
`BROKEN`, and the current streak counts back from the newest while the best streak is the
longest run anywhere.

- **Day-based habits** (`DAILY`, `WEEKDAYS`, `CUSTOM_DAYS`) count days the habit is due.
  Days it is not due are skipped over entirely, so a Mon/Wed/Fri habit is not broken by
  Tuesday.
- **`TIMES_PER_WEEK` counts weeks, not days**, because that is what the student promised:
  a week is `MET` once the target is reached, `NEUTRAL` while it is still running or if
  any day in it was skipped, and `BROKEN` otherwise. Going on Tuesday instead of Monday
  changes nothing.

## Completion rates

`thisWeekRate`, `lastWeekRate` and `last30Rate` are whole percentages, and **-1 means
nothing was due**, which reads differently from 0%. A week in progress is judged on the
days that have closed, so it does not read low until Sunday.

## Due today

`DAILY` is always due, `WEEKDAYS` Monday to Friday, `CUSTOM_DAYS` on the chosen days. A
`TIMES_PER_WEEK` habit stops asking once the week's target is in, rather than nagging for
one more — except on the day it was done, so the student can still see the tick.

## Focus sessions

A finished focus period can be credited to one time-based habit. **The student chooses
which**, on the Focus page, and the choice is remembered on that device. Nothing is
guessed: crediting the wrong habit would quietly inflate a streak, and a streak nobody
earned is worse than one that needs a tap. The frontend does this with the ordinary mark
endpoint, so there is no special path on the server.

## Deleting an account

`UserService.deleteUser` removes the student's habits and every entry against them.

## Code

`controller/HabitController.java`, `service/HabitService.java`,
`config/HabitIndexes.java`, `model/Habit*.java`, `dto/Habit*.java`.
Tests: `HabitServiceTest` covers the streak rules, the grace period, the three states and
every refusal.
