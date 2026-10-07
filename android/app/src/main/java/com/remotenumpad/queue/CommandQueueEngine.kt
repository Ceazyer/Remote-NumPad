package com.remotenumpad.queue

data class QueuedCommand(val sequence: Long, val command: String)

enum class EnqueueResult { ADDED, FULL, INVALID }

enum class QueueStatus {
    OFFLINE,
    READY,
    REVIEW_REQUIRED,
    SEND_FAILED,
    UNCERTAIN,
    PROTOCOL_ERROR
}

data class QueueSnapshot(
    val status: QueueStatus,
    val pendingCount: Int,
    val inFlightSequence: Long?,
    val clickedCount: Long,
    val enqueuedCount: Long,
    val sentCount: Long,
    val acknowledgedCount: Long,
    val errorCode: String?
)

interface CommandStore {
    val clientId: String
    fun pending(): List<QueuedCommand>
    fun enqueue(command: String, capacity: Int): QueuedCommand?
    fun acknowledge(sequence: Long): Boolean
    fun clear()
    fun serverInstanceId(): String?
    fun saveServerInstanceId(id: String)
}

fun interface CommandTransport {
    fun send(clientId: String, command: QueuedCommand): Boolean
}

class CommandQueueEngine(
    private val store: CommandStore,
    private val transport: CommandTransport,
    private val observer: (QueueSnapshot) -> Unit = {},
    private val capacity: Int = MAX_PENDING_COMMANDS
) {
    private var connected = false
    private var currentServerInstanceId: String? = null
    private var inFlight: QueuedCommand? = null
    private var pauseStatus: QueueStatus? = null
    private var reviewRequiredAtNextWelcome = store.pending().isNotEmpty()
    private var clickedCount = 0L
    private var enqueuedCount = 0L
    private var sentCount = 0L
    private var acknowledgedCount = 0L
    private var errorCode: String? = null

    @Synchronized
    fun noteClick() {
        clickedCount++
        publish()
    }

    @Synchronized
    fun enqueue(command: String): EnqueueResult {
        if (command !in CommandCatalog.allCommands) {
            errorCode = "invalid_command"
            publish()
            return EnqueueResult.INVALID
        }

        if (store.enqueue(command, capacity) == null) {
            errorCode = "queue_full"
            publish()
            return EnqueueResult.FULL
        }

        enqueuedCount++
        errorCode = null
        if (!connected) reviewRequiredAtNextWelcome = true
        publish()
        pump()
        return EnqueueResult.ADDED
    }

    @Synchronized
    fun onWelcome(serverInstanceId: String) {
        if (serverInstanceId.isBlank()) {
            pauseStatus = QueueStatus.PROTOCOL_ERROR
            errorCode = "invalid_server_instance"
            connected = false
            publish()
            return
        }

        val previouslySaved = store.serverInstanceId()
        val changedInstance = previouslySaved != null && previouslySaved != serverInstanceId
        connected = true
        currentServerInstanceId = serverInstanceId
        store.saveServerInstanceId(serverInstanceId)

        val hasPending = store.pending().isNotEmpty()
        if (hasPending && (reviewRequiredAtNextWelcome || previouslySaved == null || changedInstance)) {
            pauseStatus = QueueStatus.REVIEW_REQUIRED
            errorCode = if (changedInstance) "server_restarted" else "pending_review"
        } else if (!hasPending) {
            reviewRequiredAtNextWelcome = false
            if (pauseStatus == QueueStatus.REVIEW_REQUIRED) pauseStatus = null
            errorCode = null
        }

        publish()
        pump()
    }

    @Synchronized
    fun onDisconnected() {
        connected = false
        inFlight = null
        if (store.pending().isNotEmpty()) {
            reviewRequiredAtNextWelcome = true
            pauseStatus = QueueStatus.REVIEW_REQUIRED
            errorCode = "connection_lost_with_pending_input"
        } else {
            pauseStatus = null
            errorCode = null
        }
        publish()
    }

    @Synchronized
    fun onAck(sequence: Long, result: String, remoteError: String? = null) {
        val current = inFlight ?: return
        if (sequence != current.sequence) return

        when (result) {
            "injected" -> {
                if (!store.acknowledge(sequence)) {
                    inFlight = null
                    pauseStatus = QueueStatus.PROTOCOL_ERROR
                    errorCode = "ack_does_not_match_queue_head"
                    publish()
                    return
                }
                inFlight = null
                acknowledgedCount++
                errorCode = null
                publish()
                pump()
            }
            "failed" -> {
                inFlight = null
                pauseStatus = QueueStatus.SEND_FAILED
                errorCode = remoteError ?: "server_injection_failed"
                publish()
            }
            "uncertain" -> {
                inFlight = null
                pauseStatus = QueueStatus.UNCERTAIN
                errorCode = remoteError ?: "server_injection_uncertain"
                publish()
            }
            else -> {
                inFlight = null
                pauseStatus = QueueStatus.PROTOCOL_ERROR
                errorCode = "invalid_ack_result"
                publish()
            }
        }
    }

    @Synchronized
    fun onProtocolError(code: String) {
        inFlight = null
        pauseStatus = QueueStatus.PROTOCOL_ERROR
        errorCode = code.take(64).ifBlank { "protocol_error" }
        publish()
    }

    @Synchronized
    fun resumeAfterReview() {
        if (!connected || pauseStatus != QueueStatus.REVIEW_REQUIRED) return
        currentServerInstanceId?.let(store::saveServerInstanceId)
        reviewRequiredAtNextWelcome = false
        pauseStatus = null
        errorCode = null
        publish()
        pump()
    }

    @Synchronized
    fun retryFailedCurrent() {
        if (!connected || pauseStatus != QueueStatus.SEND_FAILED) return
        pauseStatus = null
        errorCode = null
        publish()
        pump()
    }

    @Synchronized
    fun skipUncertainAfterManualCheck() {
        if (pauseStatus != QueueStatus.UNCERTAIN) return
        val head = store.pending().firstOrNull() ?: return
        if (!store.acknowledge(head.sequence)) return
        pauseStatus = null
        errorCode = null
        publish()
        pump()
    }

    @Synchronized
    fun clearPending() {
        store.clear()
        inFlight = null
        pauseStatus = null
        reviewRequiredAtNextWelcome = false
        errorCode = null
        publish()
    }

    @Synchronized
    fun snapshot(): QueueSnapshot = snapshotUnsafe()

    private fun pump() {
        if (!connected || pauseStatus != null || inFlight != null) return
        val next = store.pending().firstOrNull() ?: return
        inFlight = next
        if (transport.send(store.clientId, next)) {
            sentCount++
            errorCode = null
        } else {
            inFlight = null
            connected = false
            reviewRequiredAtNextWelcome = true
            pauseStatus = QueueStatus.REVIEW_REQUIRED
            errorCode = "socket_send_failed"
        }
        publish()
    }

    private fun publish() = observer(snapshotUnsafe())

    private fun snapshotUnsafe(): QueueSnapshot = QueueSnapshot(
        status = pauseStatus ?: if (connected) QueueStatus.READY else QueueStatus.OFFLINE,
        pendingCount = store.pending().size,
        inFlightSequence = inFlight?.sequence,
        clickedCount = clickedCount,
        enqueuedCount = enqueuedCount,
        sentCount = sentCount,
        acknowledgedCount = acknowledgedCount,
        errorCode = errorCode
    )

    companion object {
        const val MAX_PENDING_COMMANDS = 200
    }
}
