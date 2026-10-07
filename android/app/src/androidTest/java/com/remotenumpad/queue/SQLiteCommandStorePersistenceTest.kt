package com.remotenumpad.queue

import android.test.InstrumentationTestCase

class SQLiteCommandStorePersistenceTest : InstrumentationTestCase() {
    fun testQueueClientIdAndMonotonicSequenceSurviveStoreRecreation() {
        val target = instrumentation.targetContext
        target.deleteDatabase("remote-numpad-queue.db")

        val firstStore = SQLiteCommandStore(target)
        val clientId = firstStore.clientId
        val first = firstStore.enqueue("1", 200)
        assertNotNull(first)
        firstStore.close()

        val reopenedStore = SQLiteCommandStore(target)
        assertEquals(clientId, reopenedStore.clientId)
        assertEquals(first, reopenedStore.pending().single())
        assertTrue(reopenedStore.acknowledge(first!!.sequence))
        val next = reopenedStore.enqueue("2", 200)

        assertNotNull(next)
        assertEquals(first.sequence + 1, next!!.sequence)
        reopenedStore.close()
    }
}
