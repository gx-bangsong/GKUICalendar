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

import ws.xsoh.etar.R;

/**
 * Home-screen widget listing countdown and memorial-day (anniversary /
 * birthday) events with the number of days remaining.
 */
public class CountdownWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId));
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(
                appWidgetIds, R.id.countdown_list);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // Calendar edits and midnight rollovers both change the day counts, so
        // refresh the list whenever the provider or the date moves.
        String action = intent.getAction();
        if (Intent.ACTION_PROVIDER_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(
                    new ComponentName(context, CountdownWidgetProvider.class));
            if (ids != null && ids.length > 0) {
                onUpdate(context, mgr, ids);
            }
        }
    }

    private RemoteViews buildViews(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(
                context.getPackageName(), R.layout.countdown_widget);

        // Etar's theme preference is independent of the system uiMode, so the
        // palette is resolved here rather than via -night qualifiers.
        final boolean dark = DynamicTheme.isWidgetDark(context);
        views.setInt(R.id.countdown_background, "setBackgroundResource",
                dark ? R.drawable.widget_container_bg_dark
                     : R.drawable.widget_container_bg);
        views.setTextColor(R.id.countdown_header, context.getColor(
                dark ? R.color.widget_on_surface_dark : R.color.widget_on_surface));
        views.setTextColor(R.id.countdown_empty, context.getColor(
                dark ? R.color.widget_on_surface_variant_dark
                     : R.color.widget_on_surface_variant));

        Intent serviceIntent = new Intent(context, CountdownWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // The data URI makes the intent unique per widget instance, otherwise
        // multiple placements share one factory.
        serviceIntent.setData(
                Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.countdown_list, serviceIntent);
        views.setEmptyView(R.id.countdown_list, R.id.countdown_empty);

        views.setPendingIntentTemplate(R.id.countdown_list,
                CalendarAppWidgetProvider.getLaunchPendingIntentTemplate(context));

        Intent openApp = new Intent(Intent.ACTION_VIEW)
                .setClass(context, AllInOneActivity.class)
                .setData(Uri.parse("content://" + CalendarContract.AUTHORITY + "/time/"
                        + System.currentTimeMillis()));
        views.setOnClickPendingIntent(R.id.countdown_header,
                PendingIntent.getActivity(context, 0, openApp,
                        PendingIntent.FLAG_IMMUTABLE));
        return views;
    }
}
