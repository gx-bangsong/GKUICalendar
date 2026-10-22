/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Debug-only geometry log for the lunar settings list alignment investigation.
 *
 * It records layout numbers only: screen x, sizes, paddings, translation, layout direction, and
 * the text layout start. It does not record text, preference values, or any user data. It runs
 * only in debuggable builds, and it does not change any layout value.
 *
 * Remove this file after the alignment cause is confirmed.
 */
internal object LunarAlignDebug {
    private const val TAG = "LunarAlignDebug"

    fun enabled(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun dump(list: RecyclerView, reason: String, seq: Int) {
        Log.d(
            TAG,
            "list seq=$seq reason=$reason screenX=${screenX(list)} width=${list.width} " +
                "padStart=${list.paddingStart} padEnd=${list.paddingEnd} " +
                "layoutDir=${list.layoutDirection} children=${list.childCount}"
        )
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val holder = list.getChildViewHolder(child)
            val title = child.findViewById<TextView>(android.R.id.title)
            Log.d(
                TAG,
                "  item seq=$seq pos=${holder.bindingAdapterPosition} " +
                    "type=${holder.itemViewType} cls=${child.javaClass.simpleName} " +
                    "screenX=${screenX(child)} width=${child.width} padStart=${child.paddingStart} " +
                    "tx=${child.translationX} layoutDir=${child.layoutDirection} " +
                    titleInfo(title)
            )
        }
    }

    private fun titleInfo(title: TextView?): String {
        if (title == null) return "title=none"
        val layout = title.layout
        val lineLeft = layout?.getLineLeft(0) ?: 0f
        val textStart = screenX(title) + title.compoundPaddingLeft + lineLeft
        return "titleScreenX=${screenX(title)} titleWidth=${title.width} " +
            "titlePadStart=${title.paddingStart} compoundLeft=${title.compoundPaddingLeft} " +
            "lineLeft=$lineLeft textStartScreenX=$textStart " +
            "titleTx=${title.translationX} titleLayoutDir=${title.layoutDirection} " +
            "layoutReady=${layout != null}"
    }

    private fun screenX(view: View): Int {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return loc[0]
    }
}
