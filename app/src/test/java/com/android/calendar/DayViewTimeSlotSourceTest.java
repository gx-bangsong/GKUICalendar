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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * Static checks of the day-view time block in {@link DayView}. They read the source text on the
 * plain JVM. They guard rules that a pure model test cannot see: the preview never writes, the
 * week view is gated out, the editor receives the block's own millis, and the touch path claims
 * handle drags before scrolling and scaling. They do not prove touch behaviour; that needs a
 * device.
 */
public class DayViewTimeSlotSourceTest {

    private static final String DAY_VIEW = "src/main/java/com/android/calendar/DayView.java";
    private static final String STRINGS = "src/main/res/values/strings.xml";
    private static final String IDS = "src/main/res/values/ids_day_slot.xml";

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    /** The text of one method, from its signature to the closing brace at the same indent. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing " + signature, start >= 0);
        int end = source.indexOf("\n    }\n", start);
        assertTrue("unterminated " + signature, end > start);
        return source.substring(start, end);
    }

    private static final String[] WRITE_OR_PROVIDER = {
            "ContentResolver", "CalendarContract", "insert(", "update(", "delete(", "query(",
            "saveEvent", "mService", "EventLoader", "mEventLoader"
    };

    @Test
    public void previewHelpersNeverTouchProviderOrWrite() throws Exception {
        String source = read(DAY_VIEW);
        String[] helpers = {
                "private boolean handleSlotPreviewTouch(", "private void cancelSlotDrag(",
                "private void refreshSlotPreview(", "private void startSlotPreview(",
                "private void openSlotPreviewEditor(", "private void drawSlotPreview(",
                "private void drawSlotLabel(", "private boolean isSlotPreviewShown(",
                "private int slotHandleAt(", "private boolean isInSlotBlock("
        };
        for (String signature : helpers) {
            String body = method(source, signature);
            for (String forbidden : WRITE_OR_PROVIDER) {
                assertFalse(signature + " must not reference " + forbidden,
                        body.contains(forbidden));
            }
        }
    }

    @Test
    public void drawingDoesNoLookupsAndNoDispatch() throws Exception {
        // onDraw runs every frame: no time-zone or formatting lookups, no controller calls
        String source = read(DAY_VIEW);
        for (String signature : new String[] {
                "private void drawSlotPreview(", "private void drawSlotLabel("}) {
            String body = method(source, signature);
            assertFalse(signature + " must not call Utils", body.contains("Utils."));
            assertFalse(signature + " must not dispatch", body.contains("mController"));
            assertFalse(signature + " must not format", body.contains("formatDateRange"));
        }
    }

    @Test
    public void cancelOnlyRestoresTheDraggedEdgeAndDispatchesNothing() throws Exception {
        String body = method(read(DAY_VIEW), "private void cancelSlotDrag(");
        assertFalse(body.contains("mController"));
        assertTrue(body.contains("dragStart(mSlotDownStartMinute)"));
        assertTrue(body.contains("dragEnd(mSlotDownEndMinute)"));
    }

    @Test
    public void editorReceivesTheBlocksOwnStartAndEnd() throws Exception {
        String source = read(DAY_VIEW);
        String body = method(source, "private void openSlotPreviewEditor(");
        assertTrue(body.contains("EventType.CREATE_EVENT"));
        assertTrue(Pattern.compile("mSlotStartMillis,\\s*mSlotEndMillis").matcher(body).find());
        // The millis come from the same model the drawing uses
        String refresh = method(source, "private void refreshSlotPreview(");
        assertTrue(refresh.contains("mSlotPreview.startMillis("));
        assertTrue(refresh.contains("mSlotPreview.endMillis("));
    }

    @Test
    public void releasingAHandleOpensTheEditorOnlyForATapNotADrag() throws Exception {
        String body = method(read(DAY_VIEW), "private boolean handleSlotPreviewTouch(");
        assertEquals("the editor opens from one place in the touch path", 1,
                count(body, "openSlotPreviewEditor("));
        assertTrue(body.contains("if (moved) {"));
    }

    @Test
    public void weekViewIsGatedOutOfEveryNewPath() throws Exception {
        String source = read(DAY_VIEW);
        assertTrue(method(source, "private boolean isSlotPreviewShown(")
                .contains("mNumDays == 1"));
        assertTrue(method(source, "private boolean handleSlotPreviewTouch(")
                .contains("mNumDays != 1"));
        assertTrue(source.contains("if (mNumDays == 1 && mSelectedEvent == null && !mSelectionAllday)"));
    }

    @Test
    public void handleTouchRunsBeforeScalingAndScrolling() throws Exception {
        String source = read(DAY_VIEW);
        String onTouch = method(source, "public boolean onTouchEvent(MotionEvent ev)");
        int handle = onTouch.indexOf("handleSlotPreviewTouch(ev)");
        int scale = onTouch.indexOf("mScaleGestureDetector.onTouchEvent(ev)");
        assertTrue(handle > 0);
        assertTrue(scale > handle);
    }

    @Test
    public void tapOnTheBlockIsCheckedBeforeAnySelectionChange() throws Exception {
        String tap = method(read(DAY_VIEW), "private void doSingleTapUp(MotionEvent ev)");
        int inBlock = tap.indexOf("isInSlotBlock(x, y)");
        int clear = tap.indexOf("clearSlotPreview()");
        int select = tap.indexOf("setSelectionFromPosition(x, y, false)");
        assertTrue(inBlock > 0);
        assertTrue(inBlock < clear);
        assertTrue(clear < select);
    }

    @Test
    public void accessibilityActionIdsAreDeclaredAndLabelled() throws Exception {
        String source = read(DAY_VIEW);
        String ids = read(IDS);
        String strings = read(STRINGS);
        String[] actionIds = {
                "day_slot_action_open", "day_slot_action_start_earlier",
                "day_slot_action_start_later", "day_slot_action_end_earlier",
                "day_slot_action_end_later"
        };
        for (String id : actionIds) {
            assertTrue(source.contains("R.id." + id));
            assertTrue(ids.contains("name=\"" + id + "\""));
        }
        for (String name : new String[] {
                "day_slot_description", "day_slot_open_action", "day_slot_start_earlier",
                "day_slot_start_later", "day_slot_end_earlier", "day_slot_end_later"}) {
            assertTrue(strings.contains("name=\"" + name + "\""));
        }
    }

    @Test
    public void handlesAreClampedToTheVisibleGrid() throws Exception {
        String body = method(read(DAY_VIEW), "private float slotHandleY(");
        assertTrue(body.contains("slotVisibleTop()"));
        assertTrue(body.contains("slotVisibleBottom()"));
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
