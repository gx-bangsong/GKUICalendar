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
 * One day cell of the month grid, with three slots from top to bottom: the day number,
 * the lunar label (only when it is shown) and a dedicated row for event dots.
 *
 * Every slot is sized by [CellSlotMath], the same object the grid uses for the row height, so the row height the
 * grid asks for is the height the slots actually take. The day number and the dot row never
 * shrink. Their sizes follow the system font scale through sp and the density. The lunar
 * label may shrink its width (one line) to fit, but its height never changes.
 */
class MonthDayCellView(context: Context) : MaterialCardView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(3), dp(2), dp(3))
    }
    private val dayLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, CellSlotMath.DAY_TEXT_SP)
        includeFontPadding = false
        maxLines = 1
    }
    private val lunarLabel = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, CellSlotMath.LUNAR_TEXT_SP)
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
        // Width only: the lunar label is one line, so uniform auto-size shrinks the glyphs
        // to fit the cell width and leaves the slot height untouched.
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            lunarLabel, CellSlotMath.LUNAR_MIN_SP.toInt(), CellSlotMath.LUNAR_TEXT_SP.toInt(), 1, TypedValue.COMPLEX_UNIT_SP
        )
        column.addView(dayLabel)
        column.addView(lunarLabel)
        column.addView(
            dotRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(CellSlotMath.DOT_SLOT_DP)
            ).apply { topMargin = dp(CellSlotMath.DOT_TOP_MARGIN_DP) }
        )
        addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** Binds [cell]. The row height is set by the grid; [cellHeightPx] is kept for callers. */
    fun bind(cell: MonthGridAdapter.Cell, cellHeightPx: Int) {
        val density = resources.displayMetrics.density
        val fontScale = resources.configuration.fontScale
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

        // Day slot: a square, so the today and selected circles stay round at every font scale.
        val daySlot = CellSlotMath.daySlotPx(density, fontScale)
        dayLabel.layoutParams = LinearLayout.LayoutParams(daySlot, daySlot)
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

        // Lunar slot exists only when the grid decided to show it for this layout.
        if (cell.lunarEnabled) {
            lunarLabel.visibility = View.VISIBLE
            lunarLabel.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                CellSlotMath.lunarSlotPx(density, fontScale)
            )
            lunarLabel.text = cell.lunarText.orEmpty()
            lunarLabel.setTextColor(onSurfaceVariant)
        } else {
            lunarLabel.visibility = View.GONE
            lunarLabel.text = null
        }

        dotRow.removeAllViews()
        cell.eventColors.take(CellSlotMath.MAX_DOTS).forEach { color ->
            val dot = View(context).apply { background = circle(color, Color.TRANSPARENT, 0) }
            val lp = LinearLayout.LayoutParams(dp(CellSlotMath.DOT_SLOT_DP), dp(CellSlotMath.DOT_SLOT_DP))
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
}
