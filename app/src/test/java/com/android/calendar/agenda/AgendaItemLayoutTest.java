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

package com.android.calendar.agenda;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Structure of the standalone agenda rows, read from the XML, and the protected classic rows the
 * month panel still uses. The checks are positive: the weekday sits above the date number, the
 * date circle is square and fixed, the card is a rounded coloured block with no stroke, the tablet
 * row has its own dimens and shows the date, and selection is an outline view on both sizes.
 */
public class AgendaItemLayoutTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";
    private static final String PHONE_CARD = "src/main/res/layout/agenda_item_card.xml";
    private static final String TABLET_CARD = "src/main/res/layout-sw600dp/agenda_item_card.xml";
    private static final String PHONE_CLASSIC = "src/main/res/layout/agenda_item.xml";
    private static final String TABLET_CLASSIC = "src/main/res/layout-sw600dp/agenda_item.xml";
    private static final String PHONE_DIMENS = "src/main/res/values/dimens.xml";
    private static final String TABLET_DIMENS = "src/main/res/values-sw600dp/dimens.xml";
    private static final String DRAWABLE = "src/main/res/drawable/agenda_selected_outline.xml";
    private static final String ALL_IN_ONE = "src/main/java/com/android/calendar/AllInOneActivity.java";

    private static Document parse(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new File(path));
    }

    private static Element findById(Element root, String id) {
        NodeList all = root.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (("@+id/" + id).equals(e.getAttributeNS(ANDROID_NS, "id"))
                    || ("@id/" + id).equals(e.getAttributeNS(ANDROID_NS, "id"))) {
                return e;
            }
        }
        return null;
    }

    private static String attr(Element e, String ns, String name) {
        return e.getAttributeNS(ns, name);
    }

    /** The value of one dimen in one values file, for example "44dp". */
    private static String dimen(String valuesFile, String name) throws Exception {
        NodeList all = parse(valuesFile).getElementsByTagName("dimen");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (name.equals(e.getAttribute("name"))) {
                return e.getTextContent().trim();
            }
        }
        return null;
    }

    private static float spValue(String dimenValue) {
        assertTrue(dimenValue, dimenValue.endsWith("sp"));
        return Float.parseFloat(dimenValue.substring(0, dimenValue.length() - 2));
    }

    private static String childId(Element parent, int index) {
        int seen = 0;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (seen == index) {
                return ((Element) n).getAttributeNS(ANDROID_NS, "id");
            }
            seen++;
        }
        return null;
    }

    /** Every @dimen/ reference in the tree, including the root element's own attributes. */
    private static List<String> dimenReferences(Element root) {
        List<Element> elements = new ArrayList<>();
        elements.add(root);
        NodeList all = root.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            elements.add((Element) all.item(i));
        }
        List<String> refs = new ArrayList<>();
        for (Element e : elements) {
            for (int a = 0; a < e.getAttributes().getLength(); a++) {
                String value = e.getAttributes().item(a).getNodeValue();
                if (value.startsWith("@dimen/")) {
                    refs.add(value.substring("@dimen/".length()));
                }
            }
        }
        return refs;
    }

    // ---------- Phone card row ----------

    @Test
    public void phoneWeekdaySitsAboveTheDateNumber() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element column = findById(root, "agenda_event_date_column");
        assertEquals("@+id/agenda_event_day", childId(column, 0));
        assertEquals("@+id/agenda_event_date", childId(column, 1));
    }

    @Test
    public void phoneDateCircleIsFixedAndSquareAndNumberIsCentred() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element date = findById(root, "agenda_event_date");
        assertEquals("@dimen/agenda_date_circle_size", attr(date, ANDROID_NS, "layout_width"));
        assertEquals(attr(date, ANDROID_NS, "layout_width"),
                attr(date, ANDROID_NS, "layout_height"));
        assertEquals("center", attr(date, ANDROID_NS, "gravity"));
        assertEquals("44dp", dimen(PHONE_DIMENS, "agenda_date_circle_size"));
        // The circle comes from code (today only, one circle). The layout does not paint a second.
        assertEquals("", attr(date, ANDROID_NS, "background"));
    }

    @Test
    public void phoneDateColumnIsFixedWidthFromItsDimen() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element column = findById(root, "agenda_event_date_column");
        assertEquals("@dimen/agenda_date_column_width",
                attr(column, ANDROID_NS, "layout_width"));
        assertEquals("72dp", dimen(PHONE_DIMENS, "agenda_date_column_width"));
    }

    @Test
    public void phoneCardIsRoundedAndHasNoStroke() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element card = findById(root, "agenda_event_card");
        assertEquals("@dimen/agenda_card_corner_radius",
                attr(card, APP_NS, "cardCornerRadius"));
        assertEquals("0dp", attr(card, APP_NS, "strokeWidth"));
        assertEquals("0dp", attr(card, APP_NS, "cardElevation"));
    }

    @Test
    public void phoneTextOrderIsTitleThenTimeThenLocation() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element container = findById(root, "agenda_item_text_container");
        assertEquals("@+id/title", childId(container, 0));
        assertEquals("@+id/when", childId(container, 1));
        assertEquals("@+id/where", childId(container, 2));
    }

    @Test
    public void phoneTitleWrapsToTwoLinesThenEllipsizesAndNeverShrinksBelowReadable()
            throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element title = findById(root, "title");
        assertEquals("end", attr(title, ANDROID_NS, "ellipsize"));
        assertEquals("2", attr(title, ANDROID_NS, "maxLines"));
        // Font sizes are fixed by dimens with a readable floor, not computed down to fit.
        assertTrue(spValue(dimen(PHONE_DIMENS, "agenda_card_title_text_size")) >= 14f);
        assertTrue(spValue(dimen(PHONE_DIMENS, "agenda_card_meta_text_size")) >= 12f);
    }

    @Test
    public void phoneContentPaddingAndMinimumHeightComeFromDimens() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element content = findById(root, "agenda_item_content");
        assertEquals("@dimen/agenda_card_padding", attr(content, ANDROID_NS, "padding"));
        assertEquals("@dimen/agenda_card_min_height", attr(content, ANDROID_NS, "minHeight"));
    }

    @Test
    public void phoneSelectionIsAnOutlineViewNotAnImageBar() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        Element marker = findById(root, "selected_marker");
        assertEquals("View", marker.getTagName());
        assertEquals("@drawable/agenda_selected_outline",
                attr(marker, ANDROID_NS, "background"));
        assertEquals("gone", attr(marker, ANDROID_NS, "visibility"));
    }

    @Test
    public void phoneCardRowHasNoColourDotOrSideBar() throws Exception {
        Element root = parse(PHONE_CARD).getDocumentElement();
        assertNull(findById(root, "agenda_item_color"));
        assertEquals(0, root.getElementsByTagName("ImageView").getLength());
        assertEquals(0, root.getElementsByTagName("com.android.calendar.ColorChipView").getLength());
    }

    // ---------- Tablet card row ----------

    @Test
    public void tabletDateColumnIsPresentAndNeverHiddenInTheLayout() throws Exception {
        Element root = parse(TABLET_CARD).getDocumentElement();
        Element column = findById(root, "agenda_event_date_column");
        assertNotNull("tablet row must carry the date column", column);
        // The adapter shows the first row of each day and hides the rest, so the XML default
        // must be visible; a gone default would hide the date on every row.
        assertEquals("", attr(column, ANDROID_NS, "visibility"));
        assertEquals("@+id/agenda_event_day", childId(column, 0));
        assertEquals("@+id/agenda_event_date", childId(column, 1));
    }

    @Test
    public void tabletUsesItsOwnDensityDimensNotScaledPhoneOnes() throws Exception {
        // Every dimen the tablet row refers to is defined in values-sw600dp, so the tablet
        // density is set on its own and is not inherited from the phone values.
        Element root = parse(TABLET_CARD).getDocumentElement();
        for (String name : dimenReferences(root)) {
            assertNotNull("tablet dimen " + name + " must be set for sw600dp",
                    dimen(TABLET_DIMENS, name));
        }
        // The tablet values differ from the phone values for the sizes that matter on a wide
        // screen: the date circle, the title and the reading width.
        assertEquals("52dp", dimen(TABLET_DIMENS, "agenda_date_circle_size"));
        assertEquals("18sp", dimen(TABLET_DIMENS, "agenda_card_title_text_size"));
        assertEquals("560dp", dimen(TABLET_DIMENS, "agenda_card_text_max_width"));
    }

    @Test
    public void tabletTextIsCappedToAReadingWidth() throws Exception {
        Element root = parse(TABLET_CARD).getDocumentElement();
        for (String id : new String[] {"title", "when", "where"}) {
            assertEquals("@dimen/agenda_card_text_max_width",
                    attr(findById(root, id), ANDROID_NS, "maxWidth"));
        }
    }

    @Test
    public void tabletCardMatchesThePhoneDesignLanguage() throws Exception {
        Element root = parse(TABLET_CARD).getDocumentElement();
        Element card = findById(root, "agenda_event_card");
        assertEquals("@dimen/agenda_card_corner_radius",
                attr(card, APP_NS, "cardCornerRadius"));
        assertEquals("0dp", attr(card, APP_NS, "strokeWidth"));
        assertEquals("@+id/title", childId(findById(root, "agenda_item_text_container"), 0));
    }

    @Test
    public void tabletSelectionIsTheSameViewTypeAsPhone() throws Exception {
        Element tablet = findById(parse(TABLET_CARD).getDocumentElement(), "selected_marker");
        Element phone = findById(parse(PHONE_CARD).getDocumentElement(), "selected_marker");
        assertEquals(phone.getTagName(), tablet.getTagName());
        assertEquals("View", tablet.getTagName());
        assertEquals("@drawable/agenda_selected_outline",
                attr(tablet, ANDROID_NS, "background"));
    }

    @Test
    public void tabletRowHasNoColourDotOrSideBar() throws Exception {
        Element root = parse(TABLET_CARD).getDocumentElement();
        assertNull(findById(root, "agenda_item_color"));
        assertEquals(0, root.getElementsByTagName("ImageView").getLength());
        assertEquals(0, root.getElementsByTagName("com.android.calendar.ColorChipView").getLength());
    }

    /**
     * The tag of the text container's parent. AgendaWindowAdapter reads GridLayout params only
     * for the classic row, so this is the fact that decides which path a row may take.
     */
    private static String textContainerParentTag(String path) throws Exception {
        Element container = findById(parse(path).getDocumentElement(),
                "agenda_item_text_container");
        assertNotNull("text container in " + path, container);
        return ((Element) container.getParentNode()).getTagName();
    }

    @Test
    public void tabletCardTextContainerIsNotAGridChild() throws Exception {
        // The card row sits under a ConstraintLayout. A GridLayout cast on it throws
        // ClassCastException, which is why card rows take the outline-only selection path.
        assertEquals("androidx.constraintlayout.widget.ConstraintLayout",
                textContainerParentTag(TABLET_CARD));
    }

    @Test
    public void tabletClassicTextContainerIsAGridChild() throws Exception {
        // The classic sw600dp row is still a GridLayout, so the classic path keeps its params.
        assertEquals("GridLayout", textContainerParentTag(TABLET_CLASSIC));
    }

    // ---------- Outline drawable ----------

    @Test
    public void outlineDrawableIsRectangleWithThemePrimaryStroke() throws Exception {
        Element shape = parse(DRAWABLE).getDocumentElement();
        assertEquals("shape", shape.getTagName());
        assertEquals("rectangle", attr(shape, ANDROID_NS, "shape"));
        NodeList strokes = shape.getElementsByTagName("stroke");
        assertEquals(1, strokes.getLength());
        Element stroke = (Element) strokes.item(0);
        assertEquals("@dimen/agenda_card_outline_width", attr(stroke, ANDROID_NS, "width"));
        assertEquals("?attr/colorPrimary", attr(stroke, ANDROID_NS, "color"));
    }

    // ---------- Protected classic rows used by the month panel ----------

    @Test
    public void monthPanelPhoneRowKeepsItsClassicDateColumnAndCard() throws Exception {
        Element root = parse(PHONE_CLASSIC).getDocumentElement();
        assertNotNull(findById(root, "agenda_event_date_column"));
        assertNotNull(findById(root, "agenda_event_card"));
        assertNotNull(findById(root, "agenda_event_date"));
    }

    @Test
    public void monthPanelTabletRowKeepsItsClassicColourChipAndImageSelection() throws Exception {
        Element root = parse(TABLET_CLASSIC).getDocumentElement();
        assertNotNull(findById(root, "agenda_item_color"));
        assertEquals("ImageView", findById(root, "selected_marker").getTagName());
    }

    @Test
    public void monthSecondaryPanelIsWiredToTheClassicRows() throws Exception {
        // Wiring guard: the month's secondary panel is created with the classic rows, and only
        // the standalone agenda view asks for the card rows. It is a guard on the wiring, not a
        // visual check.
        String source = new String(java.nio.file.Files.readAllBytes(
                new File(ALL_IN_ONE).toPath()), "UTF-8");
        assertTrue(source.contains("secFrag = new AgendaFragment(timeMillis, false);"));
        assertTrue(source.contains("new AgendaFragment(timeMillis, false, true)"));
    }
}
