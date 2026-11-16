/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.concurrent.CancellationException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperPreviewBlurTest {
    @Test
    public void constantColoursStayOpaqueAndUntintedIncludingAtEdges() {
        int color = Color.rgb(40, 100, 180);
        ColorDrawable source = new ColorDrawable(color);
        Rect originalBounds = new Rect(7, 11, 23, 35);
        source.setBounds(originalBounds);
        Bitmap result = WallpaperPreviewBlur.create(source, 240, 88, 1f);
        try {
            for (int y = 0; y < result.getHeight(); y++) {
                for (int x = 0; x < result.getWidth(); x++) {
                    assertEquals(color, result.getPixel(x, y));
                }
            }
            assertEquals(originalBounds, source.getBounds());
        } finally {
            result.recycle();
        }
    }

    @Test
    public void softensActualPixelsInBothDirectionsWithoutChangingTheSource() {
        for (boolean verticalEdge : new boolean[] {false, true}) {
            Bitmap source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 96; y++) {
                for (int x = 0; x < 96; x++) {
                    source.setPixel(x, y, (verticalEdge ? x : y) < 48 ? Color.RED : Color.BLUE);
                }
            }
            Bitmap result = WallpaperPreviewBlur.create(new BitmapDrawable(
                    RuntimeEnvironment.getApplication().getResources(), source), 96, 96, 1f);
            try {
                int transition = result.getPixel(47, 47);
                assertTrue(Color.red(transition) > 0 && Color.red(transition) < 255);
                assertTrue(Color.blue(transition) > 0 && Color.blue(transition) < 255);
                assertEquals(0, Color.green(transition));
                assertEquals(255, Color.alpha(transition));
                assertEquals(Color.RED, result.getPixel(0, 0));
                assertEquals(Color.BLUE, result.getPixel(95, 95));
                assertEquals(Color.RED, source.getPixel(47, 47));
                assertFalse(source.isRecycled());
            } finally {
                result.recycle();
                source.recycle();
            }
        }
    }

    @Test
    public void previewIsBoundedAndRetainsTheCardAspectRatio() {
        Bitmap result = WallpaperPreviewBlur.create(new ColorDrawable(Color.RED), 1440, 336, 3f);
        try {
            assertEquals(512, result.getWidth());
            assertEquals(119, result.getHeight());
            assertEquals(Bitmap.DENSITY_NONE, result.getDensity());
        } finally {
            result.recycle();
        }
    }

    @Test
    public void cancellationStopsWorkWithoutClearingTheInterrupt() {
        Thread.currentThread().interrupt();
        try {
            WallpaperPreviewBlur.create(new ColorDrawable(Color.RED), 240, 88, 1f);
            fail("Cancelled work must not publish an image");
        } catch (CancellationException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
