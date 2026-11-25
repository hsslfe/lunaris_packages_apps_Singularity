/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.compose.material3.MaterialShapes;
import androidx.graphics.shapes.Cubic;
import androidx.graphics.shapes.RoundedPolygon;

import com.android.settings.R;

import org.json.JSONException;
import org.json.JSONObject;

/** Keeps its existing glyph while following the homepage's global icon appearance. */
public class HomepageStyledIconView extends ImageView {
    // These are the store and fields owned by homepage/HomepageLayoutStore.
    private static final String PREFS_NAME = "settings_homepage_layout";
    private static final String KEY_LAYOUT = "layout";

    private final SharedPreferences mPreferences;
    private final IconBackground mIconBackground = new IconBackground();
    private Integer mTint;
    private final SharedPreferences.OnSharedPreferenceChangeListener mListener =
            (preferences, key) -> {
                if (key == null || KEY_LAYOUT.equals(key)) refreshAppearance();
            };

    public HomepageStyledIconView(Context context) {
        this(context, null);
    }

    public HomepageStyledIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPreferences = context.getApplicationContext().getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        setBackground(mIconBackground);
        refreshAppearance();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mPreferences.registerOnSharedPreferenceChangeListener(mListener);
        refreshAppearance();
    }

    @Override
    protected void onDetachedFromWindow() {
        mPreferences.unregisterOnSharedPreferenceChangeListener(mListener);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        refreshAppearance();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        mIconBackground.setBounds(0, 0, width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int saveCount = canvas.save();
        // HomepageIcon clips both its background and its glyph to the chosen shape.
        canvas.clipPath(mIconBackground.path);
        super.onDraw(canvas);
        canvas.restoreToCount(saveCount);
    }

    private void refreshAppearance() {
        JSONObject layout = null;
        try {
            String raw = mPreferences.getString(KEY_LAYOUT, null);
            if (raw != null && !raw.isEmpty()) layout = new JSONObject(raw);
        } catch (JSONException | ClassCastException ignored) {
            // Match HomepageLayoutStore's defaults for missing or invalid data.
        }
        String style = layout == null ? "ACCENT" : layout.optString("iconStyle", "ACCENT");
        String shape = layout == null ? "CIRCLE" : layout.optString("iconShape", "CIRCLE");
        Object storedColor = layout == null ? null : layout.opt("iconColor");
        boolean customColor = storedColor instanceof Number;
        int accent = customColor ? ((Number) storedColor).intValue() | 0xFF000000
                : getContext().getColor(R.color.singularity_accent);
        int background;
        int tint;
        switch (style) {
            case "PLAIN":
                background = Color.TRANSPARENT;
                tint = accent;
                break;
            case "FILLED":
                background = accent;
                tint = customColor ? (luminance(accent) > 0.5 ? Color.BLACK : Color.WHITE)
                        : getContext().getColor(R.color.singularity_on_accent);
                break;
            default:
                background = (accent & 0x00FFFFFF) | 0x80000000;
                tint = getContext().getColor(R.color.singularity_text_primary);
                break;
        }
        mIconBackground.update(shape, background);
        if (mTint == null || mTint != tint) {
            mTint = tint;
            setImageTintMode(PorterDuff.Mode.SRC_IN);
            setImageTintList(ColorStateList.valueOf(tint));
        }
    }

    private static double luminance(int color) {
        return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color))
                + 0.0722 * linear(Color.blue(color));
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static final class IconBackground extends Drawable {
        final Path path = new Path();
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix mMatrix = new Matrix();
        private final RectF mPathBounds = new RectF();
        private String mShape = "CIRCLE";
        private int mColor;
        private int mAlpha = 255;

        void update(String shape, int color) {
            if (!mShape.equals(shape)) {
                mShape = shape;
                rebuildPath();
            }
            mColor = color;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            rebuildPath();
        }

        private void rebuildPath() {
            path.rewind();
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            RoundedPolygon polygon;
            switch (mShape) {
                case "ARROW": polygon = MaterialShapes.Companion.getArrow(); break;
                case "PILL": polygon = MaterialShapes.Companion.getPill(); break;
                case "TRIANGLE": polygon = MaterialShapes.Companion.getTriangle(); break;
                case "GEM": polygon = MaterialShapes.Companion.getGem(); break;
                case "COOKIE_4": polygon = MaterialShapes.Companion.getCookie4Sided(); break;
                case "COOKIE_6": polygon = MaterialShapes.Companion.getCookie6Sided(); break;
                case "CLOVER_4": polygon = MaterialShapes.Companion.getClover4Leaf(); break;
                default:
                    path.addOval(new RectF(bounds), Path.Direction.CW);
                    return;
            }
            // The same normalized Material polygons, scale and bounds-centering as
            // RoundedPolygon.toShape() used by the Compose homepage.
            boolean first = true;
            for (Cubic cubic : polygon.getCubics()) {
                if (first) {
                    path.moveTo(cubic.getAnchor0X(), cubic.getAnchor0Y());
                    first = false;
                }
                path.cubicTo(cubic.getControl0X(), cubic.getControl0Y(),
                        cubic.getControl1X(), cubic.getControl1Y(),
                        cubic.getAnchor1X(), cubic.getAnchor1Y());
            }
            path.close();
            mMatrix.setScale(bounds.width(), bounds.height());
            path.transform(mMatrix);
            path.computeBounds(mPathBounds, true);
            path.offset(bounds.exactCenterX() - mPathBounds.centerX(),
                    bounds.exactCenterY() - mPathBounds.centerY());
        }

        @Override
        public void draw(Canvas canvas) {
            mPaint.setColor(mColor);
            mPaint.setAlpha(Color.alpha(mColor) * mAlpha / 255);
            canvas.drawPath(path, mPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            mPaint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
