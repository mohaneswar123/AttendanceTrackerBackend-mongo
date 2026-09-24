# Calendar

Dated events and reminders: exams, deadlines, anything that happens once. Read
[README.md](README.md) first for the rules every feature shares.

The calendar holds things with a **date**. Repeating routines belong in
[timetable.md](timetable.md).

## Document

**`calendar_event_table`** (`model/CalendarEvent.java`)

| Field | Notes |
|---|---|
| `id`, `userId` | |
| `title` | Required, at most 200 characters |
| `type` | `EVENT` or `REMINDER` (`model/CalendarEventType.java`) |
| `eventDate` | `YYYY-MM-DD` in the calendar's time zone |
| `allDay` | |
| `startAt`, `endAt` | Instants, or null for an all-day entry |
| `createdAt`, `updatedAt` | |

A reminder is a note on the calendar. **Nothing is sent** — no email, no push. The frontend
says so next to the choice, so don't add notifications without changing that wording.

## Endpoints

| Method and path | What it does |
|---|---|
| `GET /api/calendar/events?from=&to=&q=` | Entries between two dates (inclusive, either optional) and/or whose title contains `q`, sorted by date with all-day entries first. A search returns at most 100. |
| `GET /api/calendar/events/{id}` | One entry |
| `POST /api/calendar/events` `{title, type, date, allDay, startTime, endTime}` | Create |
| `PUT /api/calendar/events/{id}` | Update, same body |
| `DELETE /api/calendar/events/{id}` | Delete |

`startTime` is required unless `allDay`; `endTime` is optional but must be later the same
day.

## Time zone

Set by `CALENDAR_TIME_ZONE` (`app.calendar.time-zone`), `Asia/Kolkata` by default. **Every
student sees the same zone** — this is a study app used in one place, not a scheduler
across time zones.

Three decisions keep the dates honest:

- **The date and the times are worked out together.** `eventDate` and `startAt` are derived
  from the same request in one step, so an entry's start can never land on a different day
  than its date. Storing an instant and deriving the date from it later is what causes
  entries to jump a day at the zone boundary.
- **All-day entries have no times at all.** `startAt` and `endAt` stay null, so they sit on
  their date everywhere, whatever zone the reader's device is in.
- **Entries can't run past midnight.** An end at or before the start is refused with
  "End time must be later the same day. Events can't run past midnight." Something that
  spans two days is two entries — which keeps every list, grid and count able to treat one
  entry as one day.

Responses carry `date`, `startTime` and `endTime` already converted to that zone as strings,
so the frontend does no time-zone arithmetic.

## Searching

`q` matches anywhere in the title, ignoring case, and is capped at 100 results. The cap
exists so a one-letter search can't try to return a student's whole history; the frontend
says when it has hit the cap.

## Code

`controller/CalendarEventController.java`, `service/CalendarEventService.java`,
`dto/CalendarEventRequest.java`, `dto/CalendarEventResponse.java`. Covered by
`CalendarEventServiceTest`.
