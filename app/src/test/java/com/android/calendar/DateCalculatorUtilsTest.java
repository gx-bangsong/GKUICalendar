package com.android.calendar;

import com.android.calendar.date_calculator.util.DateCalculatorUtils;

import junit.framework.TestCase;

import java.time.LocalDate;

public class DateCalculatorUtilsTest extends TestCase {

    public void testCalculateTargetDate() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);

        // Test forward calculation
        LocalDate futureDate = DateCalculatorUtils.calculateTargetDate(startDate, 10, true);
        assertEquals(LocalDate.of(2024, 1, 11), futureDate);

        // Test backward calculation
        LocalDate pastDate = DateCalculatorUtils.calculateTargetDate(startDate, 10, false);
        assertEquals(LocalDate.of(2023, 12, 22), pastDate);

        // Test with zero days
        LocalDate sameDate = DateCalculatorUtils.calculateTargetDate(startDate, 0, true);
        assertEquals(startDate, sameDate);

        // Test with negative days (should return start date)
        LocalDate invalidDate = DateCalculatorUtils.calculateTargetDate(startDate, -5, true);
        assertEquals(startDate, invalidDate);
    }

    public void testCalculateDateDifference() {
        LocalDate date1 = LocalDate.of(2024, 1, 1);
        LocalDate date2 = LocalDate.of(2024, 1, 11);
        assertEquals(10, DateCalculatorUtils.calculateDateDifference(date1, date2));

        // Test with swapped dates (should be absolute difference)
        assertEquals(10, DateCalculatorUtils.calculateDateDifference(date2, date1));

        // Test with same date
        assertEquals(0, DateCalculatorUtils.calculateDateDifference(date1, date1));
    }

    public void testLunarConversion() {
        // Test Gregorian to Lunar for Chinese New Year 2024
        LocalDate gregorianDate = LocalDate.of(2024, 2, 10);
        DateCalculatorUtils.LunarDate lunarDate = DateCalculatorUtils.convertToLunar(gregorianDate);

        assertEquals(2024, lunarDate.gregorianYear);
        assertEquals(41, lunarDate.year);
        assertEquals(0, lunarDate.month); // Month is 0-based
        assertEquals(1, lunarDate.day);
        assertFalse(lunarDate.isLeapMonth);
        assertEquals("甲辰年(2024) 正月 初一", lunarDate.toString());

        // Test Lunar to Gregorian
        LocalDate convertedGregorian = DateCalculatorUtils.convertToGregorian(41, 0, 1, false);
        assertEquals(gregorianDate, convertedGregorian);

        // Test another date: Mid-Autumn Festival 2023
        LocalDate midAutumnGregorian = LocalDate.of(2023, 9, 29);
        DateCalculatorUtils.LunarDate midAutumnLunar = DateCalculatorUtils.convertToLunar(midAutumnGregorian);
        assertEquals(2023, midAutumnLunar.gregorianYear);
        assertEquals(40, midAutumnLunar.year);
        assertEquals(8, midAutumnLunar.month); // 9th month is index 8
        assertEquals(15, midAutumnLunar.day);
        assertFalse(midAutumnLunar.isLeapMonth);
        assertEquals("癸卯年(2023) 九月 十五", midAutumnLunar.toString());

        // Test conversion back
        LocalDate convertedMidAutumn = DateCalculatorUtils.convertToGregorian(40, 8, 15, false);
        assertEquals(midAutumnGregorian, convertedMidAutumn);
    }
}