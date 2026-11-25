/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.UserHandle;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w393dp-h873dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperPreviewLoadingTest {
    private ActivityController<Activity> mActivity;
    private FrameLayout mContainer;

    @Before
    public void setUp() {
        WallpaperPreviewCache.clear();
        mActivity = Robolectric.buildActivity(Activity.class).setup().visible();
        mContainer = new FrameLayout(mActivity.get());
        mActivity.get().setContentView(mContainer);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @After
    public void tearDown() {
        mActivity.pause().stop().destroy();
        WallpaperPreviewCache.clear();
    }

    @Test
    public void duplicateCallbacksDoNotRestartThePendingBlur() {
        Preview preview = attachPreview();
        preview.showWindow();
        preview.showWindow();
        assertEquals(1, preview.executor.submitted);
        assertNull(preview.getDrawable());
        preview.executor.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, preview.loads);
        assertNotNull(preview.getDrawable());
        preview.showWindow();
        assertEquals(1, preview.executor.submitted);
    }

    @Test
    public void reopenedViewRestoresFinishedBlurBeforeAnyWorkerRuns() {
        Preview first = attachPreview();
        first.executor.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        Bitmap ready = ((BitmapDrawable) first.getDrawable()).getBitmap();
        mContainer.removeView(first);
        Preview reopened = attachPreview();
        assertSame(ready, ((BitmapDrawable) reopened.getDrawable()).getBitmap());
        assertEquals(0, reopened.executor.submitted);
        assertEquals(0, reopened.loads);
    }

    @Test
    public void replacingViewBeforeUiCallbackStillReusesCompletedImage() {
        Preview first = attachPreview();
        first.executor.runAll();
        // Pixels are ready, but the main-thread callback has not run yet.
        assertNull(first.getDrawable());
        mContainer.removeView(first);
        Preview replacement = attachPreview();
        assertNotNull(replacement.getDrawable());
        assertEquals(0, replacement.executor.submitted);
    }

    @Test
    public void changedWallpaperKeepsReadyBlurUntilNewBlurIsComplete() {
        Preview preview = attachPreview();
        preview.executor.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        Drawable previous = preview.getDrawable();
        Bitmap oldBitmap = ((BitmapDrawable) previous).getBitmap();
        preview.lockId++;
        preview.color = Color.BLUE;
        preview.showWindow();
        preview.showWindow();
        assertSame(previous, preview.getDrawable());
        assertEquals(2, preview.executor.submitted);
        preview.executor.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        Bitmap updated = ((BitmapDrawable) preview.getDrawable()).getBitmap();
        assertNotSame(oldBitmap, updated);
        assertEquals(Color.BLUE, updated.getPixel(0, 0));
    }

    @Test
    public void cacheRejectsOtherUsersVersionsSizesAndUnknownVersions() {
        UserHandle user = android.os.Process.myUserHandle();
        WallpaperPreviewCache.Key key = new WallpaperPreviewCache.Key(user, 7, 9, 320, 88, 1f);
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            WallpaperPreviewCache.put(key, bitmap);
            assertSame(bitmap, WallpaperPreviewCache.get(
                    new WallpaperPreviewCache.Key(user, 7, 9, 320, 88, 1f)));
            assertNull(WallpaperPreviewCache.get(new WallpaperPreviewCache.Key(
                    UserHandle.getUserHandleForUid(android.os.Process.myUid() + 100000), 7, 9, 320, 88, 1f)));
            assertNull(WallpaperPreviewCache.get(new WallpaperPreviewCache.Key(
                    user, 8, 9, 320, 88, 1f)));
            assertNull(WallpaperPreviewCache.get(new WallpaperPreviewCache.Key(
                    user, 7, 10, 320, 88, 1f)));
            assertNull(WallpaperPreviewCache.get(new WallpaperPreviewCache.Key(
                    user, 7, 9, 600, 88, 1f)));
            assertNull(WallpaperPreviewCache.get(new WallpaperPreviewCache.Key(
                    user, 7, 9, 320, 88, 2f)));
            WallpaperPreviewCache.Key unknown = new WallpaperPreviewCache.Key(
                    user, 0, 0, 320, 88, 1f);
            WallpaperPreviewCache.put(unknown, bitmap);
            assertNull(WallpaperPreviewCache.get(unknown));
            for (int version = 10; version < 15; version++) {
                WallpaperPreviewCache.put(new WallpaperPreviewCache.Key(
                        user, version, 9, 320, 88, 1f), bitmap);
            }
            assertNull(WallpaperPreviewCache.get(key));
            assertFalse(bitmap.isRecycled());
        } finally {
            WallpaperPreviewCache.clear();
            bitmap.recycle();
        }
    }

    private Preview attachPreview() {
        Preview preview = new Preview(mActivity.get());
        mContainer.addView(preview, new FrameLayout.LayoutParams(320, 88));
        preview.layout(0, 0, 320, 88);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(preview.isAttachedToWindow());
        return preview;
    }

    private static final class Preview extends LockscreenWallpaperPreviewView {
        final QueuedExecutor executor = new QueuedExecutor();
        int lockId = 7;
        int color = Color.RED;
        int loads;

        Preview(Context context) {
            super(context, null);
        }

        @Override
        protected ExecutorService createExecutor() {
            return executor;
        }

        @Override
        protected int getWallpaperId(int which) {
            return which == WallpaperManager.FLAG_LOCK ? lockId : 9;
        }

        @Override
        protected Drawable loadWallpaper(int which) {
            loads++;
            return new ColorDrawable(color);
        }

        void showWindow() {
            onWindowVisibilityChanged(VISIBLE);
        }
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean stopped;
        int submitted;

        @Override
        public void execute(Runnable task) {
            if (stopped) throw new RejectedExecutionException();
            tasks.add(task);
            submitted++;
        }

        void runAll() {
            while (!tasks.isEmpty()) tasks.remove().run();
        }

        @Override public void shutdown() { stopped = true; }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped && tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
        @Override public List<Runnable> shutdownNow() {
            stopped = true;
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            return pending;
        }
    }
}
