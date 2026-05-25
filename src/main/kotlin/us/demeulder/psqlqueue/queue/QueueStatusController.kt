package us.demeulder.psqlqueue.queue

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/queue")
internal class QueueStatusController(
    private val service: TaskQueueService
) {
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<QueueStatusSummary> {
        return ResponseEntity.ok(service.getSummary())
    }

    @PostMapping("/tasks")
    fun enqueueTask(@RequestBody request: EnqueueTaskRequest): ResponseEntity<EnqueueTaskResponse> {
        val taskId = service.publishEvent(request.payload, request.taskType ?: "default", request.maxAttempts ?: 5)
        return ResponseEntity.ok(EnqueueTaskResponse(taskId.toString()))
    }
}

internal data class EnqueueTaskRequest @JsonCreator constructor(
    @JsonProperty("payload") val payload: String,
    @JsonProperty("taskType") val taskType: String? = null,
    @JsonProperty("maxAttempts") val maxAttempts: Int? = null
)

internal data class EnqueueTaskResponse(
    val taskId: String
)
