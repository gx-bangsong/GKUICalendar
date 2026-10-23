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
        holder.content = view.findViewById(R.id.agenda_item_content);

        // Re-attach the holder: AgendaByDayAdapter.getView reads the tag
        // unguarded, and AgendaWindowAdapter/AgendaListView rely on it via
        // instanceof checks (selection marker, day graying, goto time).
        // Unlike the pre-53a0d9a cached holder, this one is resolved from the
        // current view on every bind, so its lookups always match the layout
        // of the view it is attached to.
        view.setTag(holder);

        holder.startTimeMilli = cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
        // The attendee state decides the card style. It is recomputed on every bind, so a
        // recycled view never keeps the fill, outline or strike-through of an earlier item.
        boolean allDay = cursor.getInt(AgendaWindowAdapter.INDEX_ALL_DAY) != 0;
        holder.allDay = allDay;
        int selfAttendeeStatus = cursor.getInt(AgendaWindowAdapter.INDEX_SELF_ATTENDEE_STATUS);
        boolean declined = selfAttendeeStatus == Attendees.ATTENDEE_STATUS_DECLINED;
        boolean invited = selfAttendeeStatus == Attendees.ATTENDEE_STATUS_INVITED;
        // Exchange events the owner cannot answer, where the owner is the organizer, stay solid.
        int canRespond = cursor.getInt(AgendaWindowAdapter.INDEX_CAN_ORGANIZER_RESPOND);
        if (canRespond == 0) {
            String owner = cursor.getString(AgendaWindowAdapter.INDEX_OWNER_ACCOUNT);
            String organizer = cursor.getString(AgendaWindowAdapter.INDEX_ORGANIZER);
            if (owner.equals(organizer)) {
                invited = false;
            }
        }
        boolean canceled =
                cursor.getInt(AgendaWindowAdapter.INDEX_STATUS) == Events.STATUS_CANCELED;
        AgendaItemStyle style = AgendaItemStyle.resolve(declined, invited, canceled, allDay);

        TextView title = holder.title;
        TextView when = holder.when;
        TextView where = holder.where;

        // Strike-through is set or cleared on every bind, so no flag survives a recycled view.
        if (title != null) {
            int flags = title.getPaintFlags();
            title.setPaintFlags(style.strikeThrough
                    ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                    : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }

        holder.instanceId = cursor.getLong(AgendaWindowAdapter.INDEX_INSTANCE_ID);

        /* Calendar colour: the full card fill, a faded fill when declined, or a surface card
         * with an outline when invited. */
        int color = Utils.getDisplayColorFromColor(context,
                cursor.getInt(AgendaWindowAdapter.INDEX_COLOR));
        int surface = com.google.android.material.color.MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF);
        int cardFill;
        int strokeWidth = 0;
        switch (style.fill) {
            case FADED:
                cardFill = Utils.getDeclinedColorFromColor(color);
                break;
            case OUTLINED:
                cardFill = surface;
                strokeWidth = mResources.getDimensionPixelSize(R.dimen.agenda_card_outline_width);
                break;
            case SOLID:
            default:
                cardFill = color;
                break;
        }
        if (holder.card != null) {
            holder.card.setCardBackgroundColor(cardFill);
            holder.card.setStrokeWidth(strokeWidth);
            holder.card.setStrokeColor(color);
        }
        // The colour chip is only in the tablet row. Keep it in step with the same state.
        if (holder.colorChip != null) {
            holder.colorChip.setColor(color);
            holder.colorChip.setDrawStyle(declined ? ColorChipView.DRAW_FADED
                    : (invited ? ColorChipView.DRAW_BORDER : ColorChipView.DRAW_FULL));
            ViewGroup.LayoutParams params = holder.colorChip.getLayoutParams();
            params.height = allDay ? COLOR_CHIP_ALL_DAY_HEIGHT : COLOR_CHIP_HEIGHT;
            holder.colorChip.setLayoutParams(params);
        }

        // All-day items are compact. Timed items grow with their content.
        if (holder.content != null) {
            int padding = mResources.getDimensionPixelSize(allDay
                    ? R.dimen.agenda_card_padding_all_day : R.dimen.agenda_card_padding);
            holder.content.setPadding(padding, padding, padding, padding);
            holder.content.setMinimumHeight(mResources.getDimensionPixelSize(allDay
                    ? R.dimen.agenda_card_min_height_all_day : R.dimen.agenda_card_min_height));
        }

        // Text colour: whichever of on-surface and on-primary contrasts with the card fill.
        int eventTextColor;
        if (holder.title != null) {
            int onSurface = com.google.android.material.color.MaterialColors.getColor(
                    context, com.google.android.material.R.attr.colorOnSurface,
                    "AgendaAdapter");
            int onPrimary = com.google.android.material.color.MaterialColors.getColor(
                    context, com.google.android.material.R.attr.colorOnPrimary,
                    "AgendaAdapter");
            eventTextColor = ColorUtils.calculateContrast(onSurface, cardFill)
                    >= ColorUtils.calculateContrast(onPrimary, cardFill) ? onSurface : onPrimary;
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
                // Yearly recurrence means BEGIN is this year's occurrence; the
                // original date lives in DTSTART.
                long originalStart = cursor.getLong(AgendaWindowAdapter.INDEX_DTSTART);
                if (originalStart <= 0) {
                    originalStart = begin;
                }
                extraInfo = EventExtraUtils.getAnniversaryDisplayString(context, originalStart, now);
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
        View content;
        long startTimeMilli;
        boolean allDay;
        boolean grayed;
        int julianDay;
    }
}

