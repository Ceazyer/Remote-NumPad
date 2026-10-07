package com.remotenumpad.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.widget.Button

class SoftKeyButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Button(context, attrs) {
    private var icon: Drawable? = null
    private var formulaMark: String? = null
    private var formulaCaption = ""
    private var iconCaption = false
    private var directionTip: DirectionTip? = null
    private var palette = SoftPalette.forDark(false)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isAllCaps = false
        gravity = Gravity.CENTER
        minimumWidth = (48 * resources.displayMetrics.density).toInt()
        minimumHeight = minimumWidth
        minWidth = minimumWidth
        minHeight = minimumHeight
        setPadding(0, 0, 0, 0)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        stateListAnimator = null
        defaultFocusHighlightEnabled = false
        backgroundTintList = null
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun applyAppearance(colors: SoftPalette, accent: Boolean = false, iconResource: Int = 0,
                        mark: String? = null, caption: String = "", showCaption: Boolean = false,
                        tip: DirectionTip? = null) {
        palette = colors
        directionTip = tip
        background = SoftButtonDrawable(colors, resources.displayMetrics.density, tip)
        setTextColor(if (accent) colors.accent else colors.text)
        icon = if (iconResource == 0) null else context.getDrawable(iconResource)!!.mutate().apply {
            setTint(if (accent) colors.accent else colors.muted)
        }
        formulaMark = mark
        formulaCaption = caption
        iconCaption = showCaption
        if (icon != null || mark != null) text = ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        icon?.let {
            val side = ((if (iconCaption) 22 else 26) * density).toInt().coerceAtMost(minOf(width, height) - (16 * density).toInt())
            val centerY = (height * when (directionTip) { DirectionTip.DOWN -> .44f; DirectionTip.UP -> .56f; else -> .5f }).toInt() - (if (iconCaption) 7 * density else 0f).toInt()
            val centerX = (width * when (directionTip) { DirectionTip.RIGHT -> .44f; DirectionTip.LEFT -> .56f; else -> .5f }).toInt()
            it.setBounds(centerX - side / 2, centerY - side / 2, centerX + side / 2, centerY + side / 2)
            it.draw(canvas)
            if (iconCaption) {
                paint.textAlign = Paint.Align.CENTER
                paint.color = palette.muted
                paint.textSize = 10 * resources.displayMetrics.scaledDensity
                canvas.drawText(formulaCaption, width / 2f, height / 2f + 17 * density, paint)
            }
        }
        formulaMark?.let {
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.color = palette.accent
            paint.textSize = 22 * resources.displayMetrics.scaledDensity
            val maxWidth = width - 20 * density
            if (paint.measureText(it) > maxWidth) paint.textSize *= maxWidth / paint.measureText(it)
            val middle = height / 2f
            canvas.drawText(it, width / 2f, middle - 2 * density, paint)
            paint.color = palette.muted
            paint.textSize = 12 * resources.displayMetrics.scaledDensity
            if (paint.measureText(formulaCaption) > maxWidth) paint.textSize *= maxWidth / paint.measureText(formulaCaption)
            canvas.drawText(formulaCaption, width / 2f, middle + 18 * density, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val shape = background as? SoftButtonDrawable
        if (directionTip != null && shape != null && !shape.containsTouch(event.x, event.y)) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) return false
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                return try { super.onTouchEvent(cancel) } finally { cancel.recycle() }
            }
        }
        return super.onTouchEvent(event)
    }
}
