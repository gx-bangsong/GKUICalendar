package com.android.calendar.event;

import android.content.Context;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import ws.xsoh.etar.R;

public class EventExtraUtils {

    public static final String EVENT_TYPE_EXTENDED_PROP = "etar_event_type";
    public static final String EVENT_TYPE_NORMAL = "normal";
    public static final String EVENT_TYPE_BIRTHDAY = "birthday";
    public static final String EVENT_TYPE_ANNIVERSARY = "anniversary";
    public static final String EVENT_TYPE_COUNTDOWN = "countdown";

    public static long calculateYearsSince(LocalDate startDate, LocalDate today) {
        return ChronoUnit.YEARS.between(startDate, today);
    }

    public static long calculateDaysUntil(LocalDate targetDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, targetDate);
    }

    public static String getAnniversaryDisplayString(Context context, long startMillis, long todayMillis) {
        // All-day events in Android are stored as midnight UTC.
        LocalDate startDate = java.time.Instant.ofEpochMilli(startMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate today = java.time.Instant.ofEpochMilli(todayMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        if (startDate.getMonth() == today.getMonth() && startDate.getDayOfMonth() == today.getDayOfMonth()) {
            return context.getString(R.string.anniversary_today);
        }

        long years = calculateYearsSince(startDate, today);
        return context.getString(R.string.anniversary_years_passed, (int)years);
    }

    public static String getCountdownDisplayString(Context context, long targetMillis, long todayMillis) {
        LocalDate targetDate = java.time.Instant.ofEpochMilli(targetMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate today = java.time.Instant.ofEpochMilli(todayMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        long days = calculateDaysUntil(targetDate, today);

        if (days > 0) {
            return context.getString(R.string.countdown_days_remaining, (int)days);
        } else if (days == 0) {
            return context.getString(R.string.countdown_today);
        } else {
            return context.getString(R.string.countdown_days_passed, (int)Math.abs(days));
        }
    }
}
