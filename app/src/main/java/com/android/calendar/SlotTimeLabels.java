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

/**
 * Layout of the start and end labels of the day-view time block, in the hour column. This class
 * has no Android types, so the rules can be checked by JVM tests. DayView converts the results to
 * drawing calls.
 */
final class SlotTimeLabels {

    private SlotTimeLabels() {
    }

    /**
     * Centre Y of the start label and of the end label, from the block's edge Y values. Each label
     * sits on its edge. When the edges are closer than one label height, both labels move apart
     * symmetrically around the middle, so the two texts never overlap. The block and its real
     * boundaries are not changed.
     *
     * @return {startCentre, endCentre}
     */
    static float[] centres(float startEdgeY, float endEdgeY, float labelHeight) {
        if (endEdgeY - startEdgeY >= labelHeight) {
            return new float[] {startEdgeY, endEdgeY};
        }
        float mid = (startEdgeY + endEdgeY) / 2f;
        return new float[] {mid - labelHeight / 2f, mid + labelHeight / 2f};
    }

    /** Whether two labels, given by centre and height, would overlap. Touching is not overlap. */
    static boolean overlaps(float centreA, float heightA, float centreB, float heightB) {
        return Math.abs(centreA - centreB) < (heightA + heightB) / 2f;
    }

    /**
     * Keeps a label inside {@code [low, high]}, the visible grid. If the range is too short for the
     * label, it is centred in the range.
     */
    static float clampCentre(float centre, float height, float low, float high) {
        float minCentre = low + height / 2f;
        float maxCentre = high - height / 2f;
        if (maxCentre < minCentre) {
            return (low + high) / 2f;
        }
        return Math.max(minCentre, Math.min(maxCentre, centre));
    }

    /**
     * Whether a colour is light. A colour is light when black text contrasts more with it than
     * white text does (WCAG relative luminance). The alpha of {@code rgb} is ignored.
     */
    static boolean isLight(int rgb) {
        double luminance = 0.2126 * linear((rgb >> 16) & 0xFF)
                + 0.7152 * linear((rgb >> 8) & 0xFF)
                + 0.0722 * linear(rgb & 0xFF);
        return (luminance + 0.05) / 0.05 > 1.05 / (luminance + 0.05);
    }

    /**
     * Moves each RGB channel of {@code base} towards white when {@code lighter} is true, or towards
     * black otherwise, by {@code amount} (0 to 1). The alpha of {@code base} is kept.
     */
    static int shade(int base, boolean lighter, float amount) {
        int alpha = base & 0xFF000000;
        int red = shadeChannel((base >> 16) & 0xFF, lighter, amount);
        int green = shadeChannel((base >> 8) & 0xFF, lighter, amount);
        int blue = shadeChannel(base & 0xFF, lighter, amount);
        return alpha | (red << 16) | (green << 8) | blue;
    }

    private static int shadeChannel(int channel, boolean lighter, float amount) {
        float value = lighter ? channel + (255 - channel) * amount : channel * (1f - amount);
        return Math.round(Math.max(0f, Math.min(255f, value)));
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
