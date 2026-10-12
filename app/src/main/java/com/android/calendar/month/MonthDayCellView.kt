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
import androidx.core.widget.TextViewCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * One day cell of the month grid. Three fixed slots, top to bottom: the day number,
 * the lunar label (when lunar display is on) and a dedicated row for event dots.
 * Each slot keeps its own space, so a lunar label never covers the day number and
 * the dots never cover the lunar label. Text uses auto-size, so it shrinks instead
 * of clipping at large font scales.
 */
class MonthDayCellView(context: Context) : MaterialCardView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(3), dp(2), dp(3))
    }
    private val dayLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, DAY_TEXT_SP)
        includeFontPadding = false
        maxLines = 1
    }
    private val lunarLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, LUNAR_TEXT_SP)
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
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            dayLabel, DAY_MIN_SP.toInt(), DAY_TEXT_SP.toInt(), 1, TypedValue.COMPLEX_UNIT_SP
        )
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            lunarLabel, LUNAR_MIN_SP.toInt(), LUNAR_TEXT_SP.toInt(), 1, TypedValue.COMPLEX_UNIT_SP
        )
        column.addView(dayLabel, LinearLayout.LayoutParams(dp(DAY_SLOT_DP), dp(DAY_SLOT_DP)))
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
                dp(DOT_SLOT_DP)
            ).apply { topMargin = dp(2) }
        )
        addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** Binds [cell] to a row that is [cellHeightPx] tall. */
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

        // The lunar slot is reserved whenever lunar display is on, so rows do not change height
        // while the text loads.
        lunarLabel.visibility = if (cell.lunarEnabled) View.VISIBLE else View.GONE
        lunarLabel.text = if (cell.lunarEnabled) cell.lunarText.orEmpty() else null
        lunarLabel.setTextColor(onSurfaceVariant)

        dotRow.removeAllViews()
        cell.eventColors.take(MAX_DOTS).forEach { color ->
            val dot = View(context).apply { background = circle(color, Color.TRANSPARENT, 0) }
            val lp = LinearLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP))
            lp.setMargins(dp(1), 0, dp(1), 0)
            dotRow.addView(dot, lp)
        }
        // The dot slot keeps its height even without events, so cells do not shift.
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
        private const val DAY_SLOT_DP = 28
        private const val DOT_SLOT_DP = 5
        private const val DOT_DP = 5
        private const val DAY_TEXT_SP = 14f
        private const val DAY_MIN_SP = 9f
        private const val LUNAR_TEXT_SP = 11f
        private const val LUNAR_MIN_SP = 7f

        /**
         * Smallest row height (px) that shows every slot without clipping at this font scale.
         * The grid never uses a shorter row, so large text grows the row instead of clipping.
         */
        @JvmStatic
        fun minRowHeightPx(density: Float, fontScale: Float, lunarEnabled: Boolean): Int {
            val scale = maxOf(1f, fontScale)
            val padding = 6 * density
            val day = maxOf(DAY_SLOT_DP * density, DAY_TEXT_SP * scale * density * LINE_FACTOR)
            val lunar = if (lunarEnabled) LUNAR_TEXT_SP * scale * density * LINE_FACTOR else 0f
            val dots = (2 + DOT_SLOT_DP) * density
            return Math.ceil((padding + day + lunar + dots).toDouble()).toInt()
        }

        private const val LINE_FACTOR = 1.3f
    }
}
