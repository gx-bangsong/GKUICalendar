package com.android.calendar.shift

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ShiftTouchOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {


    var onTouchMoving: ((Float, Float) -> Unit)? = null
    var onTouchStopped: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
            }
            MotionEvent.ACTION_MOVE -> {
                onTouchMoving?.invoke(event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onTouchStopped?.invoke()
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
