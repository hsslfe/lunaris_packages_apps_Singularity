/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.fragments.lockscreen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.icu.text.ListFormatter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import android.widget.ListView;

import android.app.AlertDialog;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;


import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Controls which live activity types Now Bar shows on the lock screen. */
public class NowBarShowSettings extends SettingsPreferenceFragment {
    private static final String KEY_LIVE_APPS = "now_bar_live_apps";
    private static final String KEY_GLANCE = "now_bar_glance_settings";
    /**
     * The Google app's settings, where At a Glance and its Sports live when the app offers them.
     * Its At a Glance deep link (com.google.android.apps.gsa.smartspace.SETTINGS) closes at once
     * on devices the app does not treat as Pixels.
     */
    private static final Intent GLANCE_SETTINGS = new Intent().setClassName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.search.googleapp.settingsui.SettingsActivity");

    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.lunaris_now_bar_show);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
        Preference glance = findPreference(KEY_GLANCE);
        if (glance != null) {
            boolean installed;
            try {
                getContext().getPackageManager().getActivityInfo(GLANCE_SETTINGS.getComponent(), 0);
                installed = true;
            } catch (PackageManager.NameNotFoundException e) {
                installed = false;
            }
            glance.setVisible(installed);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (KEY_LIVE_APPS.equals(preference.getKey())) {
            pickApps();
            return true;
        }
        if (KEY_GLANCE.equals(preference.getKey())) {
            try {
                startActivity(GLANCE_SETTINGS);
            } catch (Exception e) {
                // Hidden when it does not resolve; a race at most.
            }
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    private void updateSummaries() {
        Preference apps = findPreference(KEY_LIVE_APPS);
        if (apps != null) {
            List<String> names = new ArrayList<>();
            for (String pkg : liveApps()) names.add(label(pkg).toString());
            names.sort(Collator.getInstance());
            apps.setSummary(names.isEmpty() ? getString(R.string.now_bar_live_apps_all)
                    : ListFormatter.getInstance().format(names));
        }
    }

    private Set<String> liveApps() {
        String csv = Settings.System.getStringForUser(getContentResolver(), KEY_LIVE_APPS,
                UserHandle.USER_CURRENT);
        Set<String> apps = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(csv)) {
            for (String pkg : csv.split(",")) {
                if (!pkg.isEmpty()) apps.add(pkg);
            }
        }
        return apps;
    }

    private CharSequence label(String pkg) {
        PackageManager pm = getContext().getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }


    /** All apps, then the apps picked, then the rest of the launchable apps. */
    private void pickApps() {
        loadApps(this::showApps);
    }

    private void loadApps(java.util.function.BiConsumer<String[], CharSequence[]> then) {
        Context context = getContext();
        if (context == null) return;
        mExecutor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<String> packages = new ArrayList<>();
            List<CharSequence> labels = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
                String pkg = info.activityInfo.packageName;
                if (seen.add(pkg)) {
                    packages.add(pkg);
                    labels.add(info.loadLabel(pm));
                }
            }
            Integer[] order = new Integer[packages.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Collator collator = Collator.getInstance();
            Arrays.sort(order, (a, b) ->
                    collator.compare(labels.get(a).toString(), labels.get(b).toString()));
            String[] sortedPackages = new String[order.length];
            CharSequence[] sortedLabels = new CharSequence[order.length];
            for (int i = 0; i < order.length; i++) {
                sortedPackages[i] = packages.get(order[i]);
                sortedLabels[i] = labels.get(order[i]);
            }
            mHandler.post(() -> then.accept(sortedPackages, sortedLabels));
        });
    }

    private void showApps(String[] packages, CharSequence[] labels) {
        if (!isAdded()) return;
        Set<String> picked = liveApps();
        List<String> rows = new ArrayList<>();
        List<CharSequence> names = new ArrayList<>();
        rows.add(null);
        names.add(getString(R.string.now_bar_live_apps_all));
        for (boolean first : new boolean[] {true, false}) {
            for (int i = 0; i < packages.length; i++) {
                if (picked.contains(packages[i]) == first) {
                    rows.add(packages[i]);
                    names.add(labels[i]);
                }
            }
        }
        boolean[] checked = new boolean[rows.size()];
        for (int i = 1; i < checked.length; i++) checked[i] = picked.contains(rows.get(i));
        checked[0] = !anyApp(checked);
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.now_bar_live_apps)
                .setMultiChoiceItems(names.toArray(new CharSequence[0]), checked,
                        (d, which, isChecked) -> {
                    checked[which] = isChecked;
                    ListView list = ((AlertDialog) d).getListView();
                    if (which == 0) {
                        // All apps is the empty list: it clears every pick and stays on.
                        for (int i = 1; i < checked.length; i++) setRow(list, checked, i, false);
                    }
                    setRow(list, checked, 0, !anyApp(checked));
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    List<String> chosen = new ArrayList<>();
                    for (int i = 1; i < checked.length; i++) {
                        if (checked[i]) chosen.add(rows.get(i));
                    }
                    putLiveApps(TextUtils.join(",", chosen));
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (!picked.isEmpty()) {
            dialog.setNeutralButton(R.string.now_bar_reset, (d, w) -> putLiveApps(""));
        }
        dialog.show();
    }

    private static boolean anyApp(boolean[] checked) {
        for (int i = 1; i < checked.length; i++) {
            if (checked[i]) return true;
        }
        return false;
    }

    /** The dialog's adapter re-checks rows from the array, so both change together. */
    private static void setRow(ListView list, boolean[] checked, int row, boolean on) {
        checked[row] = on;
        list.setItemChecked(row, on);
    }

    private void putLiveApps(String csv) {
        Settings.System.putStringForUser(getContentResolver(), KEY_LIVE_APPS, csv,
                UserHandle.USER_CURRENT);
        updateSummaries();
    }


}
