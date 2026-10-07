package com.remotenumpad.ui

import android.content.Context
import android.view.ViewGroup
import kotlin.math.roundToInt

/** Left file grid and right cross, identical on both pages; ordinary native buttons handle input. */
class AuxiliaryPanel(context: Context) : ViewGroup(context) {
    private val density = resources.displayMetrics.density
    private val gap = (8 * density).roundToInt()
    private var side = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        side = minOf(height, (168 * density).roundToInt(), (width - gap) / 2)
        getChildAt(0).measure(
            MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY))
        for (index in 1 until childCount) {
            getChildAt(index).measure(
                MeasureSpec.makeMeasureSpec((side * if (index == 1 || index == 4) .44f else .42f).roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((side * if (index == 1 || index == 4) .42f else .44f).roundToInt(), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val grid = getChildAt(0)
        val topInset = (height - side) / 2
        grid.layout(0, (height - grid.measuredHeight) / 2, grid.measuredWidth, (height + grid.measuredHeight) / 2)
        // CommandCatalog order: LEFT, UP, DOWN, RIGHT.
        val cells = arrayOf(0f to .29f, .29f to 0f, .29f to .56f, .56f to .29f)
        cells.forEachIndexed { index, (column, row) ->
            val button = getChildAt(index + 1)
            val x = width - side + (column * side).roundToInt()
            val y = topInset + (row * side).roundToInt()
            button.layout(x, y, x + button.measuredWidth, y + button.measuredHeight)
        }
    }
}
