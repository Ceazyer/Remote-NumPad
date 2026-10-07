package com.remotenumpad.ui

import android.content.Context
import android.view.ViewGroup
import kotlin.math.roundToInt

/** Equal physical columns, including the double-width zero; no scroll container. */
class CommandGrid(context: Context, private val columns: Int, private val spans: List<Int>, gutterDp: Int = 2) : ViewGroup(context) {
    private val rows = (spans.sum() + columns - 1) / columns
    private val gutter = (gutterDp * resources.displayMetrics.density).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        var cell = 0
        for (index in 0 until childCount) {
            val span = spans[index]
            val column = cell % columns
            val row = cell / columns
            val left = (width * column.toFloat() / columns).roundToInt()
            val right = (width * (column + span).toFloat() / columns).roundToInt()
            val top = (height * row.toFloat() / rows).roundToInt()
            val bottom = (height * (row + 1).toFloat() / rows).roundToInt()
            getChildAt(index).measure(
                MeasureSpec.makeMeasureSpec((right - left - 2 * gutter).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((bottom - top - 2 * gutter).coerceAtLeast(0), MeasureSpec.EXACTLY))
            cell += span
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var cell = 0
        for (index in 0 until childCount) {
            val column = cell % columns
            val row = cell / columns
            val x = (width * column.toFloat() / columns).roundToInt() + gutter
            val y = (height * row.toFloat() / rows).roundToInt() + gutter
            val child = getChildAt(index)
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            cell += spans[index]
        }
    }
}
