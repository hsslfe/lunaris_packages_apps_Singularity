/*
 * Copyright (C) 2016-2025 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.settings.fragments.lockscreen;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.lunaris.OmniJawsClient;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.preferences.SecureSettingPrimarySwitchPreference;
import org.lunaris.settings.preferences.SystemSettingPrimarySwitchPreference;
import org.lunaris.settings.utils.DeviceUtils;
import org.lunaris.settings.utils.SystemUtils;

import com.android.internal.util.lunaris.VibrationUtils;

import java.util.List;

@SearchIndexable
public class LockScreen extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "LockScreen";

    private static final String LOCKSCREEN_GESTURES_CATEGORY = "lockscreen_gestures_category";
    private static final String LOCKSCREEN_INTERFACE_CATEGORY = "lockscreen_interface_category";
    private static final String KEY_RIPPLE_EFFECT = "enable_ripple_effect";
    private static final String KEY_SMARTSPACE = "lockscreen_smartspace_enabled";
    private static final String KEY_WEATHER = "lockscreen_weather_enabled";
    private static final String KEY_KG_USER_SWITCHER = "kg_user_switcher_enabled";
    private static final String LUNARIS_UDFPS_CUSTOM_CATEGORY = "lockscreen_custom_category";
    private static final String KEY_KEYGUARD_SCRIM = "keyguard_scrim_transparent";
    private static final String KEY_NOW_BAR = "now_bar";
    private static final String KEY_MUSIC_PILL = "ax_dynamic_bar_keyguard_music_pill";

    private static final String KEY_FP_SUCCESS = "fp_success_vibrate";
    private static final String KEY_FP_ERROR = "fp_error_vibrate";

    private static final String PROP_CUSTOM_UDFPS = "persist.sys.udfps.custom";

    private Preference mRippleEffect;

    private SwitchPreferenceCompat mSmartspace;
    private SwitchPreferenceCompat mWeather;
    private SwitchPreferenceCompat mKgUserSwitcher;
    private SwitchPreferenceCompat mFpSuccessVib;
    private SwitchPreferenceCompat mFpErrorVib;
    private SwitchPreferenceCompat mKeyguardScrim;
    private SwitchPreferenceCompat mMusicPill;
    private SystemSettingPrimarySwitchPreference mNowBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.lunaris_settings_lock_screen);
        final Context context = getContext();

        PreferenceCategory gestCategory = (PreferenceCategory) findPreference(LOCKSCREEN_GESTURES_CATEGORY);
        PreferenceCategory lunarisUdfpsCategory = (PreferenceCategory) findPreference(LUNARIS_UDFPS_CUSTOM_CATEGORY);

        mFpSuccessVib = findPreference(KEY_FP_SUCCESS);
        mFpErrorVib = findPreference(KEY_FP_ERROR);
        mRippleEffect = findPreference(KEY_RIPPLE_EFFECT);

        boolean hasFingerprint = DeviceUtils.hasFingerprint(context);
        if (!hasFingerprint) {
            gestCategory.removePreference(mRippleEffect);
        }

        if (lunarisUdfpsCategory != null && !SystemProperties.getBoolean(PROP_CUSTOM_UDFPS, false)) {
            getPreferenceScreen().removePreference(lunarisUdfpsCategory);
        }

        boolean hapticAvailable = DeviceUtils.hasVibrator(context);
        if (!hasFingerprint || !hapticAvailable) {
            gestCategory.removePreference(mFpSuccessVib);
            gestCategory.removePreference(mFpErrorVib);
        }

        mSmartspace = (SwitchPreferenceCompat) findPreference(KEY_SMARTSPACE);
        mSmartspace.setOnPreferenceChangeListener(this);

        mWeather = (SwitchPreferenceCompat) findPreference(KEY_WEATHER);
        mWeather.setOnPreferenceChangeListener(this);

        mKgUserSwitcher = (SwitchPreferenceCompat) findPreference(KEY_KG_USER_SWITCHER);
        mKgUserSwitcher.setOnPreferenceChangeListener(this);

        mKeyguardScrim = (SwitchPreferenceCompat) findPreference(KEY_KEYGUARD_SCRIM);
        mKeyguardScrim.setOnPreferenceChangeListener(this);

        mNowBar = findPreference(KEY_NOW_BAR);
        if (mNowBar != null) {
            mNowBar.setOnPreferenceChangeListener(this);
        }
        mMusicPill = findPreference(KEY_MUSIC_PILL);
        if (mMusicPill != null) {
            mMusicPill.setOnPreferenceChangeListener(this);
        }

        updateWeatherSettings();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mNowBar) {
            if (Boolean.TRUE.equals(newValue)) {
                // Enabling Now Bar replaces the separate lockscreen music pill.
                Settings.Secure.putIntForUser(getContentResolver(), KEY_MUSIC_PILL,
                        0, UserHandle.USER_CURRENT);
                if (mMusicPill != null) {
                    mMusicPill.setChecked(false);
                }
            }
            return true;
        } else if (preference == mMusicPill) {
            if (Boolean.TRUE.equals(newValue)) {
                // Enabling the classic music pill disables Now Bar.
                Settings.System.putIntForUser(getContentResolver(), KEY_NOW_BAR,
                        0, UserHandle.USER_CURRENT);
                if (mNowBar != null) {
                    mNowBar.loadValue();
                }
            }
            return true;
        } else if (preference == mSmartspace) {
            mSmartspace.setChecked((Boolean)newValue);
            updateWeatherSettings();
            SystemUtils.showSystemUiRestartDialog(getContext());
            return true;
        } else if (preference == mWeather) {
            mWeather.setChecked((Boolean)newValue);
            SystemUtils.showSystemUiRestartDialog(getContext());
            return true;
        } else if (preference == mKgUserSwitcher) {
            mKgUserSwitcher.setChecked((Boolean)newValue);
            SystemUtils.showSystemUiRestartDialog(getContext());
            return true;
        } else if (preference == mKeyguardScrim) {
            mKeyguardScrim.setChecked((Boolean) newValue);
            SystemUtils.showSystemUiRestartDialog(getContext());
            return true;
        }
        return false;
    }

    private void updateWeatherSettings() {
        if (mWeather == null || mSmartspace == null) return;

        boolean weatherEnabled = OmniJawsClient.get().isOmniJawsEnabled(getContext());
        mWeather.setEnabled(!mSmartspace.isChecked() && weatherEnabled);
        mWeather.setSummary(!mSmartspace.isChecked() && weatherEnabled
                ? R.string.lockscreen_weather_summary
                : R.string.lockscreen_weather_enabled_info);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mNowBar != null) {
            mNowBar.loadValue();
        }
        if (mMusicPill != null) {
            final boolean nowBarEnabled = Settings.System.getIntForUser(
                    getContentResolver(), KEY_NOW_BAR, 0, UserHandle.USER_CURRENT) != 0;
            boolean musicPillEnabled = Settings.Secure.getIntForUser(
                    getContentResolver(), KEY_MUSIC_PILL, 0, UserHandle.USER_CURRENT) != 0;
            if (nowBarEnabled && musicPillEnabled) {
                // Resolve conflicting legacy states without enabling either feature.
                Settings.Secure.putIntForUser(getContentResolver(), KEY_MUSIC_PILL,
                        0, UserHandle.USER_CURRENT);
                musicPillEnabled = false;
            }
            mMusicPill.setChecked(musicPillEnabled);
        }
        // Keep primary switches in sync after returning from detail pages.
        for (String key : new String[] {
                "ls_media_art_enabled", "nowplaying_enabled" }) {
            SystemSettingPrimarySwitchPreference preference = findPreference(key);
            if (preference != null) {
                preference.loadValue();
            }
        }
        for (String key : new String[] {
                "lockscreen_pulse_enabled", "lockscreen_smartspace_extras_enabled" }) {
            SecureSettingPrimarySwitchPreference preference = findPreference(key);
            if (preference != null) {
                preference.loadValue();
            }
        }
        updateWeatherSettings();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lunaris_settings_lock_screen) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    boolean hasFingerprint = DeviceUtils.hasFingerprint(context);
                    if (!hasFingerprint) {
                        keys.add(KEY_RIPPLE_EFFECT);
                    }
                    if (!SystemProperties.getBoolean(PROP_CUSTOM_UDFPS, false)) {
                        keys.add(LUNARIS_UDFPS_CUSTOM_CATEGORY);
                    }
                    boolean hapticAvailable = DeviceUtils.hasVibrator(context);
                    if (!hasFingerprint || !hapticAvailable) {
                        keys.add(KEY_FP_SUCCESS);
                        keys.add(KEY_FP_ERROR);
                    }
                    return keys;
                }
            };
}
