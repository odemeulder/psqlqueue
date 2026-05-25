package us.demeulder.psqlqueue.queue

import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class TaskQueueService(
    private val repository: TaskQueueRepository
) {
    fun getSummary(): QueueStatusSummary {
        return repository.getSummary()
    }

    fun publishEvent(payload: String, taskType: String, maxAttempts: Int): UUID {
        return repository.insertTask(payload, taskType, maxAttempts)
    }
}
