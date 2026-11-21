/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.preferences;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import java.util.function.IntConsumer;

/**
 * A row of choices kept as an int in Settings.System. With {@link #setOnChipClicked} the chips are
 * buttons instead, and nothing is kept.
 */
public class NowBarChipsPreference extends Preference {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    private final CharSequence[] mEntries;
    private final String[] mValues;
    private final int mDefault;
    private IntConsumer mOnClicked;

    public NowBarChipsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.lunaris_now_bar_chips);
        setSelectable(false);
        setPersistent(false);
        mEntries = context.getResources().getTextArray(
                attrs.getAttributeResourceValue(ANDROID, "entries", 0));
        final int values = attrs.getAttributeResourceValue(ANDROID, "entryValues", 0);
        mValues = values != 0 ? context.getResources().getStringArray(values) : new String[0];
        mDefault = attrs.getAttributeIntValue(ANDROID, "defaultValue", 0);
    }

    public void setOnChipClicked(IntConsumer onClicked) {
        mOnClicked = onClicked;
        notifyChanged();
    }

    private int value() {
        return Settings.System.getIntForUser(getContext().getContentResolver(), getKey(), mDefault,
                UserHandle.USER_CURRENT);
    }

    private void pick(int index) {
        if (mOnClicked != null) {
            mOnClicked.accept(index);
            return;
        }
        Settings.System.putIntForUser(getContext().getContentResolver(), getKey(),
                Integer.parseInt(mValues[index]), UserHandle.USER_CURRENT);
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // Own the title and summary IDs: they must not be rebound by the framework
        // as other preference types in Lunaris's card-style PreferenceScreen are recycled.
        final TextView title = (TextView) holder.findViewById(R.id.now_bar_preference_title);
        final CharSequence heading = getTitle();
        final boolean hasHeading = !android.text.TextUtils.isEmpty(heading);
        if (title != null) {
            title.setText(heading);
            title.setVisibility(hasHeading ? View.VISIBLE : View.GONE);
        }
        // All labelled chip rows align with Now Bar's slider titles. The Looks
        // row has no repeated heading, so keep its chips nearer the card top.
        final View row = holder.itemView;
        final int topPadding = Math.round((hasHeading ? 26 : 12)
                * getContext().getResources().getDisplayMetrics().density);
        row.setPadding(row.getPaddingLeft(), topPadding,
                row.getPaddingRight(), row.getPaddingBottom());
        final TextView summary = (TextView) holder.findViewById(R.id.now_bar_preference_summary);
        if (summary != null) {
            final CharSequence text = getSummary();
            summary.setText(text);
            summary.setVisibility(android.text.TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        }
        final ViewGroup chips = (ViewGroup) holder.findViewById(R.id.now_bar_chips);
        chips.removeAllViews();
        final boolean choosing = mOnClicked == null;
        final int value = value();
        for (int i = 0; i < mEntries.length; i++) {
            final boolean selected = choosing ? Integer.parseInt(mValues[i]) == value : i == value;
            chips.addView(chip(mEntries[i], selected, choosing, i));
        }
    }

    private View chip(CharSequence text, boolean selected, boolean choosing, int index) {
        final Context context = getContext();
        final float density = context.getResources().getDisplayMetrics().density;
        final TextView chip = new TextView(context);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(Math.round(52 * density));
        chip.setPadding(Math.round(14 * density), 0, Math.round(14 * density), 0);
        chip.setTextSize(14);
        chip.setTextColor(context.getColor(selected
                ? com.android.internal.R.color.materialColorOnSecondaryContainer
                : com.android.internal.R.color.materialColorOnSurface));
        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(12 * density);
        shape.setColor(selected
                ? context.getColor(com.android.internal.R.color.materialColorSecondaryContainer)
                : Color.TRANSPARENT);
        shape.setStroke(Math.round(density), context.getColor(selected
                ? com.android.internal.R.color.materialColorPrimary
                : com.android.internal.R.color.materialColorOutlineVariant));
        final int ripple = context.getColor(
                com.android.internal.R.color.materialColorOnSurface) & 0x1FFFFFFF;
        chip.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), shape, null));
        chip.setEnabled(isEnabled());
        chip.setAlpha(isEnabled() ? 1f : 0.5f);
        chip.setOnClickListener(v -> pick(index));
        chip.setSelected(selected);
        chip.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (choosing) {
                    info.setClassName(RadioButton.class.getName());
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            }
        });
        return chip;
    }
}
