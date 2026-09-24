# Attendance In Hand (backend)

REST API for the Attendance In Hand app, built with Spring Boot 3.5, Java 17 and MongoDB. The frontend lives in the separate `AttendanceTrackerfront` repository.

## Running locally

```
./mvnw spring-boot:run
```

The API listens on http://localhost:8080. Run the tests with `./mvnw test`. They don't need a database.

**[`docs/`](docs/README.md) has a file per feature** — accounts, attendance, tasks, pomodoro, calendar, timetable — covering the documents each stores, its endpoints and the rules behind them. This README is the short version.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | random at startup | Key that signs login tokens, at least 32 characters. **Set it in production.** Without it, every restart signs everyone out. |
| `CALENDAR_TIME_ZONE` | `Asia/Kolkata` | Time zone for calendar dates and times; all students see entries in this zone |
| `CORS_ALLOWED_ORIGINS` | `https://attendanceinhand.netlify.app,http://localhost:5173` | Comma-separated frontend origins allowed to call the API |
| `PORT` | `8080` | HTTP port (Render sets this) |

Token lifetimes are set in `application.properties`: `app.jwt.user-token-hours` (30 days) and `app.jwt.admin-token-hours` (12 hours). The default Pomodoro lengths, used when a student doesn't pick their own, are `app.pomodoro.focus-seconds` (25 minutes) and `app.pomodoro.break-seconds` (5 minutes).

## Authentication

`POST /api/users/login` and `POST /api/admin/login` return `{ "token": ..., "user": ... }`. Every other endpoint except `POST /api/users/register` needs the header `Authorization: Bearer <token>`.

- Students can only read and change their own data, and only while their subscription is active.
- Admins can use `/api/users` (list), `DELETE /api/users/{id}` and everything under `/api/users/admin/**`, and can read any student's data.

Passwords are stored as BCrypt hashes. On startup, `LegacyPasswordMigration` hashes any passwords still stored in plain text from before hashing was added.

## Errors

Failed requests return `{ "code": ..., "message": ... }` with a matching HTTP status. The codes the frontend acts on:

| Status | Code | Meaning |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Wrong email or password at login |
| 401 | `ACCOUNT_NOT_FOUND` | The token belongs to a deleted account |
| 403 | `SUBSCRIPTION_EXPIRED`, `SUBSCRIPTION_INACTIVE` | The user must renew before using the app |
| 409 | `EMAIL_EXISTS` | Registration or email change uses another account's email. Names don't have to be unique. |

## Endpoints

