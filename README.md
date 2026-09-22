# Attendance In Hand (backend)

REST API for the Attendance In Hand app, built with Spring Boot 3.5, Java 17 and MongoDB. The frontend lives in the separate `AttendanceTrackerfront` repository.

## Running locally

```
./mvnw spring-boot:run
```

The API listens on http://localhost:8080. Run the tests with `./mvnw test`. They don't need a database.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | random at startup | Key that signs login tokens, at least 32 characters. **Set it in production.** Without it, every restart signs everyone out. |
| `CORS_ALLOWED_ORIGINS` | `https://attendanceinhand.netlify.app,http://localhost:5173` | Comma-separated frontend origins allowed to call the API |
| `PORT` | `8080` | HTTP port (Render sets this) |

Token lifetimes are set in `application.properties`: `app.jwt.user-token-hours` (30 days) and `app.jwt.admin-token-hours` (12 hours).

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
| `DELETE /api/reset/user/{userId}` | That student or an admin |

`status` is `Present`, `Absent` or `No Class`, `date` is `YYYY-MM-DD`, and `classNumber` is the class length in hours (1 to 3).

## Docker

`backend.Dockerfile` builds the jar and runs it on port 8080.
