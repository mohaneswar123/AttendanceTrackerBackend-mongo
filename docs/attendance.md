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
| `POST /api/subjects/add?userId&name&color` | Adds a subject; `color` is optional |
| `GET /api/subjects/user/{userId}` | That student's subjects |
| `PUT /api/subjects/{subjectId}/user/{userId}` `{name, color}` | Renames a subject, recolours it, or both |
| `DELETE /api/subjects/{subjectId}/user/{userId}` | Deletes the subject **and its attendance records** |
| `POST /api/attendance/add?userId&subjectId&status&date&classNumber` | Records one class |
| `GET /api/attendance/user/{userId}` | Every record, with its subject filled in |
| `PUT /api/attendance/{id}` `{status?, date?, classNumber?}` | Changes only the fields present |
| `DELETE /api/attendance/{id}` | Deletes one record |
| `DELETE /api/reset/user/{userId}` | Deletes that student's subjects and attendance records — **and nothing else** |


## Subjects have a name and a colour

`color` is one of the six `TimetableColor` values the timetable modes and habits use, or
null for a subject saved before colours could be chosen. A null colour is not a missing
value to be filled in: the page works one out from the name, which is what it did for
every subject before this field existed.

Choosing one matters for a reason that is easy to miss — a colour derived from the name
**changes when the subject is renamed**. "Maths" and "Mathematics" are different colours,
so a tidy-up would silently repaint a subject the student had learned to recognise.

Two rules the service keeps:

- **A name is a student's own.** Two subjects with the same name cannot be told apart on
  any screen, and the attendance recorded against them cannot be separated afterwards, so
  a duplicate is refused (case-insensitively) on both add and rename.
- **A rename rewrites nothing.** Records hold their subject by id, so history and reports
  follow a rename on their own. There is a test that fails if anyone ever "fixes" renaming
  by rewriting the records.

`PUT` leaves out what it is not given: a null `name` keeps the name, and a null `color`
keeps the colour, so renaming does not reset a colour the student picked.

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
