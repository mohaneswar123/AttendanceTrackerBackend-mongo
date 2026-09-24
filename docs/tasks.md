# Tasks

A Kanban board for one student: To Do, In Progress, Done. Read [README.md](README.md)
first for the rules every feature shares — in particular, the student always comes from the
token, and another student's task id gives 404.

## Document

**`task_table`** (`model/Task.java`)

| Field | Notes |
|---|---|
| `id` | |
| `userId` | |
| `title` | Required |
| `taskDate` | `YYYY-MM-DD`; the day the task is for |
| `priority` | `LOW`, `MEDIUM`, `HIGH` or null |
| `status` | `TODO`, `IN_PROGRESS`, `DONE` |
| `position` | A `double` that orders the task within its column — see below |
| `createdAt`, `updatedAt` | |

## Endpoints

| Method and path | What it does |
|---|---|
| `GET /api/tasks?from=&to=` | Tasks dated between the two dates, inclusive, ordered by position. Either date may be left out; neither returns every task. |
| `POST /api/tasks` `{title, taskDate, priority}` | Adds to the bottom of To Do |
| `PUT /api/tasks/{id}` `{title, taskDate, priority}` | Edit |
| `PUT /api/tasks/{id}/move` `{status, afterTaskId, beforeTaskId}` | Moves the task to a column, between two neighbours |
| `DELETE /api/tasks/{id}` | Delete |

## Ordering, and why it is a double

A task's place in its column is a number, not an index. Moving a task sets its position to
the midpoint of its two neighbours:

```
position = (after.position + before.position) / 2
```

With no task after it, the position is `after.position + 1`; with none before it,
`before.position - 1`; in an empty column, `0`.

**Nothing else moves.** Renumbering a whole column on every drag would mean writing every
task in it, and two people dragging at once would fight. A midpoint writes one document.

Doubles run out of precision after roughly fifty splits between the same pair, which no
student will reach by dragging; if it ever matters, the fix is to renumber that column
once.

**The neighbours are checked** (`service/TaskService.java`): each must be one of the
caller's own tasks, in the column being moved to, and `after` must really sit before
`before`. Anything else is 400 `INVALID_POSITION` rather than a board that silently
reorders itself. The frontend sends the ids it drew on screen, so a stale board is caught
instead of corrupting the order.

## Dates

`from` and `to` are inclusive and filter on `taskDate`, so the board can ask for "today",
"yesterday", "upcoming" or one chosen day with the same endpoint. A task saved outside the
filter on screen still saves — the frontend says where it went.

## Code

`controller/TaskController.java`, `service/TaskService.java`,
`Repository/TaskRepository.java`. Covered by `TaskServiceTest`.
