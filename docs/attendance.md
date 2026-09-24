# Attendance

Subjects, the records kept against them, and how a percentage is worked out. Read
[README.md](README.md) first for the rules every feature shares.

## Documents

**`subject_table`** (`model/Subject.java`): `id`, `userId`, `name`.

**`attendance_table`** (`model/AttendanceRecord.java`)

| Field | Notes |
|---|---|
| `id` | |
| `userId` | Whose record it is |
| `subjectId` | Which subject |
| `status` | `Present`, `Absent` or `No Class` |
| `date` | `YYYY-MM-DD` |
| `classNumber` | **The length of the class in hours**, 1 to 3. The name is historical; it is a duration, not an index. |

## Endpoints

Unlike the other features, these take a `userId` in the path or query, and allow either
that student or an admin. They also work while a subscription is inactive, so nobody is
locked out of records they already have.

| Method and path | What it does |
|---|---|
| `POST /api/subjects/add?userId&name` | Adds a subject |
| `GET /api/subjects/user/{userId}` | That student's subjects |
| `DELETE /api/subjects/{subjectId}/user/{userId}` | Deletes the subject **and its attendance records** |
| `POST /api/attendance/add?userId&subjectId&status&date&classNumber` | Records one class |
| `GET /api/attendance/user/{userId}` | Every record, with its subject filled in |
| `PUT /api/attendance/{id}` `{status?, date?, classNumber?}` | Changes only the fields present |
| `DELETE /api/attendance/{id}` | Deletes one record |
| `DELETE /api/reset/user/{userId}` | Deletes that student's subjects and attendance records — **and nothing else** |

`status` must be one of the three exact strings; `date` is `YYYY-MM-DD`; `classNumber` is 1,
2 or 3.

## How the percentage is worked out

The percentage is **weighted by class length**, so a 2-hour class counts twice as much as a
1-hour one:

```
attended hours = sum of classNumber over records with status Present
counted hours  = sum of classNumber over records with status Present or Absent
percentage     = round(attended hours / counted hours * 100)
```

**`No Class` never counts** — neither as attended nor as missed — so a cancelled class
cannot drag a percentage down. With no counted hours the percentage is 0.

The arithmetic lives in the frontend, which already holds every record; the server stores
the facts.

## The reset

`DELETE /api/reset/user/{userId}` (`service/ResetService.java`) is the "start the term
again" button. It clears subjects and attendance records only. Tasks, calendar entries, the
timetable and focus history are deliberately untouched — the frontend says so next to the
button, and changing that here means changing that wording too.

## Code

`controller/SubjectController.java`, `controller/AttendanceRecordController.java`,
`controller/ResetController.java`, and their services.
