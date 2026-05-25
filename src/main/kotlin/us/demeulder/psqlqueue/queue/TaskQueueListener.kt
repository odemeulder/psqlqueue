package us.demeulder.psqlqueue.queue

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class TaskQueueListener(
    private val repository: TaskQueueRepository,
    handlers: List<TaskHandler>
) {
    private val handlerByType: Map<String, TaskHandler> = handlers
        .groupBy { it.taskType }
        .mapValues { (_, list) ->
            if (list.size > 1) {
                throw IllegalStateException("Multiple TaskHandler beans registered for type '${list.first().taskType}'")
            }
            list.single()
        }
    companion object {
        private val logger = LoggerFactory.getLogger(TaskQueueListener::class.java)
    }

    @Scheduled(initialDelayString = "2000", fixedDelayString = "5000")
    fun pollQueue() {
        repeat(10) {
            val task = repository.claimNextTask() ?: return
            try {
                logger.info("Claimed queued task {} of type {} for processing", task.id, task.taskType)
                val handler = handlerByType[task.taskType]
                    ?: throw IllegalStateException("No TaskHandler registered for type '${task.taskType}'")
                handler.handle(task.payload)
                repository.markCompleted(task.id)
                logger.info("Completed queued task {}", task.id)
            } catch (ex: Exception) {
                repository.scheduleRetry(task, ex.message ?: ex::class.simpleName ?: "unknown")
                logger.warn("Task {} failed, retry scheduled if attempts remain", task.id, ex)
            }
        }
    }
}
