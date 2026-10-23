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

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Structure of the phone agenda row, read from the XML. The checks are positive: the date column
 * is a fixed square circle, the card is a rounded coloured block with no stroke, the title, time
 * and location come in that order, and selection is an outline view rather than an image bar.
 */
public class AgendaItemLayoutTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";
    private static final String LAYOUT = "src/main/res/layout/agenda_item.xml";
    private static final String DRAWABLE = "src/main/res/drawable/agenda_selected_outline.xml";

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

    @Test
    public void dateColumnIsFixedWidthAndCircleIsSquare() throws Exception {
        Element root = parse(LAYOUT).getDocumentElement();
        Element column = findById(root, "agenda_event_date_column");
        Element date = findById(root, "agenda_event_date");
        assertEquals("72dp", attr(column, ANDROID_NS, "layout_width"));
        // The date circle is a 44dp square, so its width equals its height.
        assertEquals("44dp", attr(date, ANDROID_NS, "layout_width"));
        assertEquals(attr(date, ANDROID_NS, "layout_width"),
                attr(date, ANDROID_NS, "layout_height"));
    }

    @Test
    public void cardIsRoundedAndHasNoStroke() throws Exception {
        Element root = parse(LAYOUT).getDocumentElement();
        Element card = findById(root, "agenda_event_card");
        assertEquals("@dimen/agenda_card_corner_radius",
                attr(card, APP_NS, "cardCornerRadius"));
        assertEquals("0dp", attr(card, APP_NS, "strokeWidth"));
        assertEquals("0dp", attr(card, APP_NS, "cardElevation"));
    }

    @Test
    public void textOrderIsTitleThenTimeThenLocation() throws Exception {
        Element root = parse(LAYOUT).getDocumentElement();
        Element container = findById(root, "agenda_item_text_container");
        assertEquals("@+id/title", childId(container, 0));
        assertEquals("@+id/when", childId(container, 1));
        assertEquals("@+id/where", childId(container, 2));
    }

    @Test
    public void selectionIsAnOutlineOverTheCard() throws Exception {
        Element root = parse(LAYOUT).getDocumentElement();
        Element marker = findById(root, "selected_marker");
        // An overlay View with the outline drawable, not an ImageView side bar.
        assertEquals("View", marker.getTagName());
        assertEquals("@drawable/agenda_selected_outline",
                attr(marker, ANDROID_NS, "background"));
        assertEquals("gone", attr(marker, ANDROID_NS, "visibility"));
    }

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

    @Test
    public void contentLayerHasPaddingResourceAndMinimumHeight() throws Exception {
        Element root = parse(LAYOUT).getDocumentElement();
        Element content = findById(root, "agenda_item_content");
        assertEquals("@dimen/agenda_card_padding", attr(content, ANDROID_NS, "padding"));
        assertEquals("@dimen/agenda_card_min_height", attr(content, ANDROID_NS, "minHeight"));
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
}
