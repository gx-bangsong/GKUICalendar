/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar.agenda;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import androidx.core.graphics.ColorUtils;
import android.database.Cursor;
import android.graphics.Paint;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.calendar.ColorChipView;
import com.android.calendar.DynamicTheme;
import com.android.calendar.Utils;
import com.android.calendar.calendarcommon2.Time;
import com.android.calendar.event.EventExtraUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class AgendaAdapter extends ResourceCursorAdapter {
    private final String mNoTitleLabel;
    private final Resources mResources;
    private final int mDeclinedColor;
    private final int mStandardColor;
    private final int mWhereColor;
    private final int mWhereDeclinedColor;
    // Note: Formatter is not thread safe. Fine for now as it is only used by the main thread.
    private final Formatter mFormatter;
    private final StringBuilder mStringBuilder;
    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            notifyDataSetChanged();
        }
    };
    private float mScale;
    private int COLOR_CHIP_ALL_DAY_HEIGHT;
    private int COLOR_CHIP_HEIGHT;

    public AgendaAdapter(Context context, int resource) {
        super(context, resource, null);

        mResources = context.getResources();
        mNoTitleLabel = mResources.getString(R.string.no_title_label);
        mDeclinedColor = DynamicTheme.getColor(context, "agenda_item_declined_color");
        mStandardColor = DynamicTheme.getColor(context, "agenda_item_standard_color");
        mWhereDeclinedColor = DynamicTheme.getColor(context, "agenda_item_where_declined_text_color");
        mWhereColor = DynamicTheme.getColor(context, "agenda_item_where_text_color");
        mStringBuilder = new StringBuilder(50);
        mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

        COLOR_CHIP_ALL_DAY_HEIGHT = mResources.getInteger(R.integer.color_chip_all_day_height);
        COLOR_CHIP_HEIGHT = mResources.getInteger(R.integer.color_chip_height);
        if (mScale == 0) {
            mScale = mResources.getDisplayMetrics().density;
            if (mScale != 1) {
                COLOR_CHIP_ALL_DAY_HEIGHT *= mScale;
                COLOR_CHIP_HEIGHT *= mScale;
            }
        }

    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Resolve all view ids from the current view on every bind. The
        // previous version cached the lookup in a tag-attached ViewHolder
        // and re-used it across recycled views, but AgendaWindowAdapter
        // hands this adapter recycled views from different layouts (the
        // phone layout-sw600dp variant uses a GridLayout with a different
        // id set). Cached lookups from one layout would silently point at
        // null in the next, leading to the NPEs the original 7d993e7
        // commit only patched one symptom of.
        ViewHolder holder = new ViewHolder();
        holder.card = view.findViewById(R.id.agenda_event_card);
        holder.agendaDateColumn = view.findViewById(R.id.agenda_event_date_column);
        holder.agendaDate = view.findViewById(R.id.agenda_event_date);
        holder.agendaDay = view.findViewById(R.id.agenda_event_day);
        holder.title = (TextView) view.findViewById(R.id.title);
        holder.when = (TextView) view.findViewById(R.id.when);
        holder.where = (TextView) view.findViewById(R.id.where);
        holder.textContainer = (LinearLayout)
                view.findViewById(R.id.agenda_item_text_container);
        holder.selectedMarker = view.findViewById(R.id.selected_marker);
        holder.colorChip = (ColorChipView) view.findViewById(R.id.agenda_item_color);
        holder.timelineLine = view.findViewById(R.id.agenda_timeline_line);

        // Re-attach the holder: AgendaByDayAdapter.getView reads the tag
        // unguarded, and AgendaWindowAdapter/AgendaListView rely on it via
        // instanceof checks (selection marker, day graying, goto time).
        // Unlike the pre-53a0d9a cached holder, this one is resolved from the
        // current view on every bind, so its lookups always match the layout
        // of the view it is attached to.
        view.setTag(holder);

        holder.startTimeMilli = cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
        // Fade text if event was declined and set the color chip mode (response
        boolean allDay = cursor.getInt(AgendaWindowAdapter.INDEX_ALL_DAY) != 0;
        holder.allDay = allDay;
        int selfAttendeeStatus = cursor.getInt(AgendaWindowAdapter.INDEX_SELF_ATTENDEE_STATUS);
        if (selfAttendeeStatus == Attendees.ATTENDEE_STATUS_DECLINED) {
            if (holder.title != null) holder.title.setTextColor(mDeclinedColor);
            if (holder.when != null) holder.when.setTextColor(mWhereDeclinedColor);
            if (holder.where != null) holder.where.setTextColor(mWhereDeclinedColor);
            if (holder.colorChip != null) holder.colorChip.setDrawStyle(ColorChipView.DRAW_FADED);
        } else {
            if (holder.title != null) holder.title.setTextColor(mStandardColor);
            if (holder.when != null) holder.when.setTextColor(mWhereColor);
            if (holder.where != null) holder.where.setTextColor(mWhereColor);
            if (selfAttendeeStatus == Attendees.ATTENDEE_STATUS_INVITED) {
                if (holder.colorChip != null) holder.colorChip.setDrawStyle(ColorChipView.DRAW_BORDER);
            } else {
                if (holder.colorChip != null) holder.colorChip.setDrawStyle(ColorChipView.DRAW_FULL);
            }
        }

        // Set the size of the color chip
        if (holder.colorChip != null) {
            ViewGroup.LayoutParams params = holder.colorChip.getLayoutParams();
            if (allDay) {
                params.height = COLOR_CHIP_ALL_DAY_HEIGHT;
            } else {
                params.height = COLOR_CHIP_HEIGHT;
            }
            holder.colorChip.setLayoutParams(params);
        }

        // Deal with exchange events that the owner cannot respond to
        int canRespond = cursor.getInt(AgendaWindowAdapter.INDEX_CAN_ORGANIZER_RESPOND);
        if (canRespond == 0) {
            String owner = cursor.getString(AgendaWindowAdapter.INDEX_OWNER_ACCOUNT);
            String organizer = cursor.getString(AgendaWindowAdapter.INDEX_ORGANIZER);
            if (owner.equals(organizer)) {
                if (holder.colorChip != null) holder.colorChip.setDrawStyle(ColorChipView.DRAW_FULL);
                if (holder.title != null) holder.title.setTextColor(mStandardColor);
                if (holder.when != null) holder.when.setTextColor(mStandardColor);
                if (holder.where != null) holder.where.setTextColor(mStandardColor);
            }
        }

        int status = cursor.getInt(AgendaWindowAdapter.INDEX_STATUS);
        if (status == Events.STATUS_CANCELED) {
            if (holder.title != null) {
                holder.title.setPaintFlags(holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }

        TextView title = holder.title;
        TextView when = holder.when;
        TextView where = holder.where;

        holder.instanceId = cursor.getLong(AgendaWindowAdapter.INDEX_INSTANCE_ID);

        /* Calendar Color */
        int color = Utils.getDisplayColorFromColor(context, cursor.getInt(AgendaWindowAdapter.INDEX_COLOR));
        if (holder.colorChip != null) holder.colorChip.setColor(color);
        if (holder.timelineLine != null) {
            holder.timelineLine.setBackgroundTintList(ColorStateList.valueOf(color));
        }
        if (holder.card != null) holder.card.setCardBackgroundColor(color);
        // Picking the contrasting text color used to pass holder.title as
        // the View argument to MaterialColors.getColor, but that overload
        // is declared @NonNull and reads view.getContext() on its first
        // line. On the sw600dp layout agenda_item defines no @+id/title view,
        // so holder.title is null and the call NPE'd on tablet. Use the
        // Context-based overload (which is also @NonNull but we already
        // have context as the bindView parameter) and only paint the
        // computed event color onto text views that actually exist.
        int eventTextColor;
        if (holder.title != null) {
            int onSurface = com.google.android.material.color.MaterialColors.getColor(
                    context, com.google.android.material.R.attr.colorOnSurface,
                    "AgendaAdapter");
            int onPrimary = com.google.android.material.color.MaterialColors.getColor(
                    context, com.google.android.material.R.attr.colorOnPrimary,
                    "AgendaAdapter");
            eventTextColor = ColorUtils.calculateContrast(onSurface, color)
                    >= ColorUtils.calculateContrast(onPrimary, color) ? onSurface : onPrimary;
            holder.title.setTextColor(eventTextColor);
        } else {
            eventTextColor = 0;
        }
        if (holder.when != null) holder.when.setTextColor(eventTextColor);
        if (holder.where != null) holder.where.setTextColor(eventTextColor);

        // What
        String titleString = cursor.getString(AgendaWindowAdapter.INDEX_TITLE);
        if (titleString == null || titleString.length() == 0) {
            titleString = mNoTitleLabel;
        }

        String customAppUri = cursor.getString(AgendaWindowAdapter.INDEX_CUSTOM_APP_URI);
        if (customAppUri != null && customAppUri.startsWith("etar://event_type/")) {
            String eventType = customAppUri.substring("etar://event_type/".length());
            titleString = "⭐ " + titleString;

            long begin = cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
            long now = System.currentTimeMillis();
            String extraInfo = "";
            if (EventExtraUtils.EVENT_TYPE_ANNIVERSARY.equals(eventType) || EventExtraUtils.EVENT_TYPE_BIRTHDAY.equals(eventType)) {
                extraInfo = EventExtraUtils.getAnniversaryDisplayString(context, begin, now);
            } else if (EventExtraUtils.EVENT_TYPE_COUNTDOWN.equals(eventType)) {
                extraInfo = EventExtraUtils.getCountdownDisplayString(context, begin, now);
            }

            if (!TextUtils.isEmpty(extraInfo)) {
                titleString += " (" + extraInfo + ")";
            }
        }

        title.setText(titleString);

        // When
        long begin = cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
        long end = cursor.getLong(AgendaWindowAdapter.INDEX_END);
        String eventTz = cursor.getString(AgendaWindowAdapter.INDEX_TIME_ZONE);
        int flags = 0;
        String whenString;
        // It's difficult to update all the adapters so just query this each
        // time we need to build the view.
        String tzString = Utils.getTimeZone(context, mTZUpdater);
        if (allDay) {
            tzString = Time.TIMEZONE_UTC;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME;
        }
        if (DateFormat.is24HourFormat(context)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        mStringBuilder.setLength(0);
        whenString = DateUtils.formatDateRange(context, mFormatter, begin, end, flags, tzString)
                .toString();
        if (!allDay && !TextUtils.equals(tzString, eventTz)) {
            String displayName;
            // Figure out if this is in DST
            Time date = new Time(tzString);
            date.set(begin);

            TimeZone tz = TimeZone.getTimeZone(tzString);
            if (tz == null || tz.getID().equals("GMT")) {
                displayName = tzString;
            } else {
                displayName = tz.getDisplayName(false, TimeZone.SHORT);
            }
            whenString += " (" + displayName + ")";
        }
        when.setText(whenString);

        // Where
        String whereString = cursor.getString(AgendaWindowAdapter.INDEX_EVENT_LOCATION);
        if (whereString != null && whereString.length() > 0) {
            where.setVisibility(View.VISIBLE);
            where.setText(whereString);
        } else {
            where.setVisibility(View.GONE);
        }
    }

    static class ViewHolder {

        public static final int DECLINED_RESPONSE = 0;
        public static final int TENTATIVE_RESPONSE = 1;
        public static final int ACCEPTED_RESPONSE = 2;

        /* Event */
        TextView title;
        TextView when;
        TextView where;
        View selectedMarker;
        LinearLayout textContainer;
        long instanceId;
        MaterialCardView card;
        View agendaDateColumn;
        TextView agendaDate;
        TextView agendaDay;
        ColorChipView colorChip;
        View timelineLine;
        long startTimeMilli;
        boolean allDay;
        boolean grayed;
        int julianDay;
    }
}

