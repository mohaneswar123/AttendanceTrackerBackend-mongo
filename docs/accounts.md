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
| `subscriptionEnd` | When it lapses |

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
| `PUT /api/users/admin/activate/{id}?days=N` | Admin | Starts or extends the subscription by N days |
| `PUT /api/users/admin/deactivate/{id}` | Admin | Ends it now |
| `PUT /api/users/admin/{id}/password` `{password}` | Admin | Sets a new password for a student who has forgotten theirs |

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

## Subscriptions

`AccessGuard.requireActiveStudent` refuses a student whose subscription has lapsed with 403
and either `SUBSCRIPTION_INACTIVE` (never activated, or deactivated) or
`SUBSCRIPTION_EXPIRED` (`subscriptionEnd` has passed). The frontend sends those students to
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
