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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.RemoteViews;

import com.android.calendar.AllInOneActivity;
import com.android.calendar.DynamicTheme;
import com.android.calendar.lunar.AlmanacInfo;
import com.android.calendar.lunar.LunarHelper;

import ws.xsoh.etar.R;

/**
 * 万年历 / 宜忌 widget: shows today's Gregorian and lunar date, the ganzhi
 * pillars, and the day's 宜 (suitable) and 忌 (inadvisable) activities.
 *
 * This widget has no list, so it needs no RemoteViewsService - everything is
 * computed once per update and pushed straight into the RemoteViews.
 */
public class AlmanacWidgetProvider extends AppWidgetProvider {

    /** Keep the almanac lines short enough to fit a 4x2 widget. */
    private static final int MAX_ENTRIES = 5;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // The almanac is date-derived, so it only needs refreshing when the
        // day, the time zone or the locale moves.
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(
                    new ComponentName(context, AlmanacWidgetProvider.class));
            if (ids != null && ids.length > 0) {
                onUpdate(context, mgr, ids);
            }
        }
    }

    private RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(
                context.getPackageName(), R.layout.almanac_widget);

        final boolean dark = DynamicTheme.isWidgetDark(context);
        final int onSurface = context.getColor(
                dark ? R.color.widget_on_surface_dark : R.color.widget_on_surface);
        final int onSurfaceVariant = context.getColor(
                dark ? R.color.widget_on_surface_variant_dark
                     : R.color.widget_on_surface_variant);

        views.setInt(R.id.almanac_background, "setBackgroundResource",
                dark ? R.drawable.widget_container_bg_dark
                     : R.drawable.widget_container_bg);

        AlmanacInfo info;
        try {
            info = AlmanacInfo.forJulianDay(
                    LunarHelper.todayJulianDay(), MAX_ENTRIES);
        } catch (Exception e) {
            // Never let an almanac lookup take down the host launcher.
            return views;
        }

        views.setTextViewText(R.id.almanac_day, info.getSolarDay());
        views.setTextColor(R.id.almanac_day, onSurface);

        views.setTextViewText(R.id.almanac_month, info.getSolarMonth());
        views.setTextColor(R.id.almanac_month, onSurfaceVariant);

        views.setTextViewText(R.id.almanac_weekday,
                info.getWeekday() + "  " + info.badge());
        views.setTextColor(R.id.almanac_weekday, onSurfaceVariant);

        views.setTextViewText(R.id.almanac_lunar, info.getLunarDate());
        views.setTextColor(R.id.almanac_lunar, onSurface);

        views.setTextViewText(R.id.almanac_ganzhi, info.getGanZhi());
        views.setTextColor(R.id.almanac_ganzhi, onSurfaceVariant);

        final String none = context.getString(R.string.almanac_none);
        views.setTextViewText(R.id.almanac_yi_text, info.yiText(none));
        views.setTextColor(R.id.almanac_yi_text, onSurface);
        views.setTextViewText(R.id.almanac_ji_text, info.jiText(none));
        views.setTextColor(R.id.almanac_ji_text, onSurface);

        Intent openApp = new Intent(Intent.ACTION_VIEW)
                .setClass(context, AllInOneActivity.class)
                .setData(Uri.parse("content://" + CalendarContract.AUTHORITY + "/time/"
                        + System.currentTimeMillis()));
        views.setOnClickPendingIntent(R.id.almanac_background,
                PendingIntent.getActivity(context, 0, openApp,
                        PendingIntent.FLAG_IMMUTABLE));
        return views;
    }
}
