/*
 * Copyright (C) 2026 The Etar Project
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

package com.android.calendar.widget;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.text.format.DateUtils;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.android.calendar.DynamicTheme;
import com.android.calendar.Utils;
import com.android.calendar.event.EventExtraUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ws.xsoh.etar.R;

/**
 * Feeds the countdown / memorial-day widget.
 *
 * Only events tagged with one of Etar's special types are shown:
 * {@code countdown}, {@code anniversary} and {@code birthday}. Those are
 * stored in {@link Instances#CUSTOM_APP_URI} as {@code etar://event_type/<type>}
 * by {@code EditEventHelper}.
 */
public class CountdownWidgetService extends RemoteViewsService {

    /** Anniversaries repeat yearly, so a window of just over a year always
     *  contains the next occurrence of every one of them. */
    static final long SEARCH_DURATION = 400L * DateUtils.DAY_IN_MILLIS;
    static final int MAX_ROWS = 25;

    private static final String[] PROJECTION = new String[] {
            Instances.EVENT_ID,        // 0
            Instances.TITLE,           // 1
            Instances.BEGIN,           // 2
            Instances.END,             // 3
            Instances.ALL_DAY,         // 4
            Instances.DISPLAY_COLOR,   // 5
            Instances.CUSTOM_APP_URI,  // 6
            Instances.DTSTART,         // 7
    };
    private static final int IDX_EVENT_ID = 0;
    private static final int IDX_TITLE = 1;
    private static final int IDX_BEGIN = 2;
    private static final int IDX_END = 3;
    private static final int IDX_ALL_DAY = 4;
    private static final int IDX_COLOR = 5;
    private static final int IDX_CUSTOM_APP_URI = 6;
    private static final int IDX_DTSTART = 7;

