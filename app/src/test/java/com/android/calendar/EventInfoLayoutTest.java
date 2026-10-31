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

package com.android.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Static checks of the event detail page. They read the layout, menu and Fragment sources as
 * text, so they run on the plain JVM. They cannot show how the page looks; that needs a device.
 * They guard the structure the restyle depends on: one shared body, rows that start hidden,
 * reading fields that are TextViews, one action set, and no event colour on text.
 */
public class EventInfoLayoutTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String LAYOUT = "src/main/res/layout/";

    /** Rows whose content is decided at runtime. Each must start GONE in the shared body. */
    private static final String[] DYNAMIC_ROWS = {
            "timezone_row", "repeat_row", "where_row", "calendar_container",
            "organizer_container", "description_row", "url_row", "attendees_row",
            "response_container", "email_attendees_container", "launch_custom_app_container"
    };

    private static Document parse(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new File(path));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    private static Element byId(Document doc, String id) {
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            String value = e.getAttributeNS(ANDROID_NS, "id");
            if (value.equals("@+id/" + id) || value.equals("@id/" + id)) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void dynamicRowsStartHiddenSoEmptyContentCreatesNoRow() throws Exception {
        Document body = parse(LAYOUT + "event_info_body.xml");
        List<String> visibleAtStart = new ArrayList<>();
        for (String row : DYNAMIC_ROWS) {
            Element e = byId(body, row);
            assertTrue("missing row " + row, e != null);
            if (!"gone".equals(e.getAttributeNS(ANDROID_NS, "visibility"))) {
                visibleAtStart.add(row);
            }
        }
        // The attendee list is a child of attendees_row; it is also hidden until used.
        assertEquals("rows shown before their content is known: " + visibleAtStart,
                0, visibleAtStart.size());
    }

    @Test
    public void whenRowAndReminderRowAreAlwaysPresent() throws Exception {
        Document body = parse(LAYOUT + "event_info_body.xml");
        assertTrue(byId(body, "when_row") != null);
        assertTrue(byId(body, "when_datetime") != null);
        assertTrue(byId(body, "reminders_row") != null);
        assertTrue(byId(body, "reminder_items_container") != null);
        assertTrue(byId(body, "reminder_add") != null);
    }

    @Test
    public void fullScreenAndDialogShareOneBody() throws Exception {
        String full = read(LAYOUT + "event_info.xml");
        String dialog = read(LAYOUT + "event_info_dialog.xml");
        assertTrue(full.contains("@layout/event_info_body"));
        assertTrue(dialog.contains("@layout/event_info_body"));
        // The content itself lives only in the body, so the two pages cannot drift apart.
        assertFalse(full.contains("android:id=\"@+id/when_datetime\""));
        assertFalse(dialog.contains("android:id=\"@+id/when_datetime\""));
    }

    @Test
    public void contentColumnIsCappedAtReadableWidth() throws Exception {
        Document body = parse(LAYOUT + "event_info_body.xml");
        Element content = byId(body, "event_info_content");
        assertTrue(content != null);
        assertEquals("600dp", content.getAttributeNS(
                "http://schemas.android.com/apk/res-auto", "layout_constraintWidth_max"));
    }

    @Test
    public void readingFieldsAreTextViewsNotInputs() throws Exception {
        for (String name : new String[] {
                "event_info.xml", "event_info_dialog.xml",
                "event_info_body.xml", "event_info_headline.xml"}) {
            String xml = read(LAYOUT + name);
            assertFalse(name + " uses an EditText", xml.contains("EditText"));
            assertFalse(name + " uses an outlined input", xml.contains("TextInputLayout"));
            assertFalse(name + " uses a filled input", xml.contains("TextInputEditText"));
        }
    }

    @Test
    public void headlineHasOneColourMarkAndNoBackgroundTint() throws Exception {
        Document headline = parse(LAYOUT + "event_info_headline.xml");
        assertTrue(byId(headline, "event_info_color_marker") != null);
        Element root = headline.getDocumentElement();
        assertFalse("the headline must not be filled with the event colour",
                root.hasAttributeNS(ANDROID_NS, "background"));
    }

    @Test
    public void titleWrapsInsteadOfClipping() throws Exception {
        Document headline = parse(LAYOUT + "event_info_headline.xml");
        Element title = byId(headline, "title");
        assertTrue(title != null);
        assertEquals("wrap_content", title.getAttributeNS(ANDROID_NS, "layout_height"));
        assertFalse("title must not be single-line", title.hasAttributeNS(ANDROID_NS, "singleLine")
                && "true".equals(title.getAttributeNS(ANDROID_NS, "singleLine")));
    }

    @Test
    public void actionMenuKeepsEditInTheBarAndTheRestInOverflow() throws Exception {
        Document menu = parse("src/main/res/menu/event_info_title_bar.xml");
        String app = "http://schemas.android.com/apk/res-auto";
        NodeList items = menu.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttributeNS(ANDROID_NS, "id");
            String show = item.getAttributeNS(app, "showAsAction");
            if (id.endsWith("info_action_edit")) {
                assertEquals("edit must stay in the bar", "ifRoom", show);
            } else if (!id.endsWith("info_action_reschedule")) {
                assertEquals(id + " must go to overflow", "never", show);
            }
        }
    }

    @Test
    public void eventColourIsNeverUsedForText() throws Exception {
        String fragment = read("src/main/java/com/android/calendar/EventInfoFragment.java");
        // Text colours come from the theme. The only use of the event colour is the marker.
        assertFalse(fragment.contains("setTextColor(mCurrentColor"));
        assertFalse(fragment.contains("getAdaptiveTextColor"));
        assertFalse(fragment.contains("mHeadlines"));
    }

    @Test
    public void eventLookupAndQueryAreUnchanged() throws Exception {
        String fragment = read("src/main/java/com/android/calendar/EventInfoFragment.java");
        // Same query, same event ID source, same instance times as before the restyle.
        assertTrue(fragment.contains(
                "mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,"));
        assertTrue(fragment.contains("mEventId = savedInstanceState.getLong(BUNDLE_KEY_EVENT_ID);"));
        assertTrue(fragment.contains(
                "mStartMillis = savedInstanceState.getLong(BUNDLE_KEY_START_MILLIS);"));
        assertTrue(fragment.contains("mEndMillis = savedInstanceState.getLong(BUNDLE_KEY_END_MILLIS);"));
    }

    @Test
    public void permissionRulesAreStillAppliedToTheActions() throws Exception {
        String fragment = read("src/main/java/com/android/calendar/EventInfoFragment.java");
        assertTrue(fragment.contains("EventInfoRules.canModifyCalendar("));
        assertTrue(fragment.contains("EventInfoRules.canModifyEvent("));
        assertTrue(fragment.contains("EventInfoRules.showReschedule("));
    }

    /** The full-screen page is a header that runs under the status bar plus an overlapping panel. */
    @Test
    public void fullScreenPageHasIllustratedHeaderAndOverlappingPanel() throws Exception {
        Document full = parse(LAYOUT + "event_info.xml");
        Element header = byId(full, "event_info_header");
        assertTrue("missing illustrated header", header != null);
        assertEquals("@dimen/event_info_header_height",
                header.getAttributeNS(ANDROID_NS, "layout_height"));
        Element art = byId(full, "event_info_header_art");
        assertTrue("header must carry its own illustration", art != null);
        assertEquals("@drawable/event_info_header_art", art.getAttributeNS(ANDROID_NS, "src"));

        Element panel = byId(full, "event_info_panel");
        assertTrue("missing content panel", panel != null);
        assertEquals("panel must overlap the header by the overlap dimen",
                "@dimen/event_info_panel_overlap",
                panel.getAttributeNS(ANDROID_NS, "layout_marginTop"));
        assertEquals("@drawable/event_info_panel_background",
                panel.getAttributeNS(ANDROID_NS, "background"));
        assertEquals("panel must run to the edges: no start margin",
                "", panel.getAttributeNS(ANDROID_NS, "layout_marginStart"));
        assertEquals("panel must run to the edges: no end margin",
                "", panel.getAttributeNS(ANDROID_NS, "layout_marginEnd"));
    }

    @Test
    public void fullScreenPageHasNoToolbarAndNoCardInsideThePanel() throws Exception {
        String full = read(LAYOUT + "event_info.xml");
        assertFalse("the full page must not keep the toolbar", full.contains("app_bar"));
        assertFalse("the full page must not keep an AppBarLayout", full.contains("AppBarLayout"));
        String body = read(LAYOUT + "event_info_body.xml");
        assertFalse("the body must not wrap content in a card", body.contains("MaterialCardView"));
        assertFalse("the body must not set outer margins on the panel",
                body.contains("layout_marginHorizontal"));
    }

    /** Edit is hidden until EventInfoFragment finds that the user can edit the event. */
    @Test
    public void floatingEditStartsHidden() throws Exception {
        Document bar = parse(LAYOUT + "event_info_action_bar.xml");
        Element edit = byId(bar, "event_info_action_edit");
        assertTrue("missing floating edit", edit != null);
        assertEquals("gone", edit.getAttributeNS(ANDROID_NS, "visibility"));
        assertTrue("missing back target", byId(bar, "event_info_nav_back") != null);
        assertTrue("missing more target", byId(bar, "event_info_action_more") != null);
    }

    /** The circle and the icon are separate views, so the circle's alpha never fades the icon. */
    @Test
    public void actionCircleAndIconAreSeparateAndOpaque() throws Exception {
        String bar = read(LAYOUT + "event_info_action_bar.xml");
        assertFalse("a whole action must not be given an alpha", bar.contains("android:alpha"));
        assertTrue("each action needs a 48dp touch target",
                read("src/main/res/values/dimens.xml").contains(
                        "<dimen name=\"event_info_action_size\">48dp</dimen>"));
        int circles = bar.split("@drawable/event_info_action_circle", -1).length - 1;
        assertEquals("three actions each with their own circle", 3, circles);
    }

    @Test
    public void headerHeightIsQualifiedNotOneSizeForAllWindows() throws Exception {
        String[] dirs = {"values", "values-land", "values-sw600dp", "values-sw600dp-land"};
        for (String dir : dirs) {
            String xml = read("src/main/res/" + dir + "/dimens.xml");
            assertTrue(dir + " must define the header height",
                    xml.contains("name=\"event_info_header_height\""));
        }
    }

    /** Only the full-screen Activity gives up decor fitting; the dialog path is unchanged. */
    @Test
    public void onlyTheFullScreenActivityLetsTheHeaderRunUnderTheStatusBar() throws Exception {
        String activity = read("src/main/java/com/android/calendar/EventInfoActivity.java");
        assertTrue(activity.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"));
        assertTrue(activity.contains("setContentView(R.layout.simple_frame_layout)"));
        assertTrue(activity.contains("setContentView(R.layout.event_info_activity)"));
        String container = read(LAYOUT + "event_info_activity.xml");
        assertFalse("the full-screen container must not keep the drawer",
                container.contains("DrawerLayout"));
        assertTrue(container.contains("@+id/main_frame"));
    }

    @Test
    public void fragmentInsetsTheActionBarAndAvoidsTheCutout() throws Exception {
        String fragment = read("src/main/java/com/android/calendar/EventInfoFragment.java");
        assertTrue(fragment.contains("WindowInsetsCompat.Type.displayCutout()"));
        assertTrue(fragment.contains("WindowInsetsCompat.Type.systemBars()"));
        assertTrue(fragment.contains("findEditAction()"));
    }
}
