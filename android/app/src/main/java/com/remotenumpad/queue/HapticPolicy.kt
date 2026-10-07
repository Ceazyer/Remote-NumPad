package com.remotenumpad.queue

object HapticPolicy {
    fun shouldFeedback(enabled: Boolean, result: EnqueueResult): Boolean = enabled && result == EnqueueResult.ADDED
}
