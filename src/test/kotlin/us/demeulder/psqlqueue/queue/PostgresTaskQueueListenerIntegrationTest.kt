package us.demeulder.psqlqueue.queue

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal class PostgresTaskQueueListenerIntegrationTest {

    @Test
    fun `listener processes pending task and marks completed`() {
        val repo = mockk<TaskQueueRepository>(relaxed = true)
        val dataSource = mockk<DataSource>(relaxed = true)

        val id = UUID.randomUUID()
        val now = Instant.now()

        val task = TaskQueueItem(
            id = id,
            taskType = "default",
            payload = "payload-1",
            status = QueueStatus.IN_PROGRESS,
            attempts = 0,
            maxAttempts = 3,
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now,
            lastError = null,
            startedAt = now
        )

        every { repo.claimNextTask() } returnsMany listOf(task, null)
        every { repo.isPostgres() } returns false

        val handled = mutableListOf<String>()
        val handler = object : TaskHandler {
            override val taskType: String = "default"
            override fun handle(payload: String) { handled += payload }
        }

        val listener = PostgresTaskQueueListener(repo, listOf(handler), dataSource)

        val method = PostgresTaskQueueListener::class.java.getDeclaredMethod("processPendingTasks")
        method.isAccessible = true
        method.invoke(listener)

        assertEquals(listOf("payload-1"), handled)
        verify(exactly = 1) { repo.markCompleted(id) }
    }

    @Test
    fun `listener schedules retry when handler throws`() {
        val repo = mockk<TaskQueueRepository>(relaxed = true)
        val dataSource = mockk<DataSource>(relaxed = true)

        val id = UUID.randomUUID()
        val now = Instant.now()
        val task = TaskQueueItem(id, "default", "p", QueueStatus.IN_PROGRESS, 0, 1, now, now, now, null, now)

        every { repo.claimNextTask() } returnsMany listOf(task, null)

        val handler = object : TaskHandler {
            override val taskType: String = "default"
            override fun handle(payload: String) { throw RuntimeException("boom") }
        }

        val listener = PostgresTaskQueueListener(repo, listOf(handler), dataSource)
        val method = PostgresTaskQueueListener::class.java.getDeclaredMethod("processPendingTasks")
        method.isAccessible = true
        method.invoke(listener)

        verify(exactly = 1) { repo.scheduleRetry(task, any()) }
    }
}
