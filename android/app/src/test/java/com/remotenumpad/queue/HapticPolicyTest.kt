package com.remotenumpad.queue

import org.junit.Assert.*
import org.junit.Test

class HapticPolicyTest {
    @Test fun feedbackMeansDurableEnqueueAndIsOptIn() {
        assertTrue(HapticPolicy.shouldFeedback(true, EnqueueResult.ADDED))
        assertFalse(HapticPolicy.shouldFeedback(false, EnqueueResult.ADDED))
        assertFalse(HapticPolicy.shouldFeedback(true, EnqueueResult.FULL))
        assertFalse(HapticPolicy.shouldFeedback(true, EnqueueResult.INVALID))
    }
}
