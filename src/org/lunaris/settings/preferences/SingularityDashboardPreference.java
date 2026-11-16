/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.preferences;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

public class SingularityDashboardPreference extends Preference {

    public interface NavigationListener {
        void onNavigate(String key);
    }

    private NavigationListener mNavigationListener;
    private Runnable mBackListener;
    private final View.OnLayoutChangeListener mLayoutListener =
            (v, l, t, r, b, oldL, oldT, oldR, oldB) -> updatePreviewVisibility(v);

    public SingularityDashboardPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_singularity_dashboard);
        setSelectable(false);
        setPersistent(false);
    }

    public void setNavigationListener(NavigationListener listener) {
        mNavigationListener = listener;
    }

    public void setBackListener(Runnable listener) {
        mBackListener = listener;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        View root = holder.itemView;
        // Each card is an independent accessibility/focus target.
        root.setBackgroundColor(getContext().getColor(R.color.singularity_background));
        root.setFocusable(false);
        root.setClickable(false);
        addPreview(root, R.id.singularity_monet, R.layout.singularity_dashboard_palette);
        addPreview(root, R.id.singularity_themes, R.layout.singularity_dashboard_theme_chips);
        addPreview(root, R.id.singularity_aod, R.layout.singularity_dashboard_aod_clock);
        root.findViewById(R.id.singularity_themes).setBackgroundResource(
                R.drawable.singularity_card_background);
        root.findViewById(R.id.singularity_aod).setBackgroundResource(
                R.drawable.singularity_card_background);
        root.removeOnLayoutChangeListener(mLayoutListener);
        root.addOnLayoutChangeListener(mLayoutListener);
        root.findViewById(R.id.singularity_back).setOnClickListener(v -> {
            if (mBackListener != null) {
                mBackListener.run();
            }
        });
        navigate(root.findViewById(R.id.singularity_lockscreen), "lock_screen",
                R.string.singularity_lockscreen);
        bind(root, R.id.singularity_monet, R.string.singularity_monet,
                R.string.singularity_monet_summary, R.drawable.ic_singularity_palette, "monet");
        bind(root, R.id.singularity_wallpapers, R.string.singularity_wallpapers,
                R.string.singularity_wallpapers_summary, R.drawable.ic_singularity_wallpaper,
                "wallpapers");
        bind(root, R.id.singularity_themes, R.string.singularity_theme_packs,
                R.string.singularity_theme_packs_summary, R.drawable.ic_singularity_layers,
                "themes");
        navigate(root.findViewById(R.id.singularity_theme_store), "theme_store",
                R.string.singularity_theme_packs);
        navigate(root.findViewById(R.id.singularity_fonts), "fonts", R.string.font_settings_title);
        bind(root, R.id.singularity_statusbar, R.string.singularity_statusbar, 0,
                R.drawable.ic_singularity_statusbar, "status_bar");
        bind(root, R.id.singularity_qs, R.string.singularity_qs, 0,
                R.drawable.ic_singularity_qs, "quick_settings");
        bind(root, R.id.singularity_aod, R.string.singularity_aod,
                R.string.singularity_aod_summary, R.drawable.ic_singularity_aod, "aod");
        bind(root, R.id.singularity_buttons, R.string.buttons_title, 0,
                R.drawable.ic_singularity_buttons, "buttons");
        bind(root, R.id.singularity_power, R.string.singularity_power, 0,
                R.drawable.ic_singularity_power, "power_menu");
        bind(root, R.id.singularity_notifications, R.string.singularity_notifications, 0,
                R.drawable.ic_singularity_notification, "notifications");
        bind(root, R.id.singularity_navigation, R.string.navigation_title, 0,
                R.drawable.ic_singularity_navigation, "navigation");
        bind(root, R.id.singularity_misc, R.string.singularity_miscellaneous, 0,
                R.drawable.ic_singularity_misc, "miscellaneous");
        bind(root, R.id.singularity_sound, R.string.sound_title, 0,
                R.drawable.ic_singularity_sound, "sound");

        int[] swatches = {R.id.singularity_swatch_one, R.id.singularity_swatch_two,
                R.id.singularity_swatch_three, R.id.singularity_swatch_four};
        int[] colors = {android.R.color.system_accent1_50, android.R.color.system_accent1_100,
                android.R.color.system_accent1_300, android.R.color.system_accent1_600};
        for (int i = 0; i < swatches.length; i++) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(getContext().getColor(colors[i]));
            root.findViewById(swatches[i]).setBackground(circle);
        }
        updatePreviewVisibility(root);
    }

    private void addPreview(View root, int cardId, int layout) {
        ViewGroup trailing = root.findViewById(cardId).findViewById(
                R.id.singularity_card_trailing);
        if (trailing.getChildCount() == 0) {
            LayoutInflater.from(getContext()).inflate(layout, trailing, true);
        }
    }

    private void updatePreviewVisibility(View root) {
        if (root.getWidth() == 0) {
            return;
        }
        float widthDp = root.getWidth() / getContext().getResources()
                .getDisplayMetrics().density;
        boolean show = widthDp >= 360 && getContext().getResources()
                .getConfiguration().fontScale <= 1.3f;
        for (int card : new int[] {R.id.singularity_monet, R.id.singularity_themes,
                R.id.singularity_aod}) {
            root.findViewById(card).findViewById(R.id.singularity_card_trailing)
                    .setVisibility(show ? View.VISIBLE : View.GONE);
        }
        root.findViewById(R.id.singularity_themes).findViewById(
                R.id.singularity_card_chevron).setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void bind(View root, int id, int title, int summary, int icon, String key) {
        View card = root.findViewById(id);
        ((TextView) card.findViewById(R.id.singularity_card_title)).setText(title);
        TextView subtitle = card.findViewById(R.id.singularity_card_summary);
        if (subtitle != null) {
            subtitle.setVisibility(summary == 0 ? View.GONE : View.VISIBLE);
            if (summary != 0) {
                subtitle.setText(summary);
            }
        }
        ((ImageView) card.findViewById(R.id.singularity_card_icon)).setImageResource(icon);
        navigate(card, key, title);
        if (summary != 0) {
            card.setContentDescription(getContext().getString(title) + ", "
                    + getContext().getString(summary));
        }
    }

    private void navigate(View view, String key, int title) {
        view.setContentDescription(getContext().getString(title));
        view.setOnClickListener(v -> {
            if (mNavigationListener != null) {
                mNavigationListener.onNavigate(key);
            }
        });
    }
}
