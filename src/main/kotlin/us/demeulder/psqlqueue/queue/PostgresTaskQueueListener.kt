package us.demeulder.psqlqueue.queue

import jakarta.annotation.PreDestroy
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.sql.Connection
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

@Component
internal class PostgresTaskQueueListener(
    private val repository: TaskQueueRepository,
    handlers: List<TaskHandler>,
    private val dataSource: DataSource,
    @org.springframework.beans.factory.annotation.Value("\${psqlqueue.postgres.channel:task_queue_channel}")
    private val channel: String = DEFAULT_CHANNEL
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
        private val logger = LoggerFactory.getLogger(PostgresTaskQueueListener::class.java)
        private const val DEFAULT_CHANNEL = "task_queue_channel"
        private const val LISTEN_TIMEOUT_MS = 10_000
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val MAX_TASKS_PER_CYCLE = 10
    }

    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "postgres-task-queue-listener").apply { isDaemon = true }
    }
    @Volatile
    private var listenConnection: Connection? = null

    @EventListener(ApplicationReadyEvent::class)
    fun startListener() {
        if (!repository.isPostgres()) {
            logger.info("Postgres queue listener disabled because the datasource is not PostgreSQL")
            return
        }

        if (running.compareAndSet(false, true)) {
            logger.info("Starting Postgres task queue listener for channel '{}'.", channel)
            executor.submit { runListenerLoop() }
        }
    }

    @PreDestroy
    fun stopListener() {
        running.set(false)
        executor.shutdownNow()
        listenConnection?.close()
    }

    private fun runListenerLoop() {
        while (running.get()) {
            try {
                openListenConnection()
                processPendingTasks()
                waitForNotifications()
            } catch (ex: Exception) {
                logger.warn("Postgres LISTEN/NOTIFY listener lost connection, reconnecting in {}ms", RECONNECT_DELAY_MS, ex)
                closeListenConnection()
                sleep(RECONNECT_DELAY_MS)
            }
        }
    }

    private fun openListenConnection() {
        val connection = dataSource.connection
        connection.autoCommit = true
        connection.createStatement().use { it.execute("LISTEN $channel") }
        listenConnection = connection
        logger.info("Subscribed to Postgres channel '{}'", channel)
    }

    private fun waitForNotifications() {
        val pgConnection = listenConnection?.unwrap(PGConnection::class.java)
            ?: throw IllegalStateException("PostgreSQL connection could not be unwrapped to PGConnection")

        while (running.get()) {
            val notifications = pgConnection.getNotifications(LISTEN_TIMEOUT_MS)
            if (notifications != null && notifications.isNotEmpty()) {
                logger.debug("Received {} notification(s) on Postgres channel '{}'", notifications.size, channel)
                return
            }
            // No notification: continue waiting without processing to avoid duplicate claims.
        }
    }

    private fun processPendingTasks() {
        repeat(MAX_TASKS_PER_CYCLE) {
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

    private fun closeListenConnection() {
        try {
            listenConnection?.close()
        } catch (ignored: Exception) {
            logger.debug("Error closing Postgres listen connection", ignored)
        } finally {
            listenConnection = null
        }
    }

    private fun sleep(milliseconds: Long) {
        try {
            Thread.sleep(milliseconds)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
