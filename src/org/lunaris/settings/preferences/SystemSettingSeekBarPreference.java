/*
 * Copyright (C) 2018-2019 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.preferences;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

public class SystemSettingSeekBarPreference extends CustomSeekBarPreference {

    public SystemSettingSeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }

    public SystemSettingSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }

    public SystemSettingSeekBarPreference(Context context) {
        super(context, null);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // Match Now Bar slider headers to the medium-weight chip headings
        // without changing the typography of other Settings seekbars.
        if (getKey() != null && getKey().startsWith("now_bar_")) {
            final TextView title = (TextView) holder.findViewById(android.R.id.title);
            if (title != null) {
                title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
        }
    }

    /** Set the adjustment step for an individual slider without changing other preferences. */
    public void setInterval(int interval) {
        mInterval = Math.max(1, interval);
        notifyChanged();
    }
}