| Method and path | Who |
|---|---|
| `POST /api/users/register`, `POST /api/users/login`, `POST /api/admin/login` | Anyone |
| `GET /api/users/me`, `POST /api/users/change-password` | Signed-in student |
| `GET /api/users/{id}`, `PUT /api/users/{id}/email` | That student or an admin |
| `GET /api/users`, `DELETE /api/users/{id}` (with the user's data) | Admin |
| `PUT /api/users/admin/activate/{id}?days=N`, `PUT /api/users/admin/deactivate/{id}`, `PUT /api/users/admin/{id}/password` | Admin |
| `POST /api/subjects/add?userId&name`, `GET /api/subjects/user/{userId}`, `DELETE /api/subjects/{subjectId}/user/{userId}` | That student or an admin |
| `POST /api/attendance/add?userId&subjectId&status&date&classNumber`, `GET /api/attendance/user/{userId}` | That student or an admin |
| `PUT /api/attendance/{id}`, `DELETE /api/attendance/{id}` | The record's owner or an admin |
| `DELETE /api/reset/user/{userId}` (subjects and attendance only) | That student or an admin |

`status` is `Present`, `Absent` or `No Class`, `date` is `YYYY-MM-DD`, and `classNumber` is the class length in hours (1 to 3).

### Tasks and Pomodoro

These two features are separate from each other and only for students with an active subscription. Admin tokens get 403. The student always comes from the token. Another student's task or session id gives 404.

| Method and path | What it does |
|---|---|
| `GET /api/tasks?from=&to=` | The student's tasks dated between the two `YYYY-MM-DD` dates (inclusive), ordered by position. Either date can be left out; no dates returns every task. |
| `POST /api/tasks` `{title, taskDate, priority}` | Adds a task to the bottom of To Do. `priority` is `LOW`, `MEDIUM`, `HIGH` or null. |
| `PUT /api/tasks/{id}` `{title, taskDate, priority}` | Edit |
| `PUT /api/tasks/{id}/move` `{status, afterTaskId, beforeTaskId}` | Moves the task to `TODO`, `IN_PROGRESS` or `DONE`, between the given neighbours (either may be null). Neighbours must be the student's own tasks in that column (400 `INVALID_POSITION` otherwise). |
| `DELETE /api/tasks/{id}` | Delete |
| `GET /api/pomodoro/current` | `{phase: IDLE / FOCUS / BREAK, sessionId, remainingSeconds, totalSeconds, paused}` |
| `POST /api/pomodoro/start` `{focusMinutes, breakMinutes}` | Starts a focus session with the student's chosen lengths: focus 1–120 minutes, break 1–30. Leave either out (or send no body) for the default 25 and 5. 409 `FOCUS_ALREADY_RUNNING` if one is running. |
| `PUT /api/pomodoro/{id}/pause`, `/resume`, `/stop`, `/complete`, `/skip-break` | Each returns the new state. `/complete` is safe to repeat. |

### Calendar

Student-only, like Tasks and Pomodoro, and separate from both.

| Method and path | What it does |
|---|---|
| `GET /api/calendar/events?from=&to=&q=` | Entries dated between two `YYYY-MM-DD` dates (inclusive, either optional) and/or whose title contains `q`, sorted by date with all-day entries first. Searches return at most 100. |
| `GET /api/calendar/events/{id}` | One entry |
| `POST /api/calendar/events` `{title, type, date, allDay, startTime, endTime}` | Create. `type` is `EVENT` or `REMINDER` (a reminder never notifies). Times are `HH:mm`; `startTime` is needed unless `allDay`, and `endTime` is optional but must be later the same day. |
| `PUT /api/calendar/events/{id}` | Update, with the same body |
| `DELETE /api/calendar/events/{id}` | Delete |

- Dates and times are read and returned in `CALENDAR_TIME_ZONE`. The date and times are always worked out together from the request, so an entry's start time can never fall on a different day than its date.
- All-day entries have no times, so they stay on the same date everywhere.
- Entries can't run past midnight.

### Timetable

Reusable weekly routines grouped into modes (College, Home, Exam Prep …). Student-only, like Tasks, Calendar and Pomodoro.

| Method and path | What it does |
|---|---|
| `GET /api/timetable/modes` | The student's modes in order, each with its activity count and whether it's active |
| `POST /api/timetable/modes` `{name, icon, color}` | Create a mode. `color` is VIOLET, CYAN, EMERALD, AMBER, ROSE or SLATE. The student's first mode becomes active. |
| `PUT /api/timetable/modes/{id}` | Rename, or change the icon or colour |
| `PUT /api/timetable/modes/{id}/activate` | Make this the active mode |
| `DELETE /api/timetable/modes/{id}` | Delete the mode and its whole week |
| `GET /api/timetable/modes/{id}/activities` | That mode's week, by day then start time |
| `POST /api/timetable/modes/{id}/activities` `{dayOfWeek, title, category, startTime, endTime}` | Add an activity. `dayOfWeek` is MONDAY…SUNDAY, times are `HH:mm`, and `category` (CLASS, STUDY, BREAK, MEAL, EXERCISE, SLEEP, TRAVEL, PERSONAL, OTHER) may be null. |
| `PUT /api/timetable/activities/{id}` | Edit, including moving it to another day |
| `DELETE /api/timetable/activities/{id}` | Delete |
| `POST /api/timetable/modes/{id}/copy-day` `{fromDay, toDays}` | Replace each target day with a copy of the source day |

- **Activities can't overlap** within the same mode and day; the answer is 409 `ACTIVITY_OVERLAP` naming the clash. Touching edges are allowed (8:00–9:00 then 9:00–10:00). Editing an activity ignores itself, and the same times are free to reuse on other days and in other modes.
- Times are stored as minutes from midnight and must end the same day.
- **The active mode is one field** in the student's single timetable preference document, written by one update, so simultaneous activations can't leave two modes active. A unique index on `userId` keeps that document single.
- Limits: 20 modes per student, 40 activities per day, names 40 characters, activity titles 100.

### How the Pomodoro timer works

The timer's state is kept on the server:
- The break is timed from when the focus session ended.
- A session whose time ran out while nobody was looking is completed on the next `/current` call.
- A unique index stops a student from having two running sessions, even under simultaneous requests.

## Docker

`backend.Dockerfile` builds the jar and runs it on port 8080.
