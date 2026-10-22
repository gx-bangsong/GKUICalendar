/*
 * Copyright (C) 2026 The GKUICalendar Project
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

package com.android.calendar.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.calendar.event.CalendarAccountGroups.Account;
import com.android.calendar.event.CalendarAccountGroups.Calendar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Pure JVM tests for the calendar account grouping and selection rules. */
public class CalendarAccountGroupsTest {

    private static final String GOOGLE = "com.google";
    private static final String LOCAL = CalendarAccountGroups.ACCOUNT_TYPE_LOCAL;

    private static Calendar cal(long id, String name, String owner, String account, String type) {
        return new Calendar(id, name, owner, account, type, 0xFF0000FF);
    }

    /** Two Google accounts, a same-name calendar in each, a shared calendar and a local one. */
    private static List<Calendar> sampleCursorOrder() {
        return new ArrayList<>(Arrays.asList(
                cal(1, "Work", "a@example.com", "a@example.com", GOOGLE),
                cal(2, "Work", "b@example.com", "b@example.com", GOOGLE),
                cal(3, "Shared team", "team@example.com", "a@example.com", GOOGLE),
                cal(4, "Phone", "Local", "Local", LOCAL)));
    }

    @Test
    public void sameNameCalendarsInDifferentAccountsStayInTheirOwnAccount() {
        List<Account> accounts = CalendarAccountGroups.group(sampleCursorOrder());

        assertEquals(3, accounts.size());
        assertEquals("a@example.com", accounts.get(0).accountName);
        assertEquals(1, accounts.get(0).calendars.get(0).id);
        assertEquals("b@example.com", accounts.get(1).accountName);
        assertEquals(2, accounts.get(1).calendars.get(0).id);
        assertSame(accounts.get(0).calendars.get(0), CalendarAccountGroups.findCalendar(accounts, 1));
        assertSame(accounts.get(1).calendars.get(0), CalendarAccountGroups.findCalendar(accounts, 2));
    }

    @Test
    public void sharedAndLocalCalendarsAreKept() {
        List<Account> accounts = CalendarAccountGroups.group(sampleCursorOrder());

        // The shared calendar is listed under the account it was synced into, not dropped.
        Account a = accounts.get(0);
        assertEquals(2, a.calendars.size());
        assertEquals(3, a.calendars.get(1).id);
        // The local calendar is its own account, with the local type kept.
        Account local = accounts.get(2);
        assertTrue(local.isLocal());
        assertEquals(4, local.calendars.get(0).id);
    }

    @Test
    public void accountIdentityUsesNameAndTypeTogether() {
        List<Calendar> calendars = new ArrayList<>(Arrays.asList(
                cal(10, "Cal", "x", "x", GOOGLE),
                cal(11, "Cal", "x", "x", "com.exchange")));
        List<Account> accounts = CalendarAccountGroups.group(calendars);
        assertEquals(2, accounts.size());
    }

    @Test
    public void savedCalendarIdSurvivesFilteringAndPositionChanges() {
        // The event was saved to calendar 2. Calendar 1 disappears from the writable list.
        List<Calendar> filtered = new ArrayList<>(sampleCursorOrder().subList(1, 4));
        List<Account> accounts = CalendarAccountGroups.group(filtered);

        Calendar found = CalendarAccountGroups.findCalendar(accounts, 2);
        assertEquals(2, found.id);
        assertEquals(0, CalendarAccountGroups.indexOfAccountContaining(accounts, 2));
    }

    @Test
    public void missingCalendarIsReportedNotReplacedByFirstRow() {
        List<Account> accounts = CalendarAccountGroups.group(sampleCursorOrder());
        // Read-only or deleted calendar: not found, so the caller must show unavailable state.
        assertNull(CalendarAccountGroups.findCalendar(accounts, 99));
        assertEquals(-1, CalendarAccountGroups.indexOfAccountContaining(accounts, 99));
        assertNull(CalendarAccountGroups.findCalendar(accounts, -1));
    }

    @Test
    public void accountSwitchPicksPrimaryCalendarOfThatAccountOnly() {
        List<Account> accounts = CalendarAccountGroups.group(sampleCursorOrder());

        Calendar pick = CalendarAccountGroups.fallbackCalendarOf(accounts.get(1));
        assertEquals(2, pick.id);
        assertEquals("b@example.com", pick.accountName);
    }

    @Test
    public void fallbackPrefersPrimaryOverSharedAndNeverPicksLocalAsPrimary() {
        List<Calendar> calendars = new ArrayList<>(Arrays.asList(
                cal(5, "Shared", "team@example.com", "a@example.com", GOOGLE),
                cal(6, "Main", "a@example.com", "a@example.com", GOOGLE)));
        Account account = CalendarAccountGroups.group(calendars).get(0);
        assertEquals(6, CalendarAccountGroups.fallbackCalendarOf(account).id);

        List<Calendar> localOnly = new ArrayList<>(Arrays.asList(
                cal(7, "Phone", "Local", "Local", LOCAL),
                cal(8, "Other", "Local", "Local", LOCAL)));
        Account local = CalendarAccountGroups.group(localOnly).get(0);
        // No primary exists, so the first calendar of the account is used.
        assertEquals(7, CalendarAccountGroups.fallbackCalendarOf(local).id);
    }

    @Test
    public void defaultWithoutStoredKeyIsPrimaryOfFirstNonLocalAccount() {
        List<Account> accounts = CalendarAccountGroups.group(sampleCursorOrder());
        assertEquals(1, CalendarAccountGroups.defaultCalendar(accounts, null).id);
    }

    @Test
    public void defaultWithStoredKeyMatchesOwnerAndName() {
        List<Account> accounts = CalendarAccountGroups.group(sampleCursorOrder());
        // Same display name "Work" exists in two accounts; the owner in the key decides.
        assertEquals(2, CalendarAccountGroups.defaultCalendar(accounts, "b@example.com/Work").id);
    }

    @Test
    public void defaultFallsBackToFirstRowWhenStoredKeyIsGone() {
        List<Account> accounts = CalendarAccountGroups.group(sampleCursorOrder());
        Calendar fallback = CalendarAccountGroups.defaultCalendar(accounts, "gone@example.com/X");
        assertEquals(CalendarAccountGroups.group(sampleCursorOrder()).get(0).calendars.get(0).id,
                fallback.id);
    }

    @Test
    public void emptyInputHasNoAccountAndNoDefault() {
        List<Account> accounts = CalendarAccountGroups.group(new ArrayList<Calendar>());
        assertTrue(accounts.isEmpty());
        assertNull(CalendarAccountGroups.defaultCalendar(accounts, null));
    }

    @Test
    public void defaultKeyRoundTripsThroughDefaultCalendar() {
        Calendar work = cal(2, "Work", "b@example.com", "b@example.com", GOOGLE);
        assertEquals("b@example.com/Work", CalendarAccountGroups.defaultKeyOf(work));
    }
}
