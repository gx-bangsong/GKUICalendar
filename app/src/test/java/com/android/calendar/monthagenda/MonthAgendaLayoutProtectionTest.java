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

package com.android.calendar.monthagenda;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Month protection. The month schedule rows keep their own layout and ids. The standalone agenda
 * restyle must not change them, because the same "agenda_event_*" names exist in the month
 * layouts and the widgets. These checks read the month XML and assert its positive structure.
 */
public class MonthAgendaLayoutProtectionTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static Document parse(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new File(path));
    }

    private static Element findById(Element root, String id) {
        NodeList all = root.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (("@+id/" + id).equals(e.getAttributeNS(ANDROID_NS, "id"))) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void monthEventRowKeepsItsColourBarTitleTimeAndLocation() throws Exception {
        Element root = parse("src/main/res/layout/item_agenda_event.xml").getDocumentElement();
        // The month row keeps its own colour bar; the standalone agenda change does not touch it.
        assertEquals("View", findById(root, "agenda_event_color").getTagName());
        assertEquals("TextView", findById(root, "agenda_event_title").getTagName());
        assertEquals("TextView", findById(root, "agenda_event_time").getTagName());
        assertEquals("TextView", findById(root, "agenda_event_location").getTagName());
    }
}
