package us.demeulder.psqlqueue.queue

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
internal class TaskCleanupJob(
    private val repository: TaskQueueRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TaskCleanupJob::class.java)
    }

    @Scheduled(initialDelayString = "5000", fixedDelayString = "PT1H")
    fun cleanupOldTasks() {
        val deleted = repository.cleanupOldTasks(Duration.ofDays(7))
        logger.info("Cleaned up {} old task queue items older than 7 days", deleted)
    }

    @Scheduled(initialDelayString = "15000", fixedDelayString = "PT5M")
    fun reclaimStaleInProgressTasks() {
        val reclaimed = repository.reclaimStaleInProgressTasks(Duration.ofMinutes(10))
        if (reclaimed > 0) {
            logger.info("Reclaimed {} stale IN_PROGRESS task(s) that exceeded 10 minutes", reclaimed)
        }
    }
}
