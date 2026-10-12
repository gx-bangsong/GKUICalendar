/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.home

import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.calendar.Event
import com.android.calendar.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Lists the events of the selected day. Times are shown in the home time zone. */
class HomeEventsAdapter(
    private val context: Context,
    timeZoneId: String,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<HomeEventsAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val colorBar: View = view.findViewById(R.id.home_event_color)
        val title: TextView = view.findViewById(R.id.home_event_title)
        val time: TextView = view.findViewById(R.id.home_event_time)
        val location: TextView = view.findViewById(R.id.home_event_location)
    }

    private val items = ArrayList<Event>()
    private val timeFormat: SimpleDateFormat = SimpleDateFormat(
        if (AndroidDateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        Locale.getDefault()
    ).apply { timeZone = TimeZone.getTimeZone(timeZoneId) }

    fun submit(events: List<Event>) {
        items.clear()
        items.addAll(events)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_home_event, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val event = items[position]
        holder.colorBar.setBackgroundColor(event.color)
        holder.title.text = event.title?.toString()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.no_title_label)
        holder.time.text = if (event.allDay) {
            context.getString(R.string.home_all_day)
        } else {
            timeFormat.format(Date(event.startMillis)) + " – " + timeFormat.format(Date(event.endMillis))
        }
        val location = event.location?.toString()
        holder.location.visibility = if (location.isNullOrEmpty()) View.GONE else View.VISIBLE
        holder.location.text = location
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount(): Int = items.size
}
