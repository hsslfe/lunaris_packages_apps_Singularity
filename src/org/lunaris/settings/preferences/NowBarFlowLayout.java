/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Content-sized wrapping chip row. Reserve full measured height for every wrapped
 * line so the following preference never draws on top of a chip.
 */
public class NowBarFlowLayout extends ViewGroup {
    private final int mGap;

    public NowBarFlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGap = Math.round(8 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int availableWidth = Math.max(0,
                MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
        final int childWidthSpec =
                MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST);
        final int childHeightSpec =
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(childWidthSpec, childHeightSpec);
            final int w = child.getMeasuredWidth();
            final int h = child.getMeasuredHeight();
            if (x != 0 && x + w > availableWidth) {
                x = 0;
                y += rowHeight + mGap;
                rowHeight = 0;
            }
            x += w + mGap;
            rowHeight = Math.max(rowHeight, h);
        }
        final int measuredHeight = getPaddingTop() + y + rowHeight + getPaddingBottom();
        setMeasuredDimension(
                View.resolveSize(getPaddingLeft() + availableWidth + getPaddingRight(),
                        widthMeasureSpec),
                View.resolveSize(measuredHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int availableWidth = Math.max(0,
                r - l - getPaddingLeft() - getPaddingRight());
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = 0;
        int y = getPaddingTop();
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            final int w = child.getMeasuredWidth();
            final int h = child.getMeasuredHeight();
            if (x != 0 && x + w > availableWidth) {
                x = 0;
                y += rowHeight + mGap;
                rowHeight = 0;
            }
            final int left = rtl ? getPaddingLeft() + availableWidth - x - w
                    : getPaddingLeft() + x;
            child.layout(left, y, left + w, y + h);
            x += w + mGap;
            rowHeight = Math.max(rowHeight, h);
        }
    }
}
