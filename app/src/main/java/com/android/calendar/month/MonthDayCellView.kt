package com.android.calendar.month

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.android.calendar.Event
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/** A single independently laid out month cell; no Canvas coordinate math. */
class MonthDayCellView(context: Context) : MaterialCardView(context) {
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private val dayLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 14f
        includeFontPadding = false
    }
    private val events = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    init {
        radius = dp(8).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        column.addView(dayLabel, LinearLayout.LayoutParams.MATCH_PARENT, dp(32))
        // Events fill the remaining vertical space; weight is applied via
        // LayoutParams because LinearLayout has no 4-arg addView overload.
        column.addView(
            events,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    fun bind(day: Int, currentMonth: Boolean, today: Boolean, assignedEvents: List<Event>) {
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)

        setCardBackgroundColor(surface)
        dayLabel.text = day.toString()
        dayLabel.alpha = if (currentMonth) 1f else .55f
        dayLabel.setTextColor(if (today) onPrimary else if (currentMonth) onSurface else onSurfaceVariant)
        dayLabel.background = if (today) shapeDrawable(primary, dp(16)) else ColorDrawable(Color.TRANSPARENT)

        events.removeAllViews()
        assignedEvents.take(3).forEach { event ->
            val color = event.color
            val chip = TextView(context).apply {
                text = event.title ?: ""
                textSize = 10f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(onPrimary)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(1), dp(5), dp(1))
                background = shapeDrawable(color, dp(8))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24))
            lp.setMargins(0, dp(2), 0, 0)
            events.addView(chip, lp)
        }
    }

    private fun shapeDrawable(color: Int, radius: Int): MaterialShapeDrawable =
        MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, radius.toFloat())
                .build()
        ).apply { fillColor = android.content.res.ColorStateList.valueOf(color) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + .5f).toInt()
}