    private static final String SELECTION = Calendars.VISIBLE + "=1 AND "
            + Instances.CUSTOM_APP_URI + " LIKE 'etar://event_type/%'";
    private static final String SORT_ORDER = Instances.BEGIN + " ASC";
    private static final String URI_PREFIX = "etar://event_type/";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CountdownFactory(getApplicationContext());
    }

    /** One rendered row. */
    private static final class Row {
        long eventId;
        long begin;
        long end;
        boolean allDay;
        String title;
        int color;
        String type;
        /** Whole days from today until the event; negative once it has passed. */
        long daysUntil;
        /** Completed years since the original date (anniversaries only). */
        long years;
    }

    private static final class CountdownFactory implements RemoteViewsFactory {

        private final Context mContext;
        private final List<Row> mRows = new ArrayList<>();
        private boolean mDark;

        CountdownFactory(Context context) {
            mContext = context;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDestroy() {
            mRows.clear();
        }

        /**
         * Runs on a binder thread, so querying synchronously here is both safe
         * and the documented pattern for RemoteViewsFactory.
         */
        @Override
        public void onDataSetChanged() {
            mDark = DynamicTheme.isWidgetDark(mContext);
            mRows.clear();

            final long now = System.currentTimeMillis();
            Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, now - DateUtils.DAY_IN_MILLIS);
            ContentUris.appendId(builder, now + SEARCH_DURATION);

            final LocalDate today = Instant.ofEpochMilli(now)
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            // A recurring anniversary yields one instance per year inside the
            // window; keep only the soonest for each event.
            final Set<Long> seen = new HashSet<>();

            try (Cursor cursor = mContext.getContentResolver().query(
                    builder.build(), PROJECTION, SELECTION, null, SORT_ORDER)) {
                if (cursor == null) {
                    return;
                }
                while (cursor.moveToNext() && mRows.size() < MAX_ROWS) {
                    String customAppUri = cursor.getString(IDX_CUSTOM_APP_URI);
                    if (customAppUri == null || !customAppUri.startsWith(URI_PREFIX)) {
                        continue;
                    }
                    String type = customAppUri.substring(URI_PREFIX.length());
                    if (!EventExtraUtils.EVENT_TYPE_COUNTDOWN.equals(type)
                            && !EventExtraUtils.EVENT_TYPE_ANNIVERSARY.equals(type)
                            && !EventExtraUtils.EVENT_TYPE_BIRTHDAY.equals(type)) {
                        continue;
                    }
                    long eventId = cursor.getLong(IDX_EVENT_ID);
                    if (!seen.add(eventId)) {
                        continue;
                    }

                    Row row = new Row();
                    row.eventId = eventId;
                    row.begin = cursor.getLong(IDX_BEGIN);
                    row.end = cursor.getLong(IDX_END);
                    row.allDay = cursor.getInt(IDX_ALL_DAY) != 0;
                    row.title = cursor.getString(IDX_TITLE);
                    row.color = Utils.getDisplayColorFromColor(
                            mContext, cursor.getInt(IDX_COLOR));
                    row.type = type;

                    // All-day events are stored at midnight UTC; reading them
                    // in the local zone would shift the date by a day.
                    LocalDate target = Instant.ofEpochMilli(row.begin)
                            .atZone(row.allDay ? ZoneOffset.UTC : ZoneId.systemDefault())
                            .toLocalDate();
                    row.daysUntil = EventExtraUtils.calculateDaysUntil(target, today);

                    if (!EventExtraUtils.EVENT_TYPE_COUNTDOWN.equals(type)) {
                        // Yearly recurrence means BEGIN is the upcoming
                        // occurrence; the original date lives in DTSTART.
                        long originalStart = cursor.getLong(IDX_DTSTART);
                        if (originalStart <= 0) {
                            originalStart = row.begin;
                        }
                        LocalDate origin = Instant.ofEpochMilli(originalStart)
                                .atZone(ZoneOffset.UTC).toLocalDate();
                        row.years = EventExtraUtils.calculateYearsSince(origin, today);
                    }
                    mRows.add(row);
                }
            } catch (Exception e) {
                // A widget must never crash the host launcher.
                mRows.clear();
            }
        }

        @Override
        public int getCount() {
            return mRows.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= mRows.size()) {
                return null;
            }
            Row row = mRows.get(position);
            RemoteViews views = new RemoteViews(
                    mContext.getPackageName(), R.layout.countdown_widget_item);

            views.setInt(R.id.countdown_chip, "setBackgroundResource",
                    R.drawable.widget_chip_md3_filled);
            views.setInt(R.id.countdown_chip, "setColorFilter", row.color);
            // Text sits on the calendar-coloured chip, so pick the legible
            // polarity rather than assuming white.
            int onChip = Utils.getContrastingTextColor(row.color);
            int onChipDim = Utils.getSecondaryContrastingTextColor(row.color);

            views.setTextViewText(R.id.countdown_title, row.title == null
                    ? mContext.getString(R.string.no_title_label) : row.title);
            views.setTextColor(R.id.countdown_title, onChip);

            views.setTextViewText(R.id.countdown_subtitle, buildSubtitle(row));
            views.setTextColor(R.id.countdown_subtitle, onChipDim);

            views.setTextViewText(R.id.countdown_number, buildNumber(row));
            views.setTextColor(R.id.countdown_number, onChip);
            views.setTextViewText(R.id.countdown_unit, buildUnit(row));
            views.setTextColor(R.id.countdown_unit, onChipDim);

            long start = row.begin;
            long end = row.end;
            if (row.allDay) {
                String tz = Utils.getTimeZone(mContext, null);
                com.android.calendar.calendarcommon2.Time recycle =
                        new com.android.calendar.calendarcommon2.Time();
                start = Utils.convertAlldayLocalToUTC(recycle, start, tz);
                end = Utils.convertAlldayLocalToUTC(recycle, end, tz);
            }
            views.setOnClickFillInIntent(R.id.countdown_chip,
                    CalendarAppWidgetProvider.getLaunchFillInIntent(
                            mContext, row.eventId, start, end, row.allDay));
            return views;
        }

        /** The large figure: days remaining, or elapsed once the date passed. */
        private String buildNumber(Row row) {
            long days = row.daysUntil;
            if (days == 0) {
                return mContext.getString(R.string.countdown_widget_today);
            }
            return String.valueOf(Math.abs(days));
        }

        private String buildUnit(Row row) {
            if (row.daysUntil == 0) {
                return "";
            }
            return mContext.getString(row.daysUntil > 0
                    ? R.string.countdown_widget_days_left
                    : R.string.countdown_widget_days_past);
        }

        /** Supporting line: the ordinal year for anniversaries, else the date. */
        private String buildSubtitle(Row row) {
            String date = DateUtils.formatDateTime(mContext, row.begin,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
            if (EventExtraUtils.EVENT_TYPE_COUNTDOWN.equals(row.type)) {
                return date;
            }
            // On the day itself the completed count is the ordinal celebrated.
            long ordinal = row.daysUntil == 0 ? row.years : row.years + 1;
            if (ordinal <= 0) {
                return date;
            }
            return mContext.getString(
                    R.string.countdown_widget_nth_year, (int) ordinal) + " · " + date;
        }

        @Override
        public RemoteViews getLoadingView() {
            return new RemoteViews(mContext.getPackageName(), R.layout.appwidget_loading);
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position < mRows.size() ? mRows.get(position).eventId : position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
