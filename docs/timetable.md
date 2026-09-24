# Timetable

Reusable weekly routines grouped into **modes** — College, Home, Exam Prep — each with its
own Monday-to-Sunday plan. Read [README.md](README.md) first for the rules every feature
shares.

The timetable holds what a typical week looks like. One-off dated things belong in
[calendar.md](calendar.md).

## Documents

**`timetable_mode_table`** (`model/TimetableMode.java`): `id`, `userId`, `name`, `icon`,
`color` (`model/TimetableColor.java`: VIOLET, CYAN, EMERALD, AMBER, ROSE, SLATE),
`position`, `createdAt`, `updatedAt`.

There is deliberately **no `active` flag on a mode** — see *One active mode* below.

**`timetable_preference_table`** (`model/TimetablePreference.java`): one document per
student — `id`, `userId`, `activeModeId`, `updatedAt`.

**`timetable_activity_table`** (`model/TimetableActivity.java`)

| Field | Notes |
|---|---|
| `id`, `userId`, `modeId` | |
| `dayOfWeek` | `java.time.DayOfWeek` — MONDAY…SUNDAY |
| `title` | Required, at most 100 characters |
| `category` | `model/ActivityCategory.java` (CLASS, STUDY, BREAK, MEAL, EXERCISE, SLEEP, TRAVEL, PERSONAL, OTHER) or null |
| `startMinutes`, `endMinutes` | Minutes from midnight |
| `createdAt`, `updatedAt` | |

Times are stored as **minutes from midnight**, not strings. One number is the single source
of truth, so ordering and overlap comparisons can't disagree with what is displayed.
Requests send `"HH:mm"`; responses carry both the strings and the minute values, so the
frontend does no time arithmetic of its own.

## Endpoints

| Method and path | What it does |
|---|---|
| `GET /api/timetable/modes` | The student's modes in order, each with its activity count and whether it is active |
| `POST /api/timetable/modes` `{name, icon, color}` | Create. The student's first mode becomes the active one. |
| `PUT /api/timetable/modes/{id}` | Rename, or change the icon or colour |
| `PUT /api/timetable/modes/{id}/activate` | Make this the active mode |
| `DELETE /api/timetable/modes/{id}` | Delete the mode and its whole week |
| `GET /api/timetable/modes/{id}/activities` | That mode's week, by day then start time |
| `POST /api/timetable/modes/{id}/activities` `{dayOfWeek, title, category, startTime, endTime}` | Add an activity |
| `PUT /api/timetable/activities/{id}` | Edit, including moving it to another day |
| `DELETE /api/timetable/activities/{id}` | Delete |
| `POST /api/timetable/modes/{id}/copy-day` `{fromDay, toDays}` | Replace each target day with a copy of the source day |

The activate and delete calls return the **full list of modes** with their new state, so the
frontend never has to guess which one is active now.

## Activities can't overlap

Within the same mode and the same day, two activities clash when one starts before the
other ends and ends after it starts:

```
a.start < b.end && a.end > b.start
```

The answer is 409 `ACTIVITY_OVERLAP` with a message naming the clash, for example
`Overlaps "College" (8:00 AM – 9:00 AM)`.

- **Touching edges are fine.** 8:00–9:00 and 9:00–10:00 both fit; the comparison is strict.
- **Editing ignores the activity being edited**, so saving one unchanged always works.
- **The same times are free on another day, or in another mode.** The check is scoped to one
  mode and one day.
- **Copying a day cannot create a clash**, because the source day is already valid and it
  replaces the target day rather than merging into it.

**The server is the authority.** The form checks the same rule so a student sees the problem
before saving, but a request that skips the page entirely is refused just the same. That is
worth keeping in mind when changing either side: the frontend's check is a courtesy, this
one is the rule.

## Activities stay within one day

`endTime` must be later than `startTime` on the same day; anything else is refused with
"End time must be later the same day. Activities can't run past midnight." Sleep that
crosses midnight is entered as two activities. Keeping every activity inside one day is
what lets a day be read, ordered and overlap-checked as a simple list.

## One active mode, by design rather than by timing

Which mode is active is **one field**, `activeModeId`, in the student's single preference
document. Activating is one atomic upsert filtered by `userId`:

```java
mongoTemplate.upsert(
    Query.query(Criteria.where("userId").is(userId)),
    new Update().set("activeModeId", modeId).set("updatedAt", clock.instant()),
    TimetablePreference.class);
```

Nothing has to be cleared first, so there is no window in which two modes look active. Two
activations arriving together simply leave whichever wrote last — a single field can only
hold one value. A unique index on `userId`, created at startup by
`config/TimetableIndexes.java` (`one_timetable_preference_per_user`), means even a race to
create that document leaves one.

This is why a mode has no `active` flag of its own: each mode's `active` in responses is
derived by comparing its id with that field, so no second copy of the truth exists to drift.

The alternative — clearing the old mode then setting the new one — needs a multi-document
transaction, which needs a replica set and careful locking to give the same guarantee.

Around the edges:

- Creating a student's first mode sets the field only when it is empty.
- Deleting the active mode points it at the first remaining mode, or clears it when none are
  left.
- If the field ever names a mode that has been deleted, reading falls back to the first mode
  by position, so the app is never left with nothing selected.

## Copying a day

`copy-day` **replaces** each target day: the target's activities are deleted, then copies of
the source day's are saved. It does not merge, which is why it can't produce a clash. The
frontend warns which days already have something before sending.

## Limits

| Limit | Value |
|---|---|
| Modes per student | 20 |
| Activities per day | 40 |
| Mode name | 40 characters |
| Mode icon | 4 characters |
| Activity title | 100 characters |

Each has its own message rather than a generic refusal.

## Code

`controller/TimetableController.java`, `service/TimetableService.java`,
`config/TimetableIndexes.java`, `util/Times.java`. Covered by `TimetableServiceTest`
(20 tests) and `SecurityRulesTest`.
