/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.fragments.themes

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory

import com.android.internal.logging.nano.MetricsProto

import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

import java.util.concurrent.Executors

/**
 * Which apps get the glass tab bar, and which also run their content under it. The framework
 * reads the two lists in every app's process.
 */
class LiquidGlassTabBarApps : SettingsPreferenceFragment() {

    private class App(val pkg: String, val label: String, val icon: Drawable)

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.liquid_glass_tab_bar_apps)

        val context = requireContext().applicationContext
        executor.execute {
            val apps = loadApps(context)
            activity?.runOnUiThread { if (isAdded) showApps(apps) }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun loadApps(context: Context): List<App> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { App(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun showApps(apps: List<App>) {
        val category = findPreference<PreferenceCategory>(KEY_CATEGORY) ?: return
        val entries = resources.getTextArray(R.array.liquid_glass_tab_bar_mode_entries)
        val values = resources.getTextArray(R.array.liquid_glass_tab_bar_mode_values)
        for (app in apps) {
            category.addPreference(ListPreference(requireContext()).apply {
                key = app.pkg
                title = app.label
                icon = app.icon
                dialogTitle = app.label
                this.entries = entries
                entryValues = values
                value = modeOf(app.pkg).toString()
                isPersistent = false
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setOnPreferenceChangeListener { _, newValue ->
                    val mode = newValue.toString().toIntOrNull()
                    mode != null && setMode(app.pkg, mode)
                }
            })
        }
    }

    private fun list(key: String): MutableSet<String> {
        val resolver = requireContext().contentResolver
        return Settings.Secure.getString(resolver, key)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.toMutableSet() ?: mutableSetOf()
    }

    private fun putList(key: String, list: Set<String>): Boolean =
        Settings.Secure.putString(requireContext().contentResolver, key, list.joinToString(","))


    private fun modeOf(pkg: String): Int = when (pkg) {
        in list(KEY_EXCLUDED) -> MODE_OFF
        in list(KEY_FLOAT) -> MODE_FLOAT
        else -> MODE_GLASS
    }

    private fun setMode(pkg: String, mode: Int): Boolean {
        if (mode !in MODE_OFF..MODE_FLOAT) return false
        val excluded = list(KEY_EXCLUDED)
        val floating = list(KEY_FLOAT)
        val nextExcluded = excluded.toMutableSet().apply {
            if (mode == MODE_OFF) add(pkg) else remove(pkg)
        }
        val nextFloating = floating.toMutableSet().apply {
            if (mode == MODE_FLOAT) add(pkg) else remove(pkg)
        }
        if (mode == MODE_OFF) {
            if (excluded != nextExcluded && !putList(KEY_EXCLUDED, nextExcluded)) return false
            if (floating == nextFloating || putList(KEY_FLOAT, nextFloating)) return true
            if (excluded != nextExcluded) putList(KEY_EXCLUDED, excluded)
        } else {
            if (floating != nextFloating && !putList(KEY_FLOAT, nextFloating)) return false
            if (excluded == nextExcluded || putList(KEY_EXCLUDED, nextExcluded)) return true
            if (floating != nextFloating) putList(KEY_FLOAT, floating)
        }
        return false
    }

    private companion object {
        const val KEY_CATEGORY = "liquid_glass_tab_bars_apps_category"
        const val KEY_EXCLUDED = "liquid_glass_tab_bars_excluded"
        const val KEY_FLOAT = "liquid_glass_tab_bars_float"
        const val MODE_OFF = 0
        const val MODE_GLASS = 1
        const val MODE_FLOAT = 2
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.VIEW_UNKNOWN
}
