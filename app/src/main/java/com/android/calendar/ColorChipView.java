/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;




/**
 * A custom view for a color chip for an event that can be drawn differently
 * accroding to the event's status.
 *
 */
public class ColorChipView extends View {

    public static final int DRAW_FULL = 0;
    // Style of drawing
    // Full rectangle for accepted events
    // Border for tentative events
    // Cross-hatched with 50% transparency for declined events
    public static final int DRAW_BORDER = 1;
    public static final int DRAW_FADED = 2;
    private static final String TAG = "ColorChipView";
    private static final int DEF_BORDER_WIDTH = 4;
    int mBorderWidth = DEF_BORDER_WIDTH;
    int mColor;
    private int mDrawStyle = DRAW_FULL;
    private float mDefStrokeWidth;
    private Paint mPaint;
    private final RectF mRectF = new RectF();

    public ColorChipView(Context context) {
        super(context);
        init();
    }

    public ColorChipView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mDefStrokeWidth = mPaint.getStrokeWidth();
        mPaint.setStyle(Style.FILL_AND_STROKE);
        mPaint.setAntiAlias(true);
    }


    public void setDrawStyle(int style) {
        if (style != DRAW_FULL && style != DRAW_BORDER && style != DRAW_FADED) {
            return;
        }
        mDrawStyle = style;
        invalidate();
    }

    public void setBorderWidth(int width) {
        if (width >= 0) {
            mBorderWidth = width;
            invalidate();
        }
    }

    public void setColor(int color) {
        mColor = color;
        invalidate();
    }

    @Override
    public void onDraw(Canvas c) {

        float right = getWidth();
        float bottom = getHeight();
        mPaint.setColor(mDrawStyle == DRAW_FADED ?
                Utils.getDeclinedColorFromColor(mColor) : mColor);
        mRectF.set(0, 0, right, bottom);
        float radius = Math.min(right, bottom) / 2f;

        switch (mDrawStyle) {
            case DRAW_FADED:
            case DRAW_FULL:
                mPaint.setStyle(Style.FILL);
                c.drawRoundRect(mRectF, radius, radius, mPaint);
                break;
            case DRAW_BORDER:
                if (mBorderWidth <= 0) {
                    return;
                }
                mPaint.setStyle(Style.STROKE);
                mPaint.setStrokeWidth(mBorderWidth);
                float inset = mBorderWidth / 2f;
                mRectF.inset(inset, inset);
                radius = Math.max(0, radius - inset);
                c.drawRoundRect(mRectF, radius, radius, mPaint);
                break;
        }
    }
}
