/*
 * SPDX-FileCopyrightText: MistOS
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.util.AtomicFile
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import com.android.settings.R
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Stores the icon shown inside switches while they are ON.
 *
 * The chosen icon is written as a small PNG to /sdcard/Lunaris/SwitchThumbIcon and its path is kept in
 * secure settings together with an enabled flag. SettingsLib (SwitchThumbIconDrawable) watches those
 * settings in every process, so the icon survives a reboot or a SystemUI restart and updates live.
 * OFF switches are never affected: with no icon selected the stock check mark is used.
 *
 * All functions that touch disk or settings must be called off the main thread.
 */
object SwitchThumbIconManager {

    private const val TAG = "SwitchThumbIconManager"

    // The first two are read by SettingsLib, keep them in sync with SwitchThumbIconStore.
    const val SETTING_ENABLED = "lunaris_switch_thumb_icon_enabled"
    const val SETTING_PATH = "lunaris_switch_thumb_icon_path"
    private const val SETTING_CHOICE = "lunaris_switch_thumb_icon_choice"

    const val CHOICE_DEFAULT = "default"
    const val CHOICE_CUSTOM = "custom"

    private const val ICON_DIR = "Lunaris/SwitchThumbIcon"
    private const val CUSTOM_FILE = "custom.png"
    private const val ACTIVE_PREFIX = "active_"
    private const val ICON_SIZE_PX = 128
    private val changeExecutor = Executors.newSingleThreadExecutor()

    /** Serialize uploads and selections so their file/settings updates cannot overlap. */
    fun executeChange(action: () -> Unit) {
        changeExecutor.execute(action)
    }

    enum class Preset(val key: String, @StringRes val label: Int, @DrawableRes val drawable: Int) {
        BAT("bat", R.string.switch_icon_bat, R.drawable.switch_icon_bat),
        SHIELD("shield", R.string.switch_icon_shield, R.drawable.switch_icon_shield),
        POKEBALL("pokeball", R.string.switch_icon_pokeball, R.drawable.switch_icon_pokeball),
        SPIDER("spider", R.string.switch_icon_spider, R.drawable.switch_icon_spider),
        SHARINGAN("sharingan", R.string.switch_icon_sharingan, R.drawable.switch_icon_sharingan);

        companion object {
            fun fromKey(key: String?): Preset? = entries.firstOrNull { it.key == key }
        }
    }

    /** The saved choice: [CHOICE_DEFAULT], [CHOICE_CUSTOM] or a [Preset] key. */
    fun getChoice(context: Context): String {
        val cr = context.contentResolver
        if (Settings.Secure.getInt(cr, SETTING_ENABLED, 0) == 0) {
            return CHOICE_DEFAULT
        }
        val choice = Settings.Secure.getString(cr, SETTING_CHOICE)
        return choice?.takeIf { it == CHOICE_CUSTOM || Preset.fromKey(it) != null } ?: CHOICE_DEFAULT
    }

    fun getChoiceLabel(context: Context): String {
        val choice = getChoice(context)
        return when {
            choice == CHOICE_CUSTOM -> context.getString(R.string.switch_icon_custom)
            Preset.fromKey(choice) != null ->
                context.getString(Preset.fromKey(choice)!!.label)
            else -> context.getString(R.string.switch_icon_default)
        }
    }

    @WorkerThread
    fun hasCustom(): Boolean = File(iconDir(), CUSTOM_FILE).isFile

    @WorkerThread
    fun loadCustom(): Bitmap? =
        try {
            decodeIcon(ImageDecoder.createSource(File(iconDir(), CUSTOM_FILE)), ICON_SIZE_PX)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read custom switch icon", e)
            null
        }

    /** Decodes a bundled icon at roughly [sizePx], without loading the full source image. */
    @WorkerThread
    fun loadPreset(context: Context, preset: Preset, sizePx: Int = ICON_SIZE_PX): Bitmap? =
        try {
            decodeIcon(ImageDecoder.createSource(context.resources, preset.drawable), sizePx)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to decode preset ${preset.key}", e)
            null
        }

