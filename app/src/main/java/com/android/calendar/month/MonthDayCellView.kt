/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.month

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * One day cell of the month grid. Shows the day number, an optional lunar label
 * and up to three event color dots. Event titles are not drawn here.
 */
class MonthDayCellView(context: Context) : MaterialCardView(context) {
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(2), dp(3), dp(2), dp(3))
    }
    private val dayLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 14f
        includeFontPadding = false
        maxLines = 1
    }
    private val lunarLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 10f
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val dotRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    init {
        radius = dp(8).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        isClickable = true
        isFocusable = true
        val outValue = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
            foreground = ContextCompat.getDrawable(context, outValue.resourceId)
        }
        column.addView(
            dayLabel,
            LinearLayout.LayoutParams(dp(28), dp(28))
        )
        column.addView(
            lunarLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        column.addView(
            dotRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** Binds [cell]. The lunar label is hidden when the row is too short for it. */
    fun bind(cell: MonthGridAdapter.Cell, cellHeightPx: Int) {
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val onSurfaceVariant = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY
        )
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
        val onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
        val secondaryContainer = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSecondaryContainer, surface
        )

        setCardBackgroundColor(if (cell.selected) secondaryContainer else surface)
        dayLabel.text = cell.dayOfMonth.toString()
        dayLabel.alpha = if (cell.currentMonth) 1f else 0.5f
        dayLabel.setTextColor(
            when {
                cell.today -> onPrimary
                cell.currentMonth -> onSurface
                else -> onSurfaceVariant
            }
        )
        dayLabel.background = when {
            cell.today -> circle(primary, Color.TRANSPARENT, 0)
            cell.selected -> circle(Color.TRANSPARENT, primary, dp(2))
            else -> null
        }

        val showLunar = cellHeightPx >= dp(60) && !cell.lunarText.isNullOrEmpty()
        lunarLabel.visibility = if (showLunar) View.VISIBLE else View.GONE
        lunarLabel.text = if (showLunar) cell.lunarText else null
        lunarLabel.setTextColor(onSurfaceVariant)

        dotRow.removeAllViews()
        cell.eventColors.take(MAX_DOTS).forEach { color ->
            val dot = View(context).apply { background = circle(color, Color.TRANSPARENT, 0) }
            val lp = LinearLayout.LayoutParams(dp(5), dp(5))
            lp.setMargins(dp(1), dp(2), dp(1), 0)
            dotRow.addView(dot, lp)
        }
        // Keep the dot row height constant so cells do not shift when counts change.
        dotRow.visibility = if (cell.eventColors.isEmpty()) View.INVISIBLE else View.VISIBLE
        contentDescription = cell.description
    }

    private fun circle(fill: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        private const val MAX_DOTS = 3
    }
}
