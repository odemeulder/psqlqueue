package us.demeulder.psqlqueue.queue

/*
PostgreSQL table schema required for this queue implementation:

CREATE TABLE task_queue (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_error TEXT
);

CREATE INDEX idx_task_queue_pending_next_attempt
    ON task_queue(status, next_attempt_at);
*/

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
internal class TaskQueueRepository(
    private val jdbcTemplate: JdbcTemplate,
    @org.springframework.beans.factory.annotation.Value("\${psqlqueue.postgres.channel:task_queue_channel}")
    private val channel: String = DEFAULT_TASK_QUEUE_CHANNEL
) {
    companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 5
        private const val DEFAULT_TASK_TYPE = "default"
        private const val DEFAULT_TASK_QUEUE_CHANNEL = "task_queue_channel"
    }

    fun insertTask(payload: String, taskType: String = DEFAULT_TASK_TYPE, maxAttempts: Int = DEFAULT_MAX_ATTEMPTS): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
                INSERT INTO task_queue (
                    id,
                    task_type,
                    payload,
                    status,
                    attempts,
                    max_attempts,
                    next_attempt_at,
                    created_at,
                    updated_at,
                    started_at
                ) VALUES (?, ?, ?, ?, ?, ?, now(), now(), now(), NULL)
            """.trimIndent(),
            id,
            taskType,
            payload,
            QueueStatus.PENDING.name,
            0,
            maxAttempts
        )
        notifyPostgresChannel()
        return id
    }

    @Transactional
    fun claimNextTask(): TaskQueueItem? {
        val tasks = jdbcTemplate.query(
            """
                SELECT id,
                       task_type,
                       payload,
                       status,
                       attempts,
                       max_attempts,
                       next_attempt_at,
                       created_at,
                       updated_at,
                       last_error,
                       started_at
                FROM task_queue
                WHERE status = ?
                  AND next_attempt_at <= now()
                ORDER BY created_at
                LIMIT 1
                ${if (isPostgres()) "FOR UPDATE SKIP LOCKED" else "FOR UPDATE"}
            """.trimIndent(),
            rowMapper,
            QueueStatus.PENDING.name
        )

        val task = tasks.firstOrNull() ?: return null
        jdbcTemplate.update(
            "UPDATE task_queue SET status = ?, updated_at = now(), started_at = now() WHERE id = ?",
            QueueStatus.IN_PROGRESS.name,
            task.id
        )

        return task.copy(status = QueueStatus.IN_PROGRESS, startedAt = Instant.now())
    }

    fun markCompleted(taskId: UUID) {
        jdbcTemplate.update(
            "UPDATE task_queue SET status = ?, updated_at = now(), started_at = NULL WHERE id = ?",
            QueueStatus.COMPLETED.name,
            taskId
        )
    }

    fun scheduleRetry(task: TaskQueueItem, errorMessage: String) {
        val nextAttempt = task.attempts + 1
        if (nextAttempt >= task.maxAttempts) {
            jdbcTemplate.update(
                "UPDATE task_queue SET status = ?, attempts = ?, last_error = ?, updated_at = now(), started_at = NULL WHERE id = ?",
                QueueStatus.FAILED.name,
                nextAttempt,
                errorMessage,
                task.id
            )
            return
        }

        val backoffSeconds = calculateBackoffSeconds(nextAttempt)
        val nextAttemptExpression = if (isPostgres()) {
            "now() + (? * interval '1 second')"
        } else {
            "DATEADD('SECOND', ?, now())"
        }

        jdbcTemplate.update(
            """
                UPDATE task_queue
                SET attempts = ?,
                    last_error = ?,
                    next_attempt_at = $nextAttemptExpression,
                    updated_at = now(),
                    status = ?,
                    started_at = NULL
                WHERE id = ?
            """.trimIndent(),
            nextAttempt,
            errorMessage,
            backoffSeconds,
            QueueStatus.PENDING.name,
            task.id
        )
        notifyPostgresChannel()
    }

    @Transactional
    fun reclaimStaleInProgressTasks(timeout: Duration): Int {
        val staleTasks = jdbcTemplate.query(
            """
                SELECT id,
                       task_type,
                       payload,
                       status,
                       attempts,
                       max_attempts,
                       next_attempt_at,
                       created_at,
                       updated_at,
                       last_error,
                       started_at
                FROM task_queue
                WHERE status = ?
                  AND started_at <= ${if (isPostgres()) "now() - (? * interval '1 second')" else "DATEADD('SECOND', ?, now())"}
            """.trimIndent(),
            rowMapper,
            QueueStatus.IN_PROGRESS.name,
            timeout.seconds.toInt()
        )

        staleTasks.forEach { task ->
            scheduleRetry(task, "Task timed out after ${timeout.toMinutes()} minute(s)")
        }

        return staleTasks.size
    }

    fun getSummary(): QueueStatusSummary {
        return QueueStatusSummary(
            pending = countByStatus(QueueStatus.PENDING),
            inProgress = countByStatus(QueueStatus.IN_PROGRESS),
            completed = countByStatus(QueueStatus.COMPLETED),
            failed = countByStatus(QueueStatus.FAILED),
            oldestPendingAt = queryInstant("SELECT min(created_at) FROM task_queue WHERE status = ?", QueueStatus.PENDING.name),
            nextRetryAt = queryInstant("SELECT min(next_attempt_at) FROM task_queue WHERE status = ? AND next_attempt_at > now()", QueueStatus.PENDING.name)
        )
    }

    fun cleanupOldTasks(olderThan: Duration): Int {
        val ageExpression = if (isPostgres()) {
            "now() - (? * interval '1 second')"
        } else {
            "DATEADD('SECOND', ?, now())"
        }

        return jdbcTemplate.update(
            "DELETE FROM task_queue WHERE status IN (?, ?) AND updated_at < $ageExpression",
            QueueStatus.COMPLETED.name,
            QueueStatus.FAILED.name,
            olderThan.seconds.toInt()
        )
    }

    private fun countByStatus(status: QueueStatus): Long {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM task_queue WHERE status = ?",
            Long::class.java,
            status.name
        ) ?: 0L
    }

    private fun queryInstant(sql: String, vararg args: Any): Instant? {
        return jdbcTemplate.queryForObject(sql, Timestamp::class.java, *args)?.toInstant()
    }

    internal fun isPostgres(): Boolean {
        val connection = jdbcTemplate.dataSource?.connection ?: return false
        return connection.use {
            it.metaData.databaseProductName.contains("Postgre", ignoreCase = true)
        }
    }

    private fun notifyPostgresChannel() {
        if (!isPostgres()) {
            return
        }

        jdbcTemplate.execute("SELECT pg_notify('$channel', '')")
    }

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        TaskQueueItem(
            id = rs.getObject("id", UUID::class.java),
            taskType = rs.getString("task_type"),
            payload = rs.getString("payload"),
            status = QueueStatus.valueOf(rs.getString("status")),
            attempts = rs.getInt("attempts"),
            maxAttempts = rs.getInt("max_attempts"),
            nextAttemptAt = rs.getTimestamp("next_attempt_at").toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            lastError = rs.getString("last_error"),
            startedAt = rs.getTimestamp("started_at")?.toInstant()
        )
    }

    private fun calculateBackoffSeconds(attempt: Int): Long {
        return (attempt * 5).toLong().coerceAtMost(300)
    }
}