    private fun decodeIcon(source: ImageDecoder.Source, sizePx: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > sizePx) {
                val ratio = sizePx.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * ratio).toInt().coerceAtLeast(1),
                    (info.size.height * ratio).toInt().coerceAtLeast(1),
                )
            }
        }

    /** Back to the stock icon. Keeps the uploaded custom icon so it can be picked again. */
    @WorkerThread
    @Synchronized
    fun applyDefault(context: Context): Boolean {
        if (!persistChoice(context, CHOICE_DEFAULT, "", enabled = false)) return false
        deleteActiveCopies(keep = null)
        return true
    }

    @WorkerThread
    @Synchronized
    fun applyPreset(context: Context, preset: Preset): Boolean {
        val bitmap = loadPreset(context, preset) ?: return false
        return try {
            activate(context, bitmap, preset.key)
        } finally {
            bitmap.recycle()
        }
    }

    @WorkerThread
    @Synchronized
    fun applyCustom(context: Context): Boolean {
        val bitmap = loadCustom() ?: return false
        return try {
            activate(context, bitmap, CHOICE_CUSTOM)
        } finally {
            bitmap.recycle()
        }
    }

    /** Saves a user supplied image as the custom icon and makes it the active one. */
    @WorkerThread
    @Synchronized
    fun saveCustomAndApply(context: Context, source: Bitmap): Boolean {
        val icon = toSquareIcon(source)
        return try {
            val dir = iconDir()
            if (!ensureDirectory(dir) || !writePng(icon, File(dir, CUSTOM_FILE))) return false
            activate(context, icon, CHOICE_CUSTOM)
        } finally {
            icon.recycle()
        }
    }

    private fun activate(context: Context, icon: Bitmap, choice: String): Boolean {
        val dir = iconDir()
        if (!ensureDirectory(dir)) return false
        val target = try {
            File.createTempFile(ACTIVE_PREFIX, ".png", dir)
        } catch (e: IOException) {
            Log.e(TAG, "Unable to create switch icon file", e)
            return false
        }
        val square = toSquareIcon(icon)
        val written = try {
            writePng(square, target)
        } finally {
            square.recycle()
        }
        if (!written) {
            target.delete()
            return false
        }
        if (!persistChoice(context, choice, target.absolutePath, enabled = true)) return false
        deleteActiveCopies(keep = target)
        return true
    }

    private fun ensureDirectory(dir: File): Boolean {
        return try {
            dir.isDirectory || dir.mkdirs()
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to create switch icon directory", e)
            false
        }
    }

    private fun persistChoice(context: Context, choice: String, path: String, enabled: Boolean): Boolean {
        val cr = context.contentResolver
        val oldEnabled = Settings.Secure.getInt(cr, SETTING_ENABLED, 0)
        val oldPath = Settings.Secure.getString(cr, SETTING_PATH)
        val oldChoice = Settings.Secure.getString(cr, SETTING_CHOICE)
        return try {
            // Publish only a completed PNG; disable first when restoring the default.
            if (!enabled) check(Settings.Secure.putInt(cr, SETTING_ENABLED, 0))
            check(Settings.Secure.putString(cr, SETTING_CHOICE, choice))
            check(Settings.Secure.putString(cr, SETTING_PATH, path))
            if (enabled) check(Settings.Secure.putInt(cr, SETTING_ENABLED, 1))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to apply switch icon settings", e)
            runCatching {
                Settings.Secure.putString(cr, SETTING_CHOICE, oldChoice)
                Settings.Secure.putString(cr, SETTING_PATH, oldPath)
                Settings.Secure.putInt(cr, SETTING_ENABLED, oldEnabled)
            }
            false
        }
    }

    /** Center crops to a square and scales to [ICON_SIZE_PX], keeping transparency. */
    private fun toSquareIcon(source: Bitmap): Bitmap {
        val side = min(source.width, source.height)
        val srcRect = RectF(
            (source.width - side) / 2f,
            (source.height - side) / 2f,
            (source.width + side) / 2f,
            (source.height + side) / 2f,
        )
        val out = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(out).drawBitmap(
            source,
            android.graphics.Rect(
                srcRect.left.toInt(), srcRect.top.toInt(), srcRect.right.toInt(), srcRect.bottom.toInt(),
            ),
            RectF(0f, 0f, ICON_SIZE_PX.toFloat(), ICON_SIZE_PX.toFloat()),
            paint,
        )
        return out
    }

    // Flush and rename atomically, including when replacing the saved custom icon.
    private fun writePng(bitmap: Bitmap, target: File): Boolean {
        val file = AtomicFile(target)
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = file.startWrite()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw IOException("PNG compression failed")
            }
            file.finishWrite(stream)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save switch icon", e)
            file.failWrite(stream)
            false
        }
    }

    private fun deleteActiveCopies(keep: File?) {
        val files = iconDir().listFiles { _, name -> name.startsWith(ACTIVE_PREFIX) } ?: return
        for (file in files) {
            if (file != keep && !file.delete()) {
                Log.w(TAG, "Failed to delete old switch icon ${file.name}")
            }
        }
    }

    private fun iconDir() = File(Environment.getExternalStorageDirectory(), ICON_DIR)
}
