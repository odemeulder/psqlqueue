package us.demeulder.psqlqueue

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

interface TaskHandler {
    val taskType: String
    fun handle(payload: String)
}

@Component
internal class DefaultTaskProcessorHandler : TaskHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(DefaultTaskProcessorHandler::class.java)
    }

    override val taskType: String = "default"

    override fun handle(payload: String) {
        logger.info("Processing queued payload: {}", payload)
    }
}

