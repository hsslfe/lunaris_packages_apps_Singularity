/*
 * SPDX-FileCopyrightText: MistOS
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.themes

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import org.lunaris.settings.preferences.SwitchThumbIconPickerPreference
import org.lunaris.settings.utils.SwitchThumbIconManager

/** Picks the icon shown inside switches while they are ON, system wide. */
class SwitchIconSettings : SettingsPreferenceFragment() {

    private var picker: SwitchThumbIconPickerPreference? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.switch_icon_settings)

        picker = findPreference<SwitchThumbIconPickerPreference>(KEY_PICKER)?.also {
            it.onUploadRequested = ::pickImage
        }
    }

    private fun pickImage() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.switch_icon_needs_gallery, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != Activity.RESULT_OK || uri == null) {
            return
        }
        val activity = activity ?: return

        // The icon is circular, so start with a square crop.
        SwitchIconCropDialog.show(activity, uri, SwitchIconCropDialog.INITIAL_ASPECT_SQUARE,
            object : SwitchIconCropDialog.Callback {
                override fun onCropped(bitmap: Bitmap) = saveCustomIcon(bitmap)

                override fun onFailed() {
                    if (isAdded) {
                        Toast.makeText(context, R.string.switch_icon_failed, Toast.LENGTH_LONG).show()
                    }
                }
            })
    }

    private fun saveCustomIcon(bitmap: Bitmap) {
        val appContext = context?.applicationContext
        if (appContext == null) {
            bitmap.recycle()
            return
        }
        SwitchThumbIconManager.executeChange {
            val saved = try {
                runCatching {
                    SwitchThumbIconManager.saveCustomAndApply(appContext, bitmap)
                }.getOrDefault(false)
            } finally {
                bitmap.recycle()
            }
            mainHandler.post {
                if (!isAdded) return@post
                Toast.makeText(
                    appContext,
                    if (saved) R.string.switch_icon_custom_set else R.string.switch_icon_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                picker?.refresh()
            }
        }
    }

    override fun onDestroy() {
        picker?.onUploadRequested = null
        picker = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun getMetricsCategory(): Int = MetricsEvent.LUNARIS

    private companion object {
        const val KEY_PICKER = "switch_icon_picker"
        const val REQUEST_PICK_IMAGE = 20002
    }
}
