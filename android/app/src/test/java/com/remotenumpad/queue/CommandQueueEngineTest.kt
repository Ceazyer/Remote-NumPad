package com.remotenumpad.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandQueueEngineTest {
    @Test
    fun hundredRepeatedTapsAreQueuedAndAcknowledgedInOrder() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")

        repeat(100) { index ->
            harness.engine.noteClick()
            assertEquals(EnqueueResult.ADDED, harness.engine.enqueue("7"))
            if (index < 99) {
                harness.ackCurrent()
            }
        }
        harness.ackCurrent()

        assertEquals((1L..100L).toList(), harness.transport.sent.map { it.sequence })
        assertEquals(List(100) { "7" }, harness.transport.sent.map { it.command })
        assertEquals(100, harness.engine.snapshot().acknowledgedCount)
        assertEquals(0, harness.engine.snapshot().pendingCount)
    }

    @Test
    fun fiveHundredAlternatingTapsKeepTheExactOrder() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        val expected = List(500) { if (it % 2 == 0) "1" else "9" }

        expected.forEachIndexed { index, command ->
            harness.engine.noteClick()
            assertEquals(EnqueueResult.ADDED, harness.engine.enqueue(command))
            if (index < expected.lastIndex) harness.ackCurrent()
        }
        harness.ackCurrent()

        assertEquals(expected, harness.transport.sent.map { it.command })
        assertEquals((1L..500L).toList(), harness.transport.sent.map { it.sequence })
    }

    @Test
    fun disconnectWithPendingInputRequiresExplicitResume() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.engine.enqueue("1")
        harness.engine.onDisconnected()
        harness.engine.onWelcome("server-a")

        assertEquals(QueueStatus.REVIEW_REQUIRED, harness.engine.snapshot().status)
        assertEquals(1, harness.transport.sent.size)

        harness.engine.resumeAfterReview()

        assertEquals(2, harness.transport.sent.size)
        assertEquals(1L, harness.transport.sent.last().sequence)
    }

    @Test
    fun changedServerInstanceRequiresExplicitResume() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.engine.enqueue("1")
        harness.engine.onDisconnected()
        harness.engine.onWelcome("server-b")

        assertEquals(QueueStatus.REVIEW_REQUIRED, harness.engine.snapshot().status)
        assertEquals("server-b", harness.store.savedServerInstanceId)

        harness.engine.resumeAfterReview()

        assertEquals(2, harness.transport.sent.size)
    }

    @Test
    fun tapsQueuedWhileDisconnectedDoNotFlushWithoutReview() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.engine.onDisconnected()
        harness.engine.enqueue("3")
        harness.engine.onWelcome("server-a")

        assertEquals(QueueStatus.REVIEW_REQUIRED, harness.engine.snapshot().status)
        assertTrue(harness.transport.sent.isEmpty())
    }

    @Test
    fun failedWebSocketSendLeavesTheCommandQueued() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.transport.acceptSends = false

        harness.engine.enqueue("3")

        assertEquals(1, harness.engine.snapshot().pendingCount)
        assertEquals(QueueStatus.REVIEW_REQUIRED, harness.engine.snapshot().status)
        assertNull(harness.engine.snapshot().inFlightSequence)
    }

    @Test
    fun duplicateAckDoesNotAcknowledgeTheNextCommand() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.engine.enqueue("1")
        harness.engine.enqueue("2")
        harness.engine.onAck(1, "injected")
        harness.engine.onAck(1, "injected")

        assertEquals(1, harness.engine.snapshot().acknowledgedCount)
        assertEquals(2L, harness.engine.snapshot().inFlightSequence)
        assertEquals(listOf(1L, 2L), harness.transport.sent.map { it.sequence })
    }

    @Test
    fun failedInjectionRequiresExplicitRetryAndPreservesTheSameSequence() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.engine.enqueue("4")
        harness.engine.onAck(1, "failed", "sendinput_failed")

        assertEquals(QueueStatus.SEND_FAILED, harness.engine.snapshot().status)
        assertEquals(1, harness.engine.snapshot().pendingCount)
        harness.engine.retryFailedCurrent()

        assertEquals(listOf(1L, 1L), harness.transport.sent.map { it.sequence })
    }

    @Test
    fun uncertainInjectionCanOnlyAdvanceAfterExplicitSkip() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.engine.enqueue("5")
        harness.engine.enqueue("6")
        harness.engine.onAck(1, "uncertain", "sendinput_partial")

        assertEquals(QueueStatus.UNCERTAIN, harness.engine.snapshot().status)
        assertEquals(listOf(1L), harness.transport.sent.map { it.sequence })
        harness.engine.skipUncertainAfterManualCheck()

        assertEquals(listOf(1L, 2L), harness.transport.sent.map { it.sequence })
        assertEquals(1, harness.engine.snapshot().pendingCount)
    }

    @Test
    fun queueCapacityRejectsTheTwoHundredAndFirstCommand() {
        val harness = Harness()
        repeat(200) { assertEquals(EnqueueResult.ADDED, harness.engine.enqueue("8")) }

        assertEquals(EnqueueResult.FULL, harness.engine.enqueue("8"))
        assertEquals(200, harness.engine.snapshot().pendingCount)
        assertEquals(200, harness.engine.snapshot().enqueuedCount)
    }

    @Test
    fun restoredPendingCommandsAreNotAutomaticallyReplayed() {
        val store = MemoryCommandStore()
        store.enqueue("2", 200)
        val transport = RecordingTransport()
        val restored = CommandQueueEngine(store, transport)

        restored.onWelcome("server-a")

        assertEquals(QueueStatus.REVIEW_REQUIRED, restored.snapshot().status)
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun malformedProtocolResponsePausesWithoutDroppingPendingInput() {
        val harness = Harness()
        harness.engine.onWelcome("server-a")
        harness.engine.enqueue("2")

        harness.engine.onProtocolError("malformed_ack")

        assertEquals(QueueStatus.PROTOCOL_ERROR, harness.engine.snapshot().status)
        assertEquals(1, harness.engine.snapshot().pendingCount)
        assertNull(harness.engine.snapshot().inFlightSequence)
    }

    @Test
    fun nativeCommandNamesMatchTheWindowsServiceContract() {
        assertEquals(
            setOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "-",
                "BACKSPACE", "DELETE", "EDIT", "ENTER",
                "PREV_CELL", "NEXT_CELL", "UP", "DOWN", "LEFT", "RIGHT",
                "UNDO", "COPY", "PASTE", "SAVE", "SAVE_AS", "AUTO_SUM",
                "FORMULA_AVERAGE", "FORMULA_MAX", "FORMULA_MIN", "FORMULA_ROUND", "FORMULA_IF"
            ),
            CommandCatalog.allCommands
        )
    }

    private class Harness {
        val store = MemoryCommandStore()
        val transport = RecordingTransport()
        val engine = CommandQueueEngine(store, transport)

        fun ackCurrent() {
            val sequence = engine.snapshot().inFlightSequence
            assertTrue("Expected a command in flight", sequence != null)
            engine.onAck(sequence!!, "injected")
        }
    }

    private class RecordingTransport : CommandTransport {
        val sent = mutableListOf<QueuedCommand>()
        var acceptSends = true

        override fun send(clientId: String, command: QueuedCommand): Boolean {
            if (!acceptSends) return false
            sent += command
            return true
        }
    }

    private class MemoryCommandStore : CommandStore {
        private val queue = mutableListOf<QueuedCommand>()
        private var nextSequence = 1L
        var savedServerInstanceId: String? = null
            private set

        override val clientId = "test-client"

        override fun pending(): List<QueuedCommand> = queue.toList()

        override fun enqueue(command: String, capacity: Int): QueuedCommand? {
            if (queue.size >= capacity) return null
            return QueuedCommand(nextSequence++, command).also(queue::add)
        }

        override fun acknowledge(sequence: Long): Boolean {
            val head = queue.firstOrNull() ?: return false
            if (head.sequence != sequence) return false
            queue.removeAt(0)
            return true
        }

        override fun clear() {
            queue.clear()
        }

        override fun serverInstanceId(): String? = savedServerInstanceId

        override fun saveServerInstanceId(id: String) {
            savedServerInstanceId = id
        }
    }
}
