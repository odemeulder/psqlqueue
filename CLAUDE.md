# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`psqlqueue` is a Spring Boot **library** (not a runnable application) that provides a PostgreSQL-backed task queue with LISTEN/NOTIFY-driven processing. It is published to Maven local and consumed by other Spring Boot projects.

Publication coordinates: `us.demeulder:psqlqueue:0.0.1-SNAPSHOT`

## Commands

```bash
./gradlew build           # compile + test + package
./gradlew test            # run tests only
./gradlew publishToMavenLocal   # publish to ~/.m2 for local consumption
```

There is no `bootJar` or `run` task — this is a library project without the Spring Boot application plugin.

## Architecture

All source lives in the single package `us.demeulder.psqlqueue` (no subpackages).

### Public API surface

| Class / Interface | Role |
|---|---|
| `TaskQueueService` | Enqueue tasks (`publishEvent`) and query queue status |
| `TaskHandler` | Interface consumers implement to handle a specific `taskType` |
| `QueueStatusSummary` | DTO returned by `getSummary()` / `GET /queue/status` |
| `PsqlQueueConfiguration` | Spring Boot `@AutoConfiguration` entry point — auto-registers `TaskQueueService` |

### Internal components

| Class | Role |
|---|---|
| `TaskQueueRepository` | All JDBC operations: insert, claim (`FOR UPDATE SKIP LOCKED`), complete, retry, cleanup |
| `PostgresTaskQueueListener` | Preferred listener — uses `LISTEN/NOTIFY` on a dedicated channel; runs on a single daemon thread; starts only when datasource is PostgreSQL |
| `TaskQueueListener` | Fallback polling listener — `@Scheduled` every 5 s, used when datasource is not PostgreSQL (e.g., H2 in tests) |
| `TaskCleanupJob` | Scheduled jobs: deletes completed/failed tasks older than 7 days (hourly), reclaims stale `IN_PROGRESS` tasks after 10 minutes (every 5 min) |
| `QueueStatusController` | REST endpoints: `GET /queue/status`, `POST /queue/tasks` |
| `DefaultTaskProcessorHandler` | Built-in `TaskHandler` for `taskType = "default"` — delegates to `LoggingTaskProcessor` |

### Task lifecycle

```
PENDING → IN_PROGRESS → COMPLETED
                      → PENDING  (retry, with exponential backoff up to 300 s)
                      → FAILED   (when attempts >= maxAttempts)
```

`claimNextTask()` uses `FOR UPDATE SKIP LOCKED` on PostgreSQL, enabling safe concurrent consumers.

### LISTEN/NOTIFY channel

The Postgres notification channel defaults to `task_queue_channel` and is configurable:

```properties
psqlqueue.postgres.channel=my_custom_channel
```

Both `TaskQueueRepository` (notifies on insert and retry) and `PostgresTaskQueueListener` (subscribes) read this property.

### Auto-configuration

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` registers `PsqlQueueConfiguration` so consumer applications get the beans without any `@Import`.

## Adding a new task type

Implement `TaskHandler` and register it as a Spring `@Component` — the listener routes by `taskType`. Only one handler per type is allowed (enforced at startup).

```kotlin
@Component
class MyHandler : TaskHandler {
    override val taskType = "my-type"
    override fun handle(payload: String) { /* ... */ }
}
```

## Database migrations

Flyway migrations are in `src/main/resources/db/migration/`. Consumer applications must apply them (Flyway auto-applies on startup if configured, or apply manually with `psql`).

## Testing

Tests use H2 in-memory DB (`MODE=PostgreSQL`) and [MockK](https://mockk.io/) for mocking. The `PostgresTaskQueueListener` is exercised via reflection on the private `processPendingTasks()` method since the listener only starts on a real Postgres datasource.

Test config: `src/test/resources/application.properties`

> **Note:** Source files were recently moved from the `us.demeulder.psqlqueue.queue` subpackage to `us.demeulder.psqlqueue`. The corresponding test sources in `src/test/kotlin/` have not yet been migrated — the working test source is in `bin/test/` (the old path). When adding tests, place them in `src/test/kotlin/us/demeulder/psqlqueue/`.
