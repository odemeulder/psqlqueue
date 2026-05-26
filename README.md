# psqlqueue

A Spring Boot library that provides a PostgreSQL-backed task queue with LISTEN/NOTIFY-driven processing.

- **Language:** Kotlin
- **Build:** Gradle
- **Database:** PostgreSQL (Flyway migrations included)
- **Publication coordinates:** `us.demeulder:psqlqueue:0.0.1-SNAPSHOT`

---

## 1. Add the dependency

**Gradle**

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("us.demeulder:psqlqueue:0.0.1-SNAPSHOT")
}
```

**Maven**

```xml
<dependency>
  <groupId>us.demeulder</groupId>
  <artifactId>psqlqueue</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Publish the library to your local Maven repository first if you haven't already:

```bash
cd psqlqueue
./gradlew publishToMavenLocal
```

---

## 2. Configure datasource and Flyway

Add a PostgreSQL datasource in your `application.properties`. Tell Flyway to pick up the library's migrations alongside your own:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb?currentSchema=queue
spring.datasource.username=db_user
spring.datasource.password=secret

# Include both your app migrations and the library's migrations
spring.flyway.locations=classpath:db/migration/app,classpath:db/migration
```

The library ships its own Flyway migrations on `classpath:db/migration`. Flyway will create the `task_queue` table automatically on startup.

---

## 3. Publish an event

Inject `TaskQueueService` into any Spring component and call `publishEvent(payload, taskType, maxAttempts)`:

**Java**

```java
import us.demeulder.psqlqueue.TaskQueueService;

@Component
public class OrderService {

    @Autowired
    TaskQueueService queueService;

    public void processOrder(Order order) {
        queueService.publishEvent("Order 123", "order.process", 3);
    }
}
```

**Kotlin**

```kotlin
import us.demeulder.psqlqueue.TaskQueueService

@Component
class OrderService(private val queueService: TaskQueueService) {

    fun processOrder(order: Order) {
        queueService.publishEvent("Order 123", "order.process", 3)
    }
}
```

`publishEvent` signature:

| Parameter | Type | Description |
|---|---|---|
| `payload` | `String` | Arbitrary string (plain text, JSON, etc.) |
| `taskType` | `String` | Routes the task to the matching handler |
| `maxAttempts` | `Int` | How many times to retry before marking as `FAILED` |

---

## 4. Consume an event

Implement `TaskHandler` and register the class as a Spring `@Component`. The library's listener will automatically route tasks to the handler matching `getTaskType()`.

**Java**

```java
import us.demeulder.psqlqueue.TaskHandler;

@Component
public class OrderPlacedHandler implements TaskHandler {

    @Override
    public String getTaskType() {
        return "order.process";
    }

    @Override
    public void handle(String payload) {
        // payload is whatever was passed to publishEvent()
        System.out.println("Handling order: " + payload);
    }
}
```

**Kotlin**

```kotlin
import us.demeulder.psqlqueue.TaskHandler

@Component
class OrderPlacedHandler : TaskHandler {

    override val taskType = "order.process"

    override fun handle(payload: String) {
        println("Handling order: $payload")
    }
}
```

A handler can also enqueue follow-on tasks — for example, fanning out to multiple downstream task types:

```java
@Component
public class OrderPlacedHandler implements TaskHandler {

    @Autowired TaskQueueService queueService;

    @Override
    public String getTaskType() { return "order.process"; }

    @Override
    public void handle(String payload) {
        queueService.publishEvent(payload, "send-email", 3);
        queueService.publishEvent(payload, "update-inventory", 3);
    }
}
```

Only one handler per `taskType` is allowed — the library enforces this at startup.

---

## Task lifecycle

```
PENDING → IN_PROGRESS → COMPLETED
                      → PENDING  (retry, exponential backoff up to 300 s)
                      → FAILED   (when attempts >= maxAttempts)
```

---

## Optional configuration

```properties
# Postgres LISTEN/NOTIFY channel (default: task_queue_channel)
psqlqueue.postgres.channel=my_custom_channel
```

---

## Development commands

```bash
./gradlew build                  # compile + test + package
./gradlew test                   # run tests only
./gradlew publishToMavenLocal    # publish to ~/.m2 for local consumption
```
