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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.calendar.event.CalendarAccountGroups.Account;
import com.android.calendar.event.CalendarAccountGroups.Calendar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // Test labels stand in for the localised strings. The placeholder set mirrors what the
    // form builds from the offline account name in each supported language.
    private static final String LOCAL_LABEL = "Offline label";
    private static final String LOCAL_TYPE = "Offline type";
    private static final String UNNAMED = "Unnamed";
    /** The offline account name in Simplified Chinese, written as escapes to keep the source ASCII. */
    private static final String CN_PLACEHOLDER = "\u79bb\u7ebf\u65e5\u5386";

    private static Set<String> placeholders() {
        Set<String> names = new HashSet<>();
        names.add("Offline Calendar");
        names.add(CN_PLACEHOLDER);
        return names;
    }

    private static List<CalendarAccountGroups.AccountLabel> labelsOf(List<Calendar> calendars) {
        return CalendarAccountGroups.accountLabels(CalendarAccountGroups.group(calendars),
                placeholders(), LOCAL_LABEL, LOCAL_TYPE, UNNAMED);
    }

    @Test
    public void twoLocalAccountsWithRealNamesKeepTheirOwnTitles() {
        List<Calendar> calendars = new ArrayList<>(Arrays.asList(
                cal(10, "Home", "Family", "Family", LOCAL),
                cal(11, "Gym", "Trips", "Trips", LOCAL)));
        List<CalendarAccountGroups.AccountLabel> labels = labelsOf(calendars);
        assertEquals(2, labels.size());
        assertEquals("Family", labels.get(0).title);
        assertEquals("Trips", labels.get(1).title);
        assertEquals(LOCAL_TYPE, labels.get(0).subtitle);
        assertEquals(LOCAL_TYPE, labels.get(1).subtitle);
    }

    @Test
    public void placeholderLocalNameShowsTheLocalisedLabelInAnyLanguage() {
        List<CalendarAccountGroups.AccountLabel> english = labelsOf(new ArrayList<>(Arrays.asList(
                cal(20, "Phone", "Offline Calendar", "Offline Calendar", LOCAL))));
        assertEquals(LOCAL_LABEL, english.get(0).title);
        assertNull(english.get(0).subtitle);

        List<CalendarAccountGroups.AccountLabel> chinese = labelsOf(new ArrayList<>(Arrays.asList(
                cal(21, "Phone", CN_PLACEHOLDER, CN_PLACEHOLDER, LOCAL))));
        assertEquals(LOCAL_LABEL, chinese.get(0).title);
        assertNull(chinese.get(0).subtitle);
    }

    @Test
    public void blankLocalNameFallsBackToTheLocalisedLabel() {
        List<CalendarAccountGroups.AccountLabel> labels = labelsOf(new ArrayList<>(Arrays.asList(
                cal(22, "Phone", "", "", LOCAL))));
        assertEquals(LOCAL_LABEL, labels.get(0).title);
    }

    @Test
    public void blankNonLocalNameFallsBackToTheUnnamedLabelAndKeepsItsType() {
        List<CalendarAccountGroups.AccountLabel> labels = labelsOf(new ArrayList<>(Arrays.asList(
                cal(23, "Work", "", "", GOOGLE))));
        assertEquals(UNNAMED, labels.get(0).title);
        assertEquals(GOOGLE, labels.get(0).subtitle);
    }

    @Test
    public void accountsThatWouldShowTheSameTitleAreToldApartByCalendarName() {
        List<CalendarAccountGroups.AccountLabel> labels = labelsOf(new ArrayList<>(Arrays.asList(
                cal(30, "Desk", "Offline Calendar", "Offline Calendar", LOCAL),
                cal(31, "Garage", LOCAL_LABEL, LOCAL_LABEL, LOCAL))));
        assertEquals(2, labels.size());
        assertEquals(LOCAL_LABEL, labels.get(0).title);
        assertEquals(LOCAL_LABEL, labels.get(1).title);
        assertNotEquals(labels.get(0).subtitle, labels.get(1).subtitle);
        assertTrue(labels.get(0).subtitle.contains("Desk"));
        assertTrue(labels.get(1).subtitle.contains("Garage"));
    }

    @Test
    public void chipShowsTheCalendarDisplayNameNeverTheAccountName() {
        Calendar named = cal(40, "Work", "a@example.com", "a@example.com", GOOGLE);
        Calendar unnamed = cal(41, "", "a@example.com", "a@example.com", GOOGLE);
        Calendar nullName = cal(42, null, "a@example.com", "a@example.com", GOOGLE);
        assertEquals("Work", CalendarAccountGroups.calendarChipLabel(named, UNNAMED));
        assertEquals(UNNAMED, CalendarAccountGroups.calendarChipLabel(unnamed, UNNAMED));
        assertEquals(UNNAMED, CalendarAccountGroups.calendarChipLabel(nullName, UNNAMED));
        assertFalse("a@example.com".equals(
                CalendarAccountGroups.calendarChipLabel(unnamed, UNNAMED)));
    }

    @Test
    public void calendarsOfOneAccountKeepTheirOwnChipLabels() {
        List<Calendar> calendars = new ArrayList<>(Arrays.asList(
                cal(50, "Home", "Family", "Family", LOCAL),
                cal(51, "Work", "Family", "Family", LOCAL),
                cal(52, "Holidays", "Family", "Family", LOCAL)));
        List<Account> accounts = CalendarAccountGroups.group(calendars);
        assertEquals(1, accounts.size());
        List<Calendar> inAccount = accounts.get(0).calendars;
        assertEquals("Home", CalendarAccountGroups.calendarChipLabel(inAccount.get(0), UNNAMED));
        assertEquals("Work", CalendarAccountGroups.calendarChipLabel(inAccount.get(1), UNNAMED));
        assertEquals("Holidays",
                CalendarAccountGroups.calendarChipLabel(inAccount.get(2), UNNAMED));
    }

    @Test
    public void sameNameCalendarsInDifferentAccountsKeepTheirOwnIds() {
        List<Calendar> calendars = new ArrayList<>(Arrays.asList(
                cal(60, "Work", "a@example.com", "a@example.com", GOOGLE),
                cal(61, "Work", "b@example.com", "b@example.com", GOOGLE)));
        List<Account> accounts = CalendarAccountGroups.group(calendars);
        assertEquals(2, accounts.size());
        assertEquals(60, CalendarAccountGroups.findCalendar(accounts, 60).id);
        assertEquals(61, CalendarAccountGroups.findCalendar(accounts, 61).id);
        assertEquals(0, CalendarAccountGroups.indexOfAccountContaining(accounts, 60));
        assertEquals(1, CalendarAccountGroups.indexOfAccountContaining(accounts, 61));
    }

    @Test
    public void renamingACalendarDoesNotChangeTheSelectedId() {
        List<Account> before = CalendarAccountGroups.group(new ArrayList<>(Arrays.asList(
                cal(70, "Work", "a@example.com", "a@example.com", GOOGLE))));
        List<Account> after = CalendarAccountGroups.group(new ArrayList<>(Arrays.asList(
                cal(70, "Work renamed", "a@example.com", "a@example.com", GOOGLE))));
        assertEquals(70, CalendarAccountGroups.findCalendar(before, 70).id);
        assertEquals(70, CalendarAccountGroups.findCalendar(after, 70).id);
        assertEquals(0, CalendarAccountGroups.indexOfAccountContaining(after, 70));
        assertEquals("Work renamed", CalendarAccountGroups.calendarChipLabel(
                CalendarAccountGroups.findCalendar(after, 70), UNNAMED));
    }
}
