package com.remotenumpad

import android.app.Instrumentation
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

class MainActivityClickQueueTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
    override fun setUp() {
        super.setUp()
        val target = instrumentation.targetContext
        target.deleteDatabase("remote-numpad-queue.db")
        target.getSharedPreferences(MainActivity.PREFERENCES_NAME, 0).edit()
            .clear()
            .putBoolean(MainActivity.PREF_DIAGNOSTICS, true)
            .commit()
        setActivityInitialTouchMode(false)
    }

    fun testFastRepeatedTouchEventsAreQueuedExactlyOnceAndTabsDoNotSend() {
        val activity = activity
        val instrumentation = instrumentation
        val tab = activity.findViewById<Button>(R.id.tab_editing)
        instrumentation.runOnMainSync { tab.performClick() }
        waitForQueueCount(activity, "待发送 0 / 200")

        val keypadTab = activity.findViewById<Button>(R.id.tab_keypad)
        instrumentation.runOnMainSync { keypadTab.performClick() }
        val numberSeven = activity.findViewById<Button>(R.id.key_7)
        tapRapidly(instrumentation, numberSeven, 100)

        waitForQueueCount(activity, "待发送 100 / 200")
        val diagnostics = activity.findViewById<TextView>(R.id.diagnostics_count).text.toString()
        assertTrue("Expected 100 accepted taps: $diagnostics", diagnostics.contains("点击 100"))
        assertTrue("Expected 100 durable enqueues: $diagnostics", diagnostics.contains("入队 100"))
    }

    fun testFastPointedDirectionalKeysEnqueueEachTouchExactlyOnce() {
        val activity = activity
        instrumentation.waitForIdleSync()
        fun find(view: View, label: String): Button? {
            if (view is Button && view.contentDescription?.toString() == label) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                find(view.getChildAt(index), label)?.let { return it }
            }
            return null
        }
        listOf("向上移动一格", "向右移动一格", "向下移动一格", "向左移动一格").forEach { label ->
            val button = find(activity.window.decorView, label)
            assertNotNull("Missing direction: $label", button)
            tapRapidly(instrumentation, button!!, 25)
        }
        waitForQueueCount(activity, "待发送 100 / 200")
        val diagnostics = activity.findViewById<TextView>(R.id.diagnostics_count).text.toString()
        assertTrue(diagnostics.contains("点击 100"))
        assertTrue(diagnostics.contains("入队 100"))
    }

    private fun tapRapidly(instrumentation: Instrumentation, button: Button, count: Int) {
        val location = IntArray(2)
        instrumentation.runOnMainSync { button.getLocationOnScreen(location) }
        val x = location[0] + button.width / 2f
        val y = location[1] + button.height / 2f

        repeat(count) {
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            instrumentation.sendPointerSync(down)
            down.recycle()

            val upTime = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            instrumentation.sendPointerSync(up)
            up.recycle()
        }
    }

    private fun waitForQueueCount(activity: MainActivity, expected: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var actual = ""
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                actual = activity.findViewById<TextView>(R.id.queue_count).text.toString()
            }
            if (actual == expected) return
            SystemClock.sleep(25)
        }
        fail("Expected queue label '$expected', got '$actual'")
    }
}
