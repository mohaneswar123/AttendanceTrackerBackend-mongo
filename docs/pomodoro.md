# Pomodoro

A focus timer that runs on the server, not in the browser. Read [README.md](README.md)
first for the rules every feature shares.

It is **not connected to Tasks**, on purpose: focusing and planning are separate, and a
timer that belongs to a task cannot be started without one.

## Document

**`pomodoro_table`** (`model/PomodoroSession.java`)

| Field | Notes |
|---|---|
| `id`, `userId` | |
| `status` | `RUNNING`, `PAUSED`, `COMPLETED`, `STOPPED` (`model/PomodoroStatus.java`) |
| `focusSeconds`, `breakSeconds` | The lengths this session was started with |
| `startedAt` | When the current run began |
| `remainingSeconds` | Only meaningful while paused |
| `completedAt` | When the focus ended; the break is timed from here |
| `active` | `true` while running or paused, **absent otherwise** — see the index below |

## Endpoints

Every one returns the timer's state:

```json
{ "phase": "IDLE | FOCUS | BREAK", "sessionId": ..., "remainingSeconds": ..., "totalSeconds": ..., "paused": false }
```

| Method and path | What it does |
|---|---|
| `GET /api/pomodoro/current` | The state now. Safe to poll. |
| `POST /api/pomodoro/start` `{focusMinutes, breakMinutes}` | Starts a focus session. Focus 1–120 minutes, break 1–30. Leave either out, or send no body, for the defaults in `application.properties` (25 and 5). 409 `FOCUS_ALREADY_RUNNING` if one is already going. |
| `PUT /api/pomodoro/{id}/pause` | |
| `PUT /api/pomodoro/{id}/resume` | |
| `PUT /api/pomodoro/{id}/stop` | The Reset button |
| `PUT /api/pomodoro/{id}/complete` | Ends the focus and starts the break |
| `PUT /api/pomodoro/{id}/skip-break` | Ends the break early |

## Why the server holds the timer

A timer in the browser stops when the tab is closed, drifts when the device sleeps, and
disagrees between two open tabs. Here the server stores when the session started and works
out what is left on every request, so the count survives a refresh, a closed tab and a
second device.

Three things follow from that:

- **A session whose time ran out while nobody was looking is completed on the next
  `/current` call.** Nothing is scheduled; the state is worked out from the clock.
- **The break is timed from `completedAt`**, the moment the focus ended, not from when the
  student came back. A break cannot be extended by looking away.
- **A student can only have one session running.** A unique partial index enforces it
  rather than a check-then-write:

  ```
  one_active_session_per_user on { userId: 1 }, unique, partialFilterExpression { active: true }
  ```

  created at startup by `config/PomodoroIndexes.java`. A second simultaneous `start` is
  refused by the database with a duplicate key, which the service turns into 409
  `FOCUS_ALREADY_RUNNING`. Finishing a session removes the `active` field entirely, so
  completed sessions don't collide.

- **`/complete` is safe to repeat.** It is a conditional update — "set completed, where the
  status is still running" — so a double tap, or the browser and the server both noticing
  the end at once, completes once and the second call is a no-op.

## Code

`controller/PomodoroController.java`, `service/PomodoroService.java`,
`config/PomodoroIndexes.java`. Covered by `PomodoroServiceTest`.
