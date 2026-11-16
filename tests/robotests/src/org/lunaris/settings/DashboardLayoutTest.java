/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Measures the actual dashboard XML with the unconstrained height used by RecyclerView. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w393dp-h873dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DashboardLayoutTest {
    private static final int[][] SHORTCUTS = {
            {R.id.singularity_statusbar, R.string.singularity_statusbar},
            {R.id.singularity_qs, R.string.singularity_qs},
            {R.id.singularity_buttons, R.string.buttons_title},
            {R.id.singularity_power, R.string.singularity_power},
            {R.id.singularity_notifications, R.string.singularity_notifications},
            {R.id.singularity_navigation, R.string.navigation_title},
            {R.id.singularity_misc, R.string.singularity_miscellaneous},
            {R.id.singularity_sound, R.string.sound_title}
    };

    @Test
    public void shortcutsKeepCardsAndLabelsAcrossConfigurations() {
        for (int width : new int[] {320, 360, 393, 600}) {
            for (boolean night : new boolean[] {false, true}) {
                for (float fontScale : new float[] {1f, 1.3f, 2f}) {
                    for (boolean rtl : new boolean[] {false, true}) {
                        View root = inflateDashboard(width, night, fontScale, rtl);
                        assertEquals(root.getContext().getColor(night
                                ? android.R.color.system_surface_container_dark
                                : android.R.color.system_surface_container_light),
                                ((ColorDrawable) root.getBackground()).getColor());
                        for (int[] shortcut : SHORTCUTS) {
                            assertShortcutBounds(root.findViewById(shortcut[0]));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void wallpaperIntrinsicSizeNeverStretchesTheCard() {
        Bitmap wallpaper = Bitmap.createBitmap(320, 1600, Bitmap.Config.ARGB_8888);
        try {
            for (boolean night : new boolean[] {false, true}) {
                for (float fontScale : new float[] {1f, 2f}) {
                    View root = inflateDashboard(393, night, fontScale, false);
                    ViewGroup card = root.findViewById(R.id.singularity_lockscreen);
                    int heightWithoutImage = card.getMeasuredHeight();
                    ImageView image = card.findViewById(R.id.singularity_lock_wallpaper);
                    image.setImageBitmap(wallpaper);
                    for (int mode : new int[] {View.MeasureSpec.UNSPECIFIED,
                            View.MeasureSpec.AT_MOST}) {
                        card.measure(View.MeasureSpec.makeMeasureSpec(card.getWidth(),
                                View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(2000, mode));
                        card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
                        assertEquals("Wallpaper enlarged the card", heightWithoutImage,
                                card.getHeight());
                        assertEquals("Backdrop must fill the card", card.getHeight(),
                                image.getHeight());
                    }
                    if (fontScale == 1f) {
                        assertTrue("Default lockscreen card is too tall", card.getHeight() <= 96);
                    }
                    TextClock clock = card.findViewById(R.id.singularity_lock_clock);
                    Rect bounds = new Rect(0, 0, clock.getWidth(), clock.getHeight());
                    card.offsetDescendantRectToMyCoords(clock, bounds);
                    assertTrue("Clock exceeds the compact card", bounds.bottom <= card.getHeight());
                }
            }
        } finally {
            wallpaper.recycle();
        }
    }

    @Test
    public void wallpaperIsDimmedThirtyPercentInBothThemes() {
        Bitmap wallpaper = Bitmap.createBitmap(32, 128, Bitmap.Config.ARGB_8888);
        wallpaper.eraseColor(Color.WHITE);
        try {
            for (boolean night : new boolean[] {false, true}) {
                View root = inflateDashboard(393, night, 1f, false);
                ViewGroup card = root.findViewById(R.id.singularity_lockscreen);
                ((ImageView) card.findViewById(R.id.singularity_lock_wallpaper))
                        .setImageBitmap(wallpaper);
                assertEquals(0.3f, card.findViewById(R.id.singularity_lock_dim).getAlpha(), 0.001f);
                Bitmap render = Bitmap.createBitmap(card.getWidth(), card.getHeight(),
                        Bitmap.Config.ARGB_8888);
                try {
                    card.draw(new Canvas(render));
                    assertEquals("Wallpaper escaped the rounded card", 0,
                            Color.alpha(render.getPixel(0, 0)));
                    int pixel = render.getPixel(card.getWidth() / 2, 4);
                    assertEquals(178.5, Color.red(pixel), 1.5);
                    assertEquals(Color.red(pixel), Color.green(pixel));
                    assertEquals(Color.red(pixel), Color.blue(pixel));
                } finally {
                    render.recycle();
                }
            }
        } finally {
            wallpaper.recycle();
        }
    }

    private View inflateDashboard(int width, boolean night, float fontScale, boolean rtl) {
        RuntimeEnvironment.setQualifiers((rtl ? "ldrtl" : "ldltr") + "-w" + width
                + "dp-h873dp-" + (night ? "night" : "notnight") + "-mdpi");
        Context application = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(
                application.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        Context context = new ContextThemeWrapper(
                application.createConfigurationContext(configuration),
                android.R.style.Theme_Material_Light_NoActionBar);
        View root = LayoutInflater.from(context).inflate(
                R.layout.preference_singularity_dashboard, new FrameLayout(context), false);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        for (int[] shortcut : SHORTCUTS) {
            TextView label = root.findViewById(shortcut[0]).findViewById(
                    R.id.singularity_card_title);
            label.setText(shortcut[1]);
        }
        ((TextClock) root.findViewById(R.id.singularity_lock_clock)).setText("07:48");
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, width, root.getMeasuredHeight());
        return root;
    }

    private void assertShortcutBounds(View card) {
        String name = card.getResources().getResourceEntryName(card.getId());
        assertTrue(name + " collapsed", card.getHeight() >= 60);
        TextView label = card.findViewById(R.id.singularity_card_title);
        assertTrue(name + " lost its label", label.getHeight() > 0);
        assertTrue(name + " clips its label", label.getTop() >= 0
                && label.getBottom() <= card.getHeight() - card.getPaddingBottom());
        assertTrue(name + " has no text lines", label.getLayout().getLineCount() > 0);
        for (int line = 0; line < label.getLineCount(); line++) {
            assertEquals(name + " ellipsizes text", 0, label.getLayout().getEllipsisCount(line));
            assertTrue(name + " overflows horizontally", label.getLayout().getLineMax(line)
                    <= label.getWidth() - label.getTotalPaddingLeft()
                    - label.getTotalPaddingRight() + 0.5f);
        }
        assertTrue(name + " exceeds its row", card.getBottom()
                <= ((ViewGroup) card.getParent()).getHeight());
    }
}
