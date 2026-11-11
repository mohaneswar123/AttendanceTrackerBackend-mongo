# Backend documentation

One file per feature. Each covers what the feature is for, the documents it stores, its
endpoints, the rules the server enforces and where the code lives.

| Feature | What it covers |
|---|---|
| [accounts.md](accounts.md) | Registration, login, tokens, subscriptions, admins, deleting an account |
| [attendance.md](attendance.md) | Subjects, attendance records, percentages, the data reset |
| [tasks.md](tasks.md) | The to-do list: steps, stars, ordering, dates |
| [pomodoro.md](pomodoro.md) | The focus timer, which runs on the server |
| [calendar.md](calendar.md) | Dated events and reminders |
| [timetable.md](timetable.md) | Reusable weekly routines grouped into modes |
| [habits.md](habits.md) | Habits, the three states, streaks and the week in review |


## Time zone

Everything in the app runs on one zone: `app.time-zone`, `Asia/Kolkata` by default
(`APP_TIME_ZONE`, or the older `CALENDAR_TIME_ZONE`). Almost every date here is a day in a
student's life — whether a habit is due today, which day focus minutes belong to, what date
a calendar entry falls on — and those days run on the students' clock, not the server's.

`config/TimeConfig.java` sets it in two places from that one property: the `Clock` bean
that services inject, and the JVM's own default, so a plain `LocalDate.now()` anywhere —
including code written later that forgets the clock — still means the same day as
everything else. A zone that is not a real zone stops the app at startup.

This is not cosmetic. On a host running in UTC, a student in India who opened the app after
midnight was shown the previous day, and marking a habit was refused with "You cannot mark a
day that has not happened yet" — the day had happened, just not in UTC. The frontend reads
its clock through `utils/date.js`, in the same zone, so the two always agree on today.

## Shared ground

**Everything below the surface is the same across features**, so it isn't repeated in each
file:

- **Base path** `/api`. Every endpoint except `POST /api/users/register`,
  `POST /api/users/login` and `POST /api/admin/login` needs
  `Authorization: Bearer <token>`.
- **Who the caller is** comes from the token, never from the request. Tasks, Pomodoro,
  Calendar and Timetable all start by calling
  `AccessGuard.requireActiveStudent(jwt)` (`security/AccessGuard.java`), which
  - returns 401 when the token is missing, expired or belongs to a deleted account,
  - returns 403 to admin tokens, because those features are for students,
  - returns 403 `SUBSCRIPTION_EXPIRED` / `SUBSCRIPTION_INACTIVE` when the subscription has
    lapsed,
  - and hands back the student, whose id is then used for every read and write.
- **Another student's id gives 404**, not 403. Nothing confirms that someone else's
  record exists.
- **Failures** return `{ "code": ..., "message": ... }` with a matching status, produced by
  `exception/GlobalExceptionHandler.java` from an `ApiException`. The `message` is written
  to be shown to a student as-is.
- **Timestamps** come from the injected `Clock` (`config/TimeConfig.java`), so tests can
  fix the time.
- **Dates** are `YYYY-MM-DD`, parsed strictly by `util/Dates.parseIsoDate`. **Times of day**
  are `HH:mm`, parsed strictly by `util/Times.parseMinutes`, so `25:00` and `9:5` are
  refused rather than guessed at.

## Codes the frontend acts on

| Status | Code | Meaning |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Wrong email or password at login |
| 401 | `ACCOUNT_NOT_FOUND` | The token belongs to a deleted account |
| 403 | `SUBSCRIPTION_EXPIRED`, `SUBSCRIPTION_INACTIVE` | Renew before using the app |
| 409 | `EMAIL_EXISTS` | Another account already uses that email |
| 409 | `FOCUS_ALREADY_RUNNING` | A focus session is already running |
| 409 | `ACTIVITY_OVERLAP` | Two timetable activities would overlap |
| 409 | `HABIT_HAS_HISTORY` | A habit with days recorded has to be archived, not deleted |
| 400 | `INVALID_POSITION` | A task move named neighbours that don't fit |
| 429 | `TOO_MANY_ATTEMPTS` | Too many failed admin sign-ins; wait and try again |

Anything else is shown with its own message and needs no special handling.

## Running the tests

```
mvn test
```

They use Mockito and `@WebMvcTest`; none of them need a database.
