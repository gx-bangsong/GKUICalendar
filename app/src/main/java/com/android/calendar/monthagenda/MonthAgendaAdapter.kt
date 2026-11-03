/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.monthagenda

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.format.DateFormat as AndroidDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.Event
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import ws.xsoh.etar.R

/**
 * Lists the selected day: subscription cards first, then all-day events, then timed
 * events. Times are shown in the home time zone.
 */
class MonthAgendaAdapter(
    private val context: Context,
    timeZoneId: String,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class EventHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorBar: View = view.findViewById(R.id.agenda_event_color)
        val title: TextView = view.findViewById(R.id.agenda_event_title)
        val time: TextView = view.findViewById(R.id.agenda_event_time)
        val location: TextView = view.findViewById(R.id.agenda_event_location)
    }

    class CardHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.agenda_card_icon)
        val text: TextView = view.findViewById(R.id.agenda_card_text)
        val source: TextView = view.findViewById(R.id.agenda_card_source)
    }

    private val items = ArrayList<AgendaItem>()
    private val timeFormat: SimpleDateFormat = SimpleDateFormat(
        if (AndroidDateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        Locale.getDefault()
    ).apply { timeZone = TimeZone.getTimeZone(timeZoneId) }

    fun submit(newItems: List<AgendaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is AgendaItem.Card) TYPE_CARD else TYPE_EVENT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CARD) {
            CardHolder(inflater.inflate(R.layout.item_agenda_card, parent, false))
        } else {
            EventHolder(inflater.inflate(R.layout.item_agenda_event, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AgendaItem.Card -> bindCard(holder as CardHolder, item)
            is AgendaItem.Entry -> bindEvent(holder as EventHolder, item.event)
        }
    }

    private fun bindCard(holder: CardHolder, card: AgendaItem.Card) {
        holder.text.text = card.text
        holder.source.text = card.source
        holder.icon.setImageResource(card.iconRes)
        // A subscription with its own color keeps it on the icon; the rest use the theme color.
        val tint = card.color ?: MaterialColors.getColor(
            holder.icon, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY
        )
        holder.icon.imageTintList = ColorStateList.valueOf(tint)
    }

    private fun bindEvent(holder: EventHolder, event: Event) {
        holder.colorBar.setBackgroundColor(event.color)
        holder.title.text = event.title?.toString()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.no_title_label)
        holder.time.text = if (event.allDay) {
            context.getString(R.string.agenda_all_day)
        } else {
            timeFormat.format(Date(event.startMillis)) + " – " + timeFormat.format(Date(event.endMillis))
        }
        val location = event.location?.toString()
        holder.location.visibility = if (location.isNullOrEmpty()) View.GONE else View.VISIBLE
        holder.location.text = location
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount(): Int = items.size

    companion object {
        private const val TYPE_EVENT = 0
        private const val TYPE_CARD = 1
    }
}
