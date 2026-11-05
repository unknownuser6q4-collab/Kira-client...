package com.moonlight.bedrockclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

/** The Freelook eye shortcut: a small floating bubble that toggles the orbit state. */
class EyeHandleView(context: Context) : View(context) {

    var active: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var accent: Int = GuiTheme.accentAt(0)
        set(value) {
            field = value
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(2f)

        fill.color = if (active) accent else 0xE6101824.toInt()
        canvas.drawCircle(cx, cy, radius, fill)

        ring.color = if (active) 0xFFFFFFFF.toInt() else accent
        ring.strokeWidth = dp(1.5f)
        canvas.drawCircle(cx, cy, radius - dp(1.5f), ring)

        val eyeWidth = radius * 1.15f
        val eyeHeight = radius * 0.62f
        val eye = Path()
        eye.moveTo(cx - eyeWidth / 2f, cy)
        eye.quadTo(cx, cy - eyeHeight, cx + eyeWidth / 2f, cy)
        eye.quadTo(cx, cy + eyeHeight, cx - eyeWidth / 2f, cy)
        eye.close()

        fill.color = if (active) 0xFF071018.toInt() else 0xFFDCE4F5.toInt()
        canvas.drawPath(eye, fill)

        fill.color = if (active) 0xFF071018.toInt() else accent
        canvas.drawCircle(cx, cy, radius * 0.24f, fill)
    }

    private fun dp(value: Float): Float = value * density
}
