package com.remotenumpad.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.Drawable
import kotlin.math.hypot

enum class DirectionTip { UP, DOWN, LEFT, RIGHT }

/** Drawn locally; no bitmap assets, touch handlers or animation delays. */
class SoftButtonDrawable(private val palette: SoftPalette, private val density: Float,
                         private val tip: DirectionTip? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var inset = false
    private var focused = false

    private fun outline(face: RectF): Path {
        if (tip == null) return Path().apply {
            val radius = minOf(17 * density, face.height() / 3)
            addRoundRect(face, radius, radius, Path.Direction.CW)
        }
        val normalized = listOf(0f to 0f, 1f to 0f, 1f to .55f, .5f to 1f, 0f to .55f)
        val vertices = normalized.map { (x, y) ->
            val rotated = when (tip) {
                DirectionTip.UP -> 1f - x to 1f - y
                DirectionTip.LEFT -> 1f - y to x
                DirectionTip.RIGHT -> y to 1f - x
                else -> x to y
            }
            face.left + rotated.first * face.width() to face.top + rotated.second * face.height()
        }
        val radius = minOf(10 * density, minOf(face.width(), face.height()) * .18f)
        val cuts = vertices.mapIndexed { index, vertex ->
            fun cut(other: Pair<Float, Float>): Pair<Float, Float> {
                val dx = other.first - vertex.first
                val dy = other.second - vertex.second
                val length = hypot(dx, dy)
                val distance = minOf(radius, length / 2)
                return vertex.first + dx * distance / length to vertex.second + dy * distance / length
            }
            cut(vertices[(index + 4) % 5]) to cut(vertices[(index + 1) % 5])
        }
        return Path().apply {
            moveTo(cuts[0].first.first, cuts[0].first.second)
            vertices.forEachIndexed { index, vertex ->
                quadTo(vertex.first, vertex.second, cuts[index].second.first, cuts[index].second.second)
                val next = cuts[(index + 1) % 5].first
                lineTo(next.first, next.second)
            }
            close()
        }
    }

    fun containsTouch(x: Float, y: Float): Boolean {
        val region = Region()
        region.setPath(outline(RectF(bounds)), Region(bounds))
        return region.contains(x.toInt(), y.toInt())
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val nextInset = state.contains(android.R.attr.state_pressed) || state.contains(android.R.attr.state_selected)
        val nextFocus = state.contains(android.R.attr.state_focused)
        if (nextInset == inset && nextFocus == focused) return false
        inset = nextInset
        focused = nextFocus
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val padding = 6 * density
        val face = RectF(bounds.left + padding, bounds.top + padding, bounds.right - padding, bounds.bottom - padding)
        val shape = outline(face)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = palette.surface
        if (inset) {
            canvas.drawPath(shape, paint)
            canvas.save()
            canvas.clipPath(shape)
            val outside = RectF(face).apply { inset(-2 * density, -2 * density) }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.setShadowLayer(4 * density, 2 * density, 2 * density, palette.shadow)
            canvas.drawPath(outline(outside), paint)
            paint.setShadowLayer(4 * density, -2 * density, -2 * density, palette.highlight)
            canvas.drawPath(outline(outside), paint)
            paint.clearShadowLayer()
            canvas.restore()
        } else {
            paint.setShadowLayer(4 * density, 2 * density, 2 * density, palette.shadow)
            canvas.drawPath(shape, paint)
            paint.setShadowLayer(4 * density, -2 * density, -2 * density, palette.highlight)
            canvas.drawPath(shape, paint)
            paint.clearShadowLayer()
            canvas.drawPath(shape, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (focused) 2 * density else .65f * density
        paint.color = if (focused) palette.accent else palette.rim
        canvas.drawPath(shape, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
