package us.demeulder.psqlqueue.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DefaultTaskProcessorHandlerTest {

    private class RecordingProcessor : TaskProcessor {
        val processed = mutableListOf<String>()
        override fun process(payload: String) {
            processed += payload
        }
    }

    @Test
    fun `handler delegates payload to processor`() {
        val recorder = RecordingProcessor()
        val handler = DefaultTaskProcessorHandler(recorder)

        handler.handle("hello-world")

        assertEquals(listOf("hello-world"), recorder.processed)
    }
}
