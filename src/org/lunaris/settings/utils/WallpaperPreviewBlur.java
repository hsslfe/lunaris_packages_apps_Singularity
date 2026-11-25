/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.concurrent.CancellationException;

/** Creates a cropped, opaque, softly blurred image without tinting the wallpaper. */
public final class WallpaperPreviewBlur {
    private static final int MAX_EDGE_PX = 512;
    private static final float BLUR_SIGMA_DP = 10f;

    private WallpaperPreviewBlur() {}

    /** Run on a worker. The source drawable and its bitmap remain caller-owned. */
    public static Bitmap create(Drawable source, int targetWidth, int targetHeight,
            float density) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Preview dimensions must be positive");
        }
        checkCancelled();
        float scale = Math.min(1f, (float) MAX_EDGE_PX / Math.max(targetWidth, targetHeight));
        int width = Math.max(1, Math.round(targetWidth * scale));
        int height = Math.max(1, Math.round(targetHeight * scale));
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setDensity(Bitmap.DENSITY_NONE);
        try {
            Canvas canvas = new Canvas(result);
            canvas.drawColor(Color.BLACK);
            int sourceWidth = source.getIntrinsicWidth();
            int sourceHeight = source.getIntrinsicHeight();
            Rect oldBounds = new Rect(source.getBounds());
            int saveCount = canvas.save();
            try {
                if (sourceWidth > 0 && sourceHeight > 0) {
                    float cropScale = Math.max((float) width / sourceWidth,
                            (float) height / sourceHeight);
                    canvas.translate((width - sourceWidth * cropScale) / 2f,
                            (height - sourceHeight * cropScale) / 2f);
                    canvas.scale(cropScale, cropScale);
                    source.setBounds(0, 0, sourceWidth, sourceHeight);
                } else {
                    source.setBounds(0, 0, width, height);
                }
                source.draw(canvas);
            } finally {
                source.setBounds(oldBounds);
                canvas.restoreToCount(saveCount);
            }

            int[] pixels = new int[width * height];
            int[] scratch = new int[pixels.length];
            result.getPixels(pixels, 0, width, 0, 0, width, height);
            int radius = Math.max(1, Math.min(48, Math.round(BLUR_SIGMA_DP * density * scale)));
            // Three separable box passes approximate a Gaussian in linear time.
            // Clamping samples at the image edges prevents dark/transparent fringes.
            for (int pass = 0; pass < 3; pass++) {
                blurPass(pixels, scratch, width, height, radius, true);
                blurPass(scratch, pixels, width, height, radius, false);
            }
            checkCancelled();
            result.setPixels(pixels, 0, width, 0, 0, width, height);
            return result;
        } catch (RuntimeException e) {
            result.recycle();
            throw e;
        }
    }

    private static void blurPass(int[] source, int[] destination, int width, int height,
            int radius, boolean horizontal) {
        int lineCount = horizontal ? height : width;
        int length = horizontal ? width : height;
        int step = horizontal ? 1 : width;
        int divisor = radius * 2 + 1;
        for (int line = 0; line < lineCount; line++) {
            checkCancelled();
            int start = horizontal ? line * width : line;
            int red = 0;
            int green = 0;
            int blue = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int color = source[start + clamp(offset, length) * step];
                red += Color.red(color);
                green += Color.green(color);
                blue += Color.blue(color);
            }
            for (int position = 0; position < length; position++) {
                destination[start + position * step] = Color.rgb(
                        (red + divisor / 2) / divisor,
                        (green + divisor / 2) / divisor,
                        (blue + divisor / 2) / divisor);
                int leaving = source[start + clamp(position - radius, length) * step];
                int entering = source[start + clamp(position + radius + 1, length) * step];
                red += Color.red(entering) - Color.red(leaving);
                green += Color.green(entering) - Color.green(leaving);
                blue += Color.blue(entering) - Color.blue(leaving);
            }
        }
    }

    private static int clamp(int position, int length) {
        return Math.max(0, Math.min(position, length - 1));
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Wallpaper preview cancelled");
        }
    }
}
