package us.demeulder.psqlqueue

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import us.demeulder.psqlqueue.TaskQueueRepository

@Service
class TaskQueueService() {

    @Autowired
    private lateinit var repository: TaskQueueRepository

    fun getSummary(): QueueStatusSummary {
        return repository.getSummary()
    }

    fun publishEvent(payload: String, taskType: String, maxAttempts: Int): UUID {
        return repository.insertTask(payload, taskType, maxAttempts)
    }
}
