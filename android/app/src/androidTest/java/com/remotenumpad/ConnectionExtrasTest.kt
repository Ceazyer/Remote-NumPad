package com.remotenumpad

import android.test.InstrumentationTestCase
import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.remotenumpad.net.ConnectionQrParser
import com.remotenumpad.net.ConnectionEndpoint
import com.remotenumpad.net.RemoteNumPadConnection
import java.util.concurrent.atomic.AtomicInteger

class ConnectionExtrasTest : InstrumentationTestCase() {
    fun testDecodesTheDesktopGeneratedQrAndParsesItsPort() {
        val bitmap = instrumentation.context.assets.open("connection-qr.png").use { BitmapFactory.decodeStream(it) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals(ConnectionEndpoint("192.168.1.20", 8888), ConnectionQrParser.parse(result.text))
        bitmap.recycle()
    }
    fun testFeedbackCallbackRunsOnlyForDurableAcceptedKeysAndNotForFullOrInvalidInput() {
        instrumentation.targetContext.deleteDatabase("remote-numpad-queue.db")
        val accepted = AtomicInteger()
        val clicked = java.util.concurrent.atomic.AtomicLong()
        val connection = RemoteNumPadConnection(instrumentation.targetContext,
            onState = { state, _ -> clicked.set(state.clickedCount) }, onStorageError = { fail(it) })
        try {
            connection.enqueue("NOT_A_COMMAND") { fail("Invalid input triggered successful feedback") }
            repeat(201) { connection.enqueue("7") { accepted.incrementAndGet() } }
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000
            while (clicked.get() < 202 && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(25)
            instrumentation.waitForIdleSync()
            assertEquals(202L, clicked.get())
            assertEquals(200, accepted.get())
        } finally { connection.dispose() }
    }
}
