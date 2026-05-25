package us.demeulder.psqlqueue.queue

import org.springframework.stereotype.Component

interface TaskHandler {
    val taskType: String
    fun handle(payload: String)
}

@Component
internal class DefaultTaskProcessorHandler(
    private val processor: TaskProcessor
) : TaskHandler {
    override val taskType: String = "default"

    override fun handle(payload: String) {
        processor.process(payload)
    }
}
