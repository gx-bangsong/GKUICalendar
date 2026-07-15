package com.android.calendar.shift

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ShiftTouchOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trailPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 20f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val trailPath = Path()
    var onTouchMoving: ((Float, Float) -> Unit)? = null
    var onTouchStopped: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                trailPath.reset()
                trailPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                trailPath.lineTo(x, y)
                onTouchMoving?.invoke(event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trailPath.reset()
                onTouchStopped?.invoke()
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(trailPath, trailPaint)
    }
}
