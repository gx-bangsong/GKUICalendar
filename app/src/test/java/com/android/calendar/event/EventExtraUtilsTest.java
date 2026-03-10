package com.android.calendar.event;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.time.LocalDate;

public class EventExtraUtilsTest {

    @Test
    public void testCalculateYearsSince() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate now = LocalDate.of(2024, 1, 1);
        assertEquals(4, EventExtraUtils.calculateYearsSince(start, now));

        LocalDate now2 = LocalDate.of(2024, 6, 1);
        assertEquals(4, EventExtraUtils.calculateYearsSince(start, now2));

        LocalDate now3 = LocalDate.of(2023, 12, 31);
        assertEquals(3, EventExtraUtils.calculateYearsSince(start, now3));
    }

    @Test
    public void testCalculateDaysUntil() {
        LocalDate target = LocalDate.of(2024, 1, 10);
        LocalDate now = LocalDate.of(2024, 1, 1);
        assertEquals(9, EventExtraUtils.calculateDaysUntil(target, now));

        LocalDate now2 = LocalDate.of(2024, 1, 11);
        assertEquals(-1, EventExtraUtils.calculateDaysUntil(target, now2));

        LocalDate now3 = LocalDate.of(2024, 1, 10);
        assertEquals(0, EventExtraUtils.calculateDaysUntil(target, now3));
    }
}
