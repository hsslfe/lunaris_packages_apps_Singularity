/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.utils;

import android.graphics.Bitmap;
import android.os.UserHandle;
import android.util.LruCache;

/** Small process-local cache of finished images, isolated by user and wallpaper version. */
final class WallpaperPreviewCache {
    // A blurred preview has at most 512 x 512 pixels, so four entries use at most 4 MiB.
    private static final LruCache<Key, Bitmap> PREVIEWS = new LruCache<>(4);

    private WallpaperPreviewCache() {}

    static Bitmap get(Key key) {
        if (!key.isVersioned()) return null;
        Bitmap bitmap = PREVIEWS.get(key);
        if (bitmap != null && bitmap.isRecycled()) {
            PREVIEWS.remove(key);
            return null;
        }
        return bitmap;
    }

    static void put(Key key, Bitmap bitmap) {
        if (key.isVersioned() && bitmap != null && !bitmap.isRecycled()) {
            PREVIEWS.put(key, bitmap);
        }
    }

    static void clear() {
        // Do not recycle: an attached ImageView may still be drawing an evicted image.
        PREVIEWS.evictAll();
    }

    static final class Key {
        final UserHandle user;
        final int lockId;
        final int homeId;
        final int width;
        final int height;
        final int density;

        Key(UserHandle user, int lockId, int homeId, int width, int height, float density) {
            this.user = user;
            this.lockId = lockId;
            this.homeId = homeId;
            this.width = width;
            this.height = height;
            this.density = Float.floatToIntBits(density);
        }

        boolean isVersioned() {
            return lockId > 0 || homeId > 0;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return user.equals(key.user) && lockId == key.lockId && homeId == key.homeId
                    && width == key.width && height == key.height && density == key.density;
        }

        @Override
        public int hashCode() {
            int result = user.hashCode();
            result = 31 * result + lockId;
            result = 31 * result + homeId;
            result = 31 * result + width;
            result = 31 * result + height;
            return 31 * result + density;
        }
    }
}
