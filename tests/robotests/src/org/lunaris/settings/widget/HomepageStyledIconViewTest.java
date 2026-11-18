/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.widget.FrameLayout;

import com.android.settings.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w393dp-h873dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomepageStyledIconViewTest {
    private SharedPreferences mPreferences;
    private ActivityController<Activity> mActivity;

    @Before
    public void setUp() {
        mPreferences = RuntimeEnvironment.getApplication().getSharedPreferences(
                "settings_homepage_layout", Context.MODE_PRIVATE);
        mPreferences.edit().clear().commit();
    }

    @After
    public void tearDown() {
        if (mActivity != null) mActivity.pause().stop().destroy();
        mPreferences.edit().clear().commit();
    }

    @Test
    public void defaultStylesFollowThemeInLightAndDark() {
        for (boolean night : new boolean[] {false, true}) {
            RuntimeEnvironment.setQualifiers("w393dp-h873dp-"
                    + (night ? "night" : "notnight") + "-mdpi");
            Context context = RuntimeEnvironment.getApplication();
            int primary = context.getColor(R.color.singularity_accent);
            for (String style : new String[] {"ACCENT", "PLAIN", "FILLED"}) {
                save(style, "CIRCLE", null);
                HomepageStyledIconView view = new HomepageStyledIconView(context);
                int background = backgroundPixel(view);
                if (style.equals("PLAIN")) {
                    assertEquals(0, Color.alpha(background));
                    assertEquals(primary, tint(view));
                } else if (style.equals("FILLED")) {
                    assertEquals(primary, background);
                    assertEquals(context.getColor(R.color.singularity_on_accent), tint(view));
                } else {
                    assertEquals(128, Color.alpha(background));
                    assertRgbClose(primary, background);
                    assertEquals(context.getColor(R.color.singularity_text_primary), tint(view));
                }
            }
        }
    }

    @Test
    public void customColorsMatchHomepageOpacityAndContrast() {
        for (int color : new int[] {Color.WHITE, Color.YELLOW, Color.BLACK, Color.BLUE}) {
            save("FILLED", "CIRCLE", color);
            HomepageStyledIconView view = new HomepageStyledIconView(
                    RuntimeEnvironment.getApplication());
            assertEquals(color, backgroundPixel(view));
            assertEquals(color == Color.WHITE || color == Color.YELLOW
                    ? Color.BLACK : Color.WHITE, tint(view));
        }
        save("FILLED", "CIRCLE", 0x12336699);
        HomepageStyledIconView opaque = new HomepageStyledIconView(
                RuntimeEnvironment.getApplication());
        assertEquals(0xff336699, backgroundPixel(opaque));
        save("ACCENT", "CIRCLE", Color.RED);
        HomepageStyledIconView accent = new HomepageStyledIconView(
                RuntimeEnvironment.getApplication());
        assertEquals(0x80ff0000, backgroundPixel(accent));
        save("PLAIN", "CIRCLE", Color.RED);
        HomepageStyledIconView plain = new HomepageStyledIconView(
                RuntimeEnvironment.getApplication());
        assertEquals(Color.RED, tint(plain));
        assertEquals(0, backgroundPixel(plain));
    }

    @Test
    public void allHomepageShapesRenderDistinctCenteredSilhouettes() {
        Set<Integer> silhouettes = new HashSet<>();
        for (String shape : new String[] {"CIRCLE", "ARROW", "PILL", "TRIANGLE", "GEM",
                "COOKIE_4", "COOKIE_6", "CLOVER_4"}) {
            save("FILLED", shape, Color.RED);
            HomepageStyledIconView view = new HomepageStyledIconView(
                    RuntimeEnvironment.getApplication());
            Bitmap bitmap = renderBackground(view);
            try {
                int[] pixels = new int[64 * 64];
                bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64);
                assertTrue(shape + " was not rendered distinctly",
                        silhouettes.add(Arrays.hashCode(pixels)));
                assertEquals(shape + " has a transparent centre", Color.RED,
                        bitmap.getPixel(32, 32));
                assertEquals(shape + " was not clipped at its corner", 0,
                        Color.alpha(bitmap.getPixel(0, 0)));
            } finally {
                bitmap.recycle();
            }
        }
        assertEquals(8, silhouettes.size());
    }

    @Test
    public void attachedIconsUpdateResetAndReloadWithoutReplacingTheGlyph() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().visible();
        FrameLayout container = new FrameLayout(mActivity.get());
        mActivity.get().setContentView(container);
        HomepageStyledIconView view = new HomepageStyledIconView(mActivity.get());
        Drawable glyph = new ColorDrawable(Color.WHITE);
        view.setImageDrawable(glyph);
        container.addView(view, new FrameLayout.LayoutParams(40, 40));
        shadowOf(Looper.getMainLooper()).idle();
        save("FILLED", "COOKIE_6", Color.RED);
        assertEquals(Color.RED, backgroundPixel(view));
        assertEquals(Color.WHITE, tint(view));
        assertSame(glyph, view.getDrawable());

        mPreferences.edit().clear().commit();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(mActivity.get().getColor(R.color.singularity_text_primary), tint(view));
        assertEquals(128, Color.alpha(backgroundPixel(view)));
        container.removeView(view);
        save("PLAIN", "GEM", Color.BLUE);
        container.addView(view);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Color.BLUE, tint(view));
        assertEquals(0, backgroundPixel(view));
        assertSame(glyph, view.getDrawable());
        assertEquals(40, view.getLayoutParams().width);
        assertEquals(40, view.getLayoutParams().height);
    }

    @Test
    public void invalidSavedDataFallsBackToHomepageDefaults() {
        for (String raw : new String[] {"{", "[]", "{}",
                "{\"iconStyle\":\"UNKNOWN\",\"iconShape\":\"UNKNOWN\",\"iconColor\":\"red\"}"}) {
            mPreferences.edit().putString("layout", raw).commit();
            assertDefaults(new HomepageStyledIconView(RuntimeEnvironment.getApplication()));
        }
        mPreferences.edit().putInt("layout", 1).commit();
        assertDefaults(new HomepageStyledIconView(RuntimeEnvironment.getApplication()));
    }

    private void assertDefaults(HomepageStyledIconView view) {
        assertEquals(view.getContext().getColor(R.color.singularity_text_primary), tint(view));
        int pixel = backgroundPixel(view);
        assertEquals(128, Color.alpha(pixel));
        assertRgbClose(view.getContext().getColor(R.color.singularity_accent), pixel);
    }

    private void save(String style, String shape, Integer color) {
        mPreferences.edit().putString("layout", "{\"iconStyle\":\"" + style
                + "\",\"iconShape\":\"" + shape + "\",\"iconColor\":" + color + "}").commit();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static int tint(HomepageStyledIconView view) {
        return view.getImageTintList().getDefaultColor();
    }

    private static int backgroundPixel(HomepageStyledIconView view) {
        Bitmap bitmap = renderBackground(view);
        try {
            return bitmap.getPixel(32, 32);
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap renderBackground(HomepageStyledIconView view) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        view.getBackground().setBounds(0, 0, 64, 64);
        view.getBackground().draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void assertRgbClose(int expected, int actual) {
        assertEquals(Color.red(expected), Color.red(actual), 1);
        assertEquals(Color.green(expected), Color.green(actual), 1);
        assertEquals(Color.blue(expected), Color.blue(actual), 1);
    }
}
