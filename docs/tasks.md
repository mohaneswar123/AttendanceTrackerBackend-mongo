# Tasks

A to-do list for one student: one list, ticked off or not. Read
[README.md](README.md) first for the rules every feature shares — in particular, the
student always comes from the token, and another student's task id gives 404.

## Document

**`task_table`** (`model/Task.java`)

| Field | Notes |
|---|---|
| `id` | |
| `userId` | |
| `title` | Required, at most 200 characters |
| `taskDate` | `YYYY-MM-DD`; the day the task is for |
| `status` | `TODO` or `DONE` |
| `important` | Starred. Appears under Important. |
| `note` | Free text, at most 1000 characters, or null |
| `steps` | Up to 20 embedded `TaskStep`s (`{id, title, done}`) |
| `position` | A `double` that orders the task within the list — see below |
| `createdAt`, `updatedAt` | |
| `completedAt` | Set while the task is done, cleared when it is put back |
| `priority` | Dead. See *What the board left behind*. |

### Steps

Steps live inside the task rather than in their own collection: they are only ever read
and written together with it, and a task with more than a handful of steps wants to be two
tasks. A step with no `id` in the request has just been typed and is given one; a step the
request leaves out has been deleted. So `PUT /api/tasks/{id}` replaces the steps as a set,
which makes adding, renaming, ticking and removing steps one request instead of four.

## Endpoints

| Method and path | What it does |
|---|---|
| `GET /api/tasks?from=&to=` | Tasks dated between the two dates, inclusive, ordered by position. Either date may be left out; neither returns every task. |
| `POST /api/tasks` `{title, taskDate, important, note, steps}` | Adds to the bottom of the list |
| `PUT /api/tasks/{id}` `{title, taskDate, important, note, steps}` | Edit, steps included |
| `PUT /api/tasks/{id}/done` | Tick it off |
| `DELETE /api/tasks/{id}/done` | Put it back |
| `PUT /api/tasks/{id}/move` `{afterTaskId, beforeTaskId}` | Moves the task between two neighbours |
| `DELETE /api/tasks/{id}` | Delete |

Ticking a task off is its own call, with no body, because it is one tap on a row and
should not need the rest of the task sent back to the server to be correct.

### The frontend asks for every task

The date filter stays on `GET /api/tasks`, but the app does not use it: it fetches the
whole list and slices it on the client. That is deliberate. The Today screen has to show
everything from earlier days that was never finished, and asking the server for "today"
was exactly what made that work disappear overnight.

## Ordering, and why it is a double

A task's place in the list is a number, not an index. Moving a task sets its position to
the midpoint of its two neighbours:

```
position = (after.position + before.position) / 2
```

With no task after it, the position is `after.position + 1024`; with none before it,
`before.position - 1024`; in an empty list, `1024`.

**Nothing else moves.** Renumbering the whole list on every drag would mean writing every
task in it. A midpoint writes one document. When the gap between two neighbours falls
below `MIN_GAP`, and only then, the list is spread out again in one pass and the midpoint
is taken a second time.

**The neighbours are checked** (`service/TaskService.java`): each must be one of the
caller's own tasks, neither may be the task being moved, and `after` must really sit
before `before`. Anything else is 400 `INVALID_POSITION` rather than a list that silently
reorders itself. The frontend sends the ids it drew on screen, so a stale list is caught
instead of corrupting the order.

The neighbours are looked up in the student's whole list, not in the group on screen, so a
task hidden by the open view keeps its place.

## Dates

`from` and `to` are inclusive and filter on `taskDate`. A task saved outside the view the
student is looking at still saves — the frontend says where it went.

## What the board left behind

This was a Kanban board: three columns, and a priority of `LOW`, `MEDIUM` or `HIGH`.

Both are kept in the model so that tasks saved before the change still load:

- **`TaskStatus.IN_PROGRESS`** is `@Deprecated` and nothing sets it. A task still in that
  column reads back as not done, which is what it was.
- **`priority`** is never written. `Task.isImportant()` falls back to
  `priority == HIGH` when `important` is null, so a task that was marked High before the
  star existed comes back starred.

Neither appears in a request or a response body the frontend writes to. They are read-only
history, and dropping them would mean a migration for no gain.

## Code

`controller/TaskController.java`, `service/TaskService.java`,
`Repository/TaskRepository.java`. Covered by `TaskServiceTest`.
