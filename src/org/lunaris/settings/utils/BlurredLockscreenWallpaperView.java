/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.utils;

import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A read-only, bounded wallpaper thumbnail. Only this image layer is blurred. */
public class BlurredLockscreenWallpaperView extends ImageView {
    private static final String TAG = "SingularityWallpaper";
    private static final int MAX_PREVIEW_SIZE = 768;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final WallpaperManager mWallpaperManager;
    private ExecutorService mExecutor;
    private Future<?> mLoad;
    private int mGeneration;

    private final WallpaperManager.OnColorsChangedListener mColorsListener =
            (WallpaperColors colors, int which) -> refreshWallpaper();

    public BlurredLockscreenWallpaperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mWallpaperManager = WallpaperManager.getInstance(context);
        setScaleType(ScaleType.CENTER_CROP);
        float radius = 14f * getResources().getDisplayMetrics().density;
        setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mExecutor = Executors.newSingleThreadExecutor();
        mWallpaperManager.addOnColorsChangedListener(mColorsListener, mHandler);
        refreshWallpaper();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            refreshWallpaper();
        }
    }

    private void refreshWallpaper() {
        if (mExecutor == null || mExecutor.isShutdown()) {
            return;
        }
        final int generation = ++mGeneration;
        if (mLoad != null) {
            mLoad.cancel(true);
        }
        mLoad = mExecutor.submit(() -> {
            Drawable preview = loadWallpaper(WallpaperManager.FLAG_LOCK);
            if (preview == null) {
                preview = loadWallpaper(WallpaperManager.FLAG_SYSTEM);
            }
            final Drawable result = preview;
            mHandler.post(() -> {
                if (isAttachedToWindow() && generation == mGeneration) {
                    // Null clears any previous user's/wallpaper's image; the card
                    // background remains a readable fallback when access is denied.
                    setImageDrawable(result);
                }
            });
        });
    }

    private Drawable loadWallpaper(int which) {
        try (ParcelFileDescriptor file = mWallpaperManager.getWallpaperFile(which)) {
            if (file != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(), null, options);
                options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > MAX_PREVIEW_SIZE
                        || options.outHeight / options.inSampleSize > MAX_PREVIEW_SIZE) {
                    options.inSampleSize *= 2;
                }
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(
                        file.getFileDescriptor(), null, options);
                if (bitmap != null) {
                    return new BitmapDrawable(getResources(), bitmap);
                }
            }
            WallpaperInfo info = mWallpaperManager.getWallpaperInfo(which);
            return info == null ? null : info.loadThumbnail(getContext().getPackageManager());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Wallpaper preview unavailable for flag " + which, e);
            return null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        ++mGeneration;
        mWallpaperManager.removeOnColorsChangedListener(mColorsListener);
        if (mLoad != null) {
            mLoad.cancel(true);
            mLoad = null;
        }
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        setImageDrawable(null);
        super.onDetachedFromWindow();
    }
}
