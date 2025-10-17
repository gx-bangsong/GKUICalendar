package com.android.calendar.date_calculator.util;

import android.icu.util.Calendar;
import android.icu.util.ChineseCalendar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Utility class for date calculations, including Gregorian and Lunar conversions.
 */
public class DateCalculatorUtils {

    private static final String[] HEAVENLY_STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] EARTHLY_BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] LUNAR_MONTH_NAMES = {"正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月"};
    private static final String[] LUNAR_DAY_NAMES = {
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    /**
     * Calculates the target date by adding or subtracting a specified number of days from a start date.
     *
     * @param startDate The starting date.
     * @param days      The number of days to add or subtract. Must be a non-negative integer.
     * @param isForward True to calculate a future date, false to calculate a past date.
     * @return The calculated target date.
     */
    public static LocalDate calculateTargetDate(LocalDate startDate, int days, boolean isForward) {
        if (days < 0) {
            return startDate;
        }
        return isForward ? startDate.plusDays(days) : startDate.minusDays(days);
    }

    /**
     * Calculates the absolute difference in days between two dates.
     *
     * @param startDate The first date.
     * @param endDate   The second date.
     * @return The absolute number of days between the two dates.
     */
    public static long calculateDateDifference(LocalDate startDate, LocalDate endDate) {
        return Math.abs(ChronoUnit.DAYS.between(startDate, endDate));
    }

    /**
     * Represents a Lunar date.
     */
    public static class LunarDate {
        public final int gregorianYear;
        public final int year;
        public final int month;
        public final int day;
        public final boolean isLeapMonth;
        public final String yearName;
        public final String monthName;
        public final String dayName;

        public LunarDate(int gregorianYear, int year, int month, int day, boolean isLeapMonth) {
            this.gregorianYear = gregorianYear;
            this.year = year;
            this.month = month; // 0-based
            this.day = day;
            this.isLeapMonth = isLeapMonth;

            this.yearName = getYearName(year);
            this.monthName = getMonthName(month);
            this.dayName = getDayName(day);
        }

        @Override
        public String toString() {
            return String.format("%s(%d) %s%s %s", yearName, gregorianYear, isLeapMonth ? "闰" : "", monthName, dayName);
        }
    }

    private static String getYearName(int year) {
        if (year <= 0) return "";
        int cyclicalYear = year + 2637;
        int stemIndex = (cyclicalYear - 1) % 10;
        int branchIndex = (cyclicalYear - 1) % 12;
        return HEAVENLY_STEMS[stemIndex] + EARTHLY_BRANCHES[branchIndex] + "年";
    }

    private static String getMonthName(int month) {
        if (month < 0 || month > 11) return "";
        return LUNAR_MONTH_NAMES[month];
    }

    private static String getDayName(int day) {
        if (day < 1 || day > 30) return "";
        return LUNAR_DAY_NAMES[day - 1];
    }

    /**
     * Converts a Gregorian date to a Lunar date.
     *
     * @param gregorianDate The Gregorian date to convert.
     * @return The corresponding LunarDate object.
     */
    public static LunarDate convertToLunar(LocalDate gregorianDate) {
        ChineseCalendar cc = new ChineseCalendar(Date.from(gregorianDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        int year = cc.get(ChineseCalendar.YEAR);
        int month = cc.get(ChineseCalendar.MONTH); // 0-based
        int day = cc.get(ChineseCalendar.DAY_OF_MONTH);
        boolean isLeap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1;

        return new LunarDate(gregorianDate.getYear(), year, month, day, isLeap);
    }

    /**
     * Converts a Lunar date to a Gregorian date.
     *
     * @param lunarYear  The lunar year.
     * @param lunarMonth The lunar month (0-based).
     * @param lunarDay   The lunar day.
     * @param isLeap     True if the month is a leap month.
     * @return The corresponding Gregorian LocalDate.
     */
    public static LocalDate convertToGregorian(int lunarYear, int lunarMonth, int lunarDay, boolean isLeap) {
        ChineseCalendar cc = new ChineseCalendar();
        cc.set(ChineseCalendar.YEAR, lunarYear);
        cc.set(ChineseCalendar.MONTH, lunarMonth); // 0-based
        cc.set(ChineseCalendar.IS_LEAP_MONTH, isLeap ? 1 : 0);
        cc.set(ChineseCalendar.DAY_OF_MONTH, lunarDay);

        return cc.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}