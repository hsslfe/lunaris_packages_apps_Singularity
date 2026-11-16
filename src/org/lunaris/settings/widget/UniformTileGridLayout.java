/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.settings.R;

/** Three columns of equal-height tiles, sized from the longest visible label. */
public class UniformTileGridLayout extends ViewGroup {
    private static final int COLUMNS = 3;
    private final int mGap;

    public UniformTileGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGap = getResources().getDimensionPixelSize(R.dimen.singularity_tile_gap);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = Math.round(96 * getResources().getDisplayMetrics().density) * COLUMNS
                    + mGap * (COLUMNS - 1) + getPaddingLeft() + getPaddingRight();
        }
        int contentWidth = Math.max(0,
                width - getPaddingLeft() - getPaddingRight() - mGap * (COLUMNS - 1));
        int count = 0;
        int tileHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View tile = getChildAt(i);
            if (tile.getVisibility() == GONE) continue;
            int column = physicalColumn(count++ % COLUMNS);
            int tileWidth = columnWidth(contentWidth, column);
            tile.measure(MeasureSpec.makeMeasureSpec(tileWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            tileHeight = Math.max(tileHeight, tile.getMeasuredHeight());
        }
        count = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View tile = getChildAt(i);
            if (tile.getVisibility() == GONE) continue;
            int column = physicalColumn(count++ % COLUMNS);
            tile.measure(MeasureSpec.makeMeasureSpec(columnWidth(contentWidth, column),
                    MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tileHeight, MeasureSpec.EXACTLY));
        }
        int rows = (count + COLUMNS - 1) / COLUMNS;
        int height = getPaddingTop() + getPaddingBottom() + rows * tileHeight
                + Math.max(0, rows - 1) * mGap;
        setMeasuredDimension(resolveSizeAndState(width, widthMeasureSpec, 0),
                resolveSizeAndState(Math.max(height, getSuggestedMinimumHeight()),
                        heightMeasureSpec, 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int contentWidth = Math.max(0,
                getWidth() - getPaddingLeft() - getPaddingRight() - mGap * (COLUMNS - 1));
        int count = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View tile = getChildAt(i);
            if (tile.getVisibility() == GONE) continue;
            int column = physicalColumn(count % COLUMNS);
            int x = getPaddingLeft() + contentWidth * column / COLUMNS + mGap * column;
            int y = getPaddingTop() + (count / COLUMNS) * (tile.getMeasuredHeight() + mGap);
            tile.layout(x, y, x + tile.getMeasuredWidth(), y + tile.getMeasuredHeight());
            count++;
        }
    }

    private int physicalColumn(int column) {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? COLUMNS - 1 - column : column;
    }

    private static int columnWidth(int contentWidth, int column) {
        return contentWidth * (column + 1) / COLUMNS - contentWidth * column / COLUMNS;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }
}
