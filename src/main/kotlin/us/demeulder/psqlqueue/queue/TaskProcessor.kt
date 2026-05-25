package us.demeulder.psqlqueue.queue

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

internal interface TaskProcessor {
    fun process(payload: String)
}

@Component
internal class LoggingTaskProcessor : TaskProcessor {
    companion object {
        private val logger = LoggerFactory.getLogger(LoggingTaskProcessor::class.java)
    }

    override fun process(payload: String) {
        logger.info("Processing queued payload: {}", payload)
    }
}
