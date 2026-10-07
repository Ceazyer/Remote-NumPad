package com.remotenumpad

import android.graphics.Rect
import android.content.Context
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import kotlin.math.ceil

class MainActivityLayoutTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
    override fun setUp() {
        super.setUp()
        instrumentation.targetContext.deleteDatabase("remote-numpad-queue.db")
        instrumentation.targetContext.getSharedPreferences(MainActivity.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    fun testCalculatorAndNavigationShareTheDefaultScreen() {
        val activity = activity
        instrumentation.waitForIdleSync()

        assertEquals(2, activity.findViewById<LinearLayout>(R.id.page_tabs).childCount)
        listOf("7", "8", "9", "0", "下一格", "上一格", "向上移动一格", "向左移动一格", "向下移动一格", "向右移动一格")
            .forEach { label -> assertUsableButton(activity, label) }

        val zero = bounds(activity, "0")
        val seven = bounds(activity, "7")
        val eight = bounds(activity, "8")
        val decimal = bounds(activity, ".")
        val nine = bounds(activity, "9")
        val enter = bounds(activity, "Enter")
        assertEquals(seven.left, zero.left)
        assertEquals(eight.right, zero.right)
        assertEquals(nine.left, decimal.left)
        assertEquals(decimal.top, enter.top)
        assertTrue(bounds(activity, "撤销").top < seven.top)
        assertEquals(seven.top, bounds(activity, "退格").top)
        assertAuxiliaryLayout(activity)
    }

    fun testThemeSwitchPersistsWithoutEnqueuingOrChangingThePage() {
        val activity = activity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { findButton(activity.window.decorView, "7")!!.performClick() }
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var queued = false
            instrumentation.runOnMainSync {
                queued = activity.findViewById<android.widget.TextView>(R.id.queue_count).text.toString() == "待发送 1 / 200"
            }
            if (queued) break
            android.os.SystemClock.sleep(25)
        }
        val toggle = findButton(activity.window.decorView, "切换到黑色主题")
        assertNotNull("Missing theme switch", toggle)
        instrumentation.runOnMainSync { toggle!!.performClick() }
        instrumentation.waitForIdleSync()
        assertTrue(activity.getSharedPreferences(MainActivity.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean("dark_theme", false))
        assertNotNull(findButton(activity.window.decorView, "切换到白色主题"))
        assertUsableButton(activity, "7")
        assertUsableButton(activity, "下一格")
        assertEquals("待发送 1 / 200", activity.findViewById<android.widget.TextView>(R.id.queue_count).text.toString())
    }

    fun testEditingAndFormulasShareTheSecondScreen() {
        val activity = activity
        instrumentation.waitForIdleSync()

        val toolsTab = activity.findViewById<Button>(R.id.tab_editing)
        assertNotNull("Missing combined tools tab", toolsTab)
        instrumentation.runOnMainSync { toolsTab!!.performClick() }
        instrumentation.waitForIdleSync()

        listOf("编辑", "撤销", "复制", "粘贴", "自动求和", "平均值", "条件判断")
            .forEach { label -> assertUsableButton(activity, label) }
        assertAuxiliaryLayout(activity)
    }

    private fun assertAuxiliaryLayout(activity: MainActivity) {
        listOf("复制", "粘贴", "保存", "另存", "向上移动一格", "向左移动一格", "向右移动一格", "向下移动一格")
            .forEach { assertUsableButton(activity, it) }
        val up = bounds(activity, "向上移动一格")
        val left = bounds(activity, "向左移动一格")
        val right = bounds(activity, "向右移动一格")
        val down = bounds(activity, "向下移动一格")
        assertTrue(up.top < left.top && left.top < down.top)
        assertEquals(left.top, right.top)
        assertEquals(up.left, down.left)
        assertEquals(bounds(activity, "复制").top, bounds(activity, "粘贴").top)
        assertEquals(bounds(activity, "保存").top, bounds(activity, "另存").top)
        assertTrue(bounds(activity, "粘贴").right <= left.left)
        val pad = Rect(left).apply { union(up); union(right); union(down) }
        val files = Rect(bounds(activity, "复制")).apply {
            union(bounds(activity, "粘贴")); union(bounds(activity, "保存")); union(bounds(activity, "另存"))
        }
        assertEquals(files.width(), pad.width())
        assertEquals(files.height(), pad.height())
        assertEquals(files.top, pad.top)
        val upButton = findButton(activity.window.decorView, "向上移动一格")!!
        var accepted = true
        instrumentation.runOnMainSync {
            val now = android.os.SystemClock.uptimeMillis()
            val event = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN,
                upButton.width * .85f, upButton.height * .8f, 0)
            accepted = upButton.onTouchEvent(event)
            event.recycle()
            val cancel = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            upButton.onTouchEvent(cancel)
            cancel.recycle()
        }
        assertFalse("Transparent pointed-key corner must not start a press", accepted)
    }

    private fun assertUsableButton(activity: MainActivity, label: String) {
        val button = findButton(activity.window.decorView, label)
        assertNotNull("Missing visible button: $label", button)
        val visible = Rect()
        assertTrue("Button is off screen: $label", button!!.getGlobalVisibleRect(visible))
        val minimumPixels = ceil(48 * activity.resources.displayMetrics.density).toInt()
        assertTrue("Button is too narrow: $label", visible.width() >= minimumPixels)
        assertTrue("Button is too short: $label", visible.height() >= minimumPixels)
    }

    private fun findButton(view: View, label: String): Button? {
        if (view is Button && (view.text.toString() == label || view.contentDescription?.toString() == label)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findButton(view.getChildAt(index), label)?.let { return it }
            }
        }
        return null
    }

    private fun bounds(activity: MainActivity, label: String): Rect {
        val button = findButton(activity.window.decorView, label)!!
        return Rect().also { assertTrue(button.getGlobalVisibleRect(it)) }
    }
}
