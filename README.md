# psqlqueue

psqlqueue is a small Kotlin library providing a PostgreSQL-backed task queue and lightweight task processing primitives.

This repository contains a simple task queue implementation using PostgreSQL for persistence and Kotlin (Gradle) for the application and tests.

**Status:** ready for development and agent automation.

## Quick summary

- **Language:** Kotlin
- **Build:** Gradle
- **Database:** PostgreSQL (migrations included)

## Contents

- [Source code](src/main/kotlin/us/demeulder/psqlqueue/)
- [Tests and test resources](src/test/)
- Database migrations: `src/main/resources/db/migration/`

## Usage

How to import the library?

Add the project as a Gradle or Maven dependency. The current publication coordinates are:

Gradle

```kotlin
implementation("us.demeulder:psqlqueue:0.0.1-SNAPSHOT")
```

Maven

```xml
<dependency>
  <groupId>us.demeulder</groupId>
  <artifactId>psqlqueue</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

How to publish an event?

Inject `TaskQueueService` into your Spring component and call `publishEvent`.

```kotlin
@Service
class OrderService(
    private val taskQueueService: TaskQueueService
) {
    fun publishOrderEvent(orderId: String) {
        val payload = "{\"orderId\": \"$orderId\"}" // JSON or plain text
        val taskId = taskQueueService.publishEvent(
            payload = payload,
            taskType = "order-created",
            maxAttempts = 5
        )
        println("Published task $taskId")
    }
}
```

```java
@Service
public class OrderService {
    private final TaskQueueService taskQueueService;

    public OrderService(TaskQueueService taskQueueService) {
        this.taskQueueService = taskQueueService;
    }

    public void publishOrderEvent(String orderId) {
        String payload = String.format("{\"orderId\": \"%s\"}", orderId);
        UUID taskId = taskQueueService.publishEvent(
            payload,
            "order-created",
            5
        );
        System.out.println("Published task " + taskId);
    }
}
```

How to build a handler to consume an event?

Implement `TaskHandler` and register it as a Spring bean. The queue listener will route tasks by `taskType`.

```kotlin
@Component
class OrderCreatedTaskHandler : TaskHandler {
    override val taskType: String = "order-created"

    override fun handle(payload: String) {
        // Deserialize payload and process the task
        println("Handling order-created task: $payload")
    }
}
```

```java
@Component
public class OrderCreatedTaskHandler implements TaskHandler {
    @Override
    public String getTaskType() {
        return "order-created";
    }

    @Override
    public void handle(String payload) {
        // Deserialize payload and process the task
        System.out.println("Handling order-created task: " + payload);
    }
}
```

A `PostgresTaskQueueListener` is included in the application and starts automatically when the Spring context is ready. It listens for Postgres notifications, claims pending tasks, and dispatches them to the matching handler.

## Quick Start

1. Ensure PostgreSQL is running and reachable.
2. Create a database (example: `psqlqueue_dev`) and apply migrations found in `src/main/resources/db/migration/`.
   - You can apply migrations manually with `psql`:

```bash
psql -h <host> -U <user> -d psqlqueue_dev -f src/main/resources/db/migration/V1__Create_task_queue_table.sql
psql -h <host> -U <user> -d psqlqueue_dev -f src/main/resources/db/migration/V2__Add_task_type_and_started_at_to_task_queue.sql
```

3. Build the project:

```bash
./gradlew build
```

4. Run the app (if an application plugin / run task exists in the Gradle build):

```bash
./gradlew run
```

## Configuration

Configuration is read from `application.properties` in `src/main/resources` (and `test/resources` for tests). Provide a database URL, username, and password via properties or environment variables as your application expects.

## Key files

- Main queue code: [src/main/kotlin/us/demeulder/psqlqueue/queue](src/main/kotlin/us/demeulder/psqlqueue/queue/)
- Example/service entrypoint: [src/main/kotlin/us/demeulder/psqlqueue/queue/TaskQueueService.kt](src/main/kotlin/us/demeulder/psqlqueue/queue/TaskQueueService.kt)
- Migrations: [src/main/resources/db/migration](src/main/resources/db/migration/)
- Tests: [src/test/kotlin](src/test/kotlin/)

## Development commands (for humans and agents)

- Build: `./gradlew build`
- Run tests: `./gradlew test`
- Run with logging: `./gradlew run --info`

When automating, prefer the Gradle wrapper (`./gradlew`) to ensure consistent tooling.

## Agent-friendly checklist

If an agent is modifying or inspecting this repo, the following checklist makes tasks straightforward:

1. Run the test suite:

```bash
./gradlew test
```

2. Run a static build to ensure compilation:

```bash
./gradlew build
```

3. If running migrations from the repository is required, apply SQL directly from `src/main/resources/db/migration/` using `psql` (see Quick Start).

4. Key places to inspect for task queue behavior:
   - `src/main/kotlin/us/demeulder/psqlqueue/queue/TaskQueueService.kt` — main service
   - `src/main/kotlin/us/demeulder/psqlqueue/queue/TaskQueueRepository.kt` — DB access
   - `src/main/kotlin/us/demeulder/psqlqueue/queue/TaskProcessor.kt` — processing loop

5. When adding tests, mirror production config in `src/test/resources/application.properties` and use an isolated test database.

6. Suggested PR checks for CI pipeline (automated by an agent):
   - `./gradlew build` passes
   - `./gradlew test` passes
   - Database migration SQL is syntactically valid (optionally lint with psql)

