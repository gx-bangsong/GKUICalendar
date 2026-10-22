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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure grouping and selection rules for the calendar selector of the event form.
 *
 * <p>Calendars are grouped by the account identity (ACCOUNT_NAME plus ACCOUNT_TYPE), so two
 * calendars with the same display name in different accounts stay separate. Every selection is
 * identified by the calendar _ID, never by a filtered list position. This class has no Android
 * dependency so that the rules can be covered by JVM unit tests.
 */
public final class CalendarAccountGroups {

    /** Account type used by the provider for calendars that live only on this device. */
    static final String ACCOUNT_TYPE_LOCAL = "LOCAL";

    private CalendarAccountGroups() {
    }

    /** One writable calendar as read from the provider cursor. */
    public static final class Calendar {
        public final long id;
        public final String displayName;
        public final String ownerAccount;
        public final String accountName;
        public final String accountType;
        public final int color;

        public Calendar(long id, String displayName, String ownerAccount, String accountName,
                String accountType, int color) {
            this.id = id;
            this.displayName = displayName;
            this.ownerAccount = ownerAccount;
            this.accountName = accountName;
            this.accountType = accountType;
            this.color = color;
        }

        public boolean isLocal() {
            return ACCOUNT_TYPE_LOCAL.equals(accountType);
        }
    }

    /** An account and its calendars, in the order the provider returned them. */
    public static final class Account {
        public final String accountName;
        public final String accountType;
        public final List<Calendar> calendars;

        Account(String accountName, String accountType) {
            this.accountName = accountName;
            this.accountType = accountType;
            this.calendars = new ArrayList<>();
        }

        public boolean isLocal() {
            return ACCOUNT_TYPE_LOCAL.equals(accountType);
        }

        boolean hasSameIdentity(String name, String type) {
            return Objects.equals(accountName, name) && Objects.equals(accountType, type);
        }
    }

    /**
     * Groups calendars by (ACCOUNT_NAME, ACCOUNT_TYPE). Accounts keep the order in which they
     * first appear, and calendars keep their input order. Local and non-Google calendars are
     * kept; nothing is dropped here.
     */
    public static List<Account> group(List<Calendar> calendars) {
        List<Account> accounts = new ArrayList<>();
        for (Calendar calendar : calendars) {
            Account target = null;
            for (Account account : accounts) {
                if (account.hasSameIdentity(calendar.accountName, calendar.accountType)) {
                    target = account;
                    break;
                }
            }
            if (target == null) {
                target = new Account(calendar.accountName, calendar.accountType);
                accounts.add(target);
            }
            target.calendars.add(calendar);
        }
        return accounts;
    }

    /** Returns the calendar with this _ID, or null when it is not in the writable list. */
    public static Calendar findCalendar(List<Account> accounts, long calendarId) {
        if (calendarId == -1) {
            return null;
        }
        for (Account account : accounts) {
            for (Calendar calendar : account.calendars) {
                if (calendar.id == calendarId) {
                    return calendar;
                }
            }
        }
        return null;
    }

    /** Returns the account index that contains the calendar, or -1 when there is none. */
    public static int indexOfAccountContaining(List<Account> accounts, long calendarId) {
        for (int i = 0; i < accounts.size(); i++) {
            for (Calendar calendar : accounts.get(i).calendars) {
                if (calendar.id == calendarId) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The calendar used when an account is chosen, or when a new event has no calendar yet.
     * The rule is explainable and stable: the account's primary calendar (owner equals the
     * account name, not local) if there is one, otherwise the first calendar of that account.
     * The choice is not persisted.
     */
    public static Calendar fallbackCalendarOf(Account account) {
        if (account.calendars.isEmpty()) {
            return null;
        }
        for (Calendar calendar : account.calendars) {
            if (!calendar.isLocal() && calendar.ownerAccount != null
                    && calendar.ownerAccount.equals(calendar.accountName)) {
                return calendar;
            }
        }
        return account.calendars.get(0);
    }

    /**
     * The default calendar for a new event. {@code defaultKey} is the stored "owner/name" value of
     * the last saved calendar, or null when nothing is stored. Without a stored value the primary
     * calendar of the first non-local account is used, matching the previous behaviour.
     */
    public static Calendar defaultCalendar(List<Account> accounts, String defaultKey) {
        List<Calendar> all = new ArrayList<>();
        for (Account account : accounts) {
            all.addAll(account.calendars);
        }
        if (all.isEmpty()) {
            return null;
        }
        if (defaultKey != null) {
            for (Calendar calendar : all) {
                if (defaultKey.equals(calendar.ownerAccount + "/" + calendar.displayName)) {
                    return calendar;
                }
            }
        } else {
            for (Calendar calendar : all) {
                if (calendar.ownerAccount != null
                        && calendar.ownerAccount.equals(calendar.accountName)
                        && !calendar.isLocal()) {
                    return calendar;
                }
            }
        }
        return all.get(0);
    }

    /** Stored key format used for the default calendar preference. */
    public static String defaultKeyOf(Calendar calendar) {
        return calendar.ownerAccount + "/" + calendar.displayName;
    }
}
