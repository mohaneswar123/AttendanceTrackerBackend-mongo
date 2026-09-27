# Accounts

Registration, signing in, how long a token lasts, what a subscription gates, and what an
admin can do. Read [README.md](README.md) first for the rules every feature shares.

## Documents

**`user_table`** (`model/User.java`)

| Field | Notes |
|---|---|
| `id` | |
| `username` | The name shown in the app. **Not unique** — two students may share a name. |
| `email` | Unique. This is what you log in with. |
| `password` | A BCrypt hash. See *Passwords* below. |
| `active` | Whether the subscription is running |
| `paidTill` | The date access is allowed until |

**`admin_table`** (`model/Admin.java`) holds `id`, `email`, `password`. Admins are created
directly in the database; there is no endpoint for it.

## Endpoints

| Method and path | Who | What it does |
|---|---|---|
| `POST /api/users/register` `{username, email, password}` | Anyone | Creates the account. 409 `EMAIL_EXISTS` if the email is taken. |
| `POST /api/users/login` `{email, password}` | Anyone | `{token, user}`. 401 `INVALID_CREDENTIALS` on a wrong email or password — the message doesn't say which. |
| `POST /api/admin/login` `{email, password}` | Anyone | The same, with an admin token |
| `GET /api/users/me` | Signed-in student | The caller's own account |
| `POST /api/users/change-password` `{oldPassword, newPassword}` | Signed-in student | Checks the old password first |
| `GET /api/users/{id}` | That student, or an admin | |
| `PUT /api/users/{id}/email` `{email}` | That student, or an admin | 409 `EMAIL_EXISTS` |
| `GET /api/users` | Admin | Every account |
| `DELETE /api/users/{id}` | Admin | Deletes the account **and everything it owns** (see below) |
| `PUT /api/users/admin/activate/{id}?days=N` | Admin | Access for N days **counted from today**, whatever the account had before |
| `PUT /api/users/admin/extend/{id}?days=N` | Admin | **Adds** N days to what is left (see below) |
| `PUT /api/users/admin/deactivate/{id}` | Admin | Ends access now, and clears `paidTill` |
| `PUT /api/users/admin/{id}/password` `{password}` | Admin | Sets a new password for a student who has forgotten theirs |
| `GET /api/users/admin/activity?limit=&userId=` | Admin | The audit log, newest first (see below) |

## Activate or extend

Both grant access; they differ in where the days are counted from.

- **Activate** sets `paidTill` to today + N. Use it to start someone, or to correct a
  date that is wrong.
- **Extend** adds N days to `paidTill` when that date is still in the future, and counts
  from today when it has passed or was never set. Renewing a week early therefore keeps
  the week that was left instead of throwing it away.

Deactivating clears `paidTill` as well as the flag, so a switched-off account can never
read as paid up.

## The audit log

Every admin action on an account is recorded in `admin_action_table`
(`model/AdminAction.java`) once the change has succeeded: who did it, what they did, to
whom, and a line of detail such as `30 days, paid till 30 Oct 2026`.

Two decisions worth knowing:

- **The target's email is stored as text**, not looked up through `targetUserId`. The
  account may be deleted, and naming who it was is exactly what the log is for.
- **Entries are never edited or removed**, including when the account is deleted. There is
  no endpoint that writes to the log directly; it is only written as a side effect of a
  successful action.

`GET /api/users/admin/activity` returns the most recent entries, at most 200. Pass
`userId` for one account's history.

## Tokens

Signed with HS256 by `security/TokenService.java`, using `JWT_SECRET`.

- A student's token lasts `app.jwt.user-token-hours` (30 days).
- An admin's lasts `app.jwt.admin-token-hours` (12 hours).
- The `role` claim becomes `ROLE_USER` or `ROLE_ADMIN` in Spring Security.
- **If `JWT_SECRET` is unset a random key is generated at startup**, so every restart signs
  everyone out. Set it in production.

## Passwords

`security/PasswordHasher.java` stores BCrypt hashes.

Accounts created before hashing existed held their password in plain text. Two things keep
those working:

- `PasswordHasher.matches` accepts a stored value that isn't a BCrypt hash by comparing it
  directly, then the caller re-saves it hashed.
- `security/LegacyPasswordMigration.java` runs once at startup and hashes any that are
  still plain, with a conditional update so it can't overwrite a password changed in the
  meantime.

Both can be deleted once no plain-text password remains.

## Guessing at the admin password

There is one admin account and one password, and nothing else between a guess and every
student's data, so `POST /api/admin/login` counts failures per email
(`security/LoginThrottle.java`). After 5 wrong tries it answers 429 `TOO_MANY_ATTEMPTS`
for 15 minutes and says how long is left. A correct password clears the count, so
mistyping twice and then getting it right is never held up, and one account's failures
never lock another out.

**The counts are in memory.** That needs no extra service, and the worst a restart can do
is forgive some failed attempts — but it also means the limit is per instance. If the API
is ever run on more than one, this has to move to shared storage for the limit to mean
anything. Student login is not throttled.

## Subscriptions

`AccessGuard.requireActiveStudent` refuses a student whose subscription has lapsed with 403
and either `SUBSCRIPTION_INACTIVE` (never activated, or deactivated) or
`SUBSCRIPTION_EXPIRED` (`paidTill` has passed). The frontend sends those students to
`/inactive`.

This gate covers Tasks, Pomodoro, Calendar and Timetable. Reading and writing attendance is
allowed while inactive, so nobody is locked out of the records they already have.

## Deleting an account

`UserService.deleteUser` removes, in one call: the account, its subjects and attendance
records, its tasks, its Pomodoro sessions, its calendar entries, and its timetable modes,
activities and preference. Anything added later must be deleted here too — `UserServiceTest`
checks each repository is called.

## Code

`controller/UserController.java`, `controller/AdminController.java`,
`service/UserService.java`, `service/AdminService.java`, `security/`.
