package us.demeulder.psqlqueue.queue

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

internal class LoggingTaskProcessorTest {

    @Test
    fun `logging processor does not throw`() {
        val processor = LoggingTaskProcessor()
        assertDoesNotThrow { processor.process("payload") }
    }
}
