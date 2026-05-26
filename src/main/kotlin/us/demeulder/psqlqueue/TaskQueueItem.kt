package us.demeulder.psqlqueue

import java.time.Instant
import java.util.UUID

internal enum class QueueStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

internal data class TaskQueueItem(
    val id: UUID,
    val taskType: String,
    val payload: String,
    val status: QueueStatus,
    val attempts: Int,
    val maxAttempts: Int,
    val nextAttemptAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastError: String?,
    val startedAt: Instant?
)

data class QueueStatusSummary(
    val pending: Long,
    val inProgress: Long,
    val completed: Long,
    val failed: Long,
    val oldestPendingAt: Instant?,
    val nextRetryAt: Instant?
)
