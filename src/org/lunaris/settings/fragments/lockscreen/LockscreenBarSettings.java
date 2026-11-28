/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.settings.fragments.lockscreen;

import android.content.ContentResolver;
import android.os.UserHandle;
import android.provider.Settings;

/** Keep Now Bar exclusive with the two legacy lockscreen bars. */
public final class LockscreenBarSettings {
    public static final String NOW_BAR = "now_bar";
    public static final String DYNAMIC_BAR = "ax_dynamic_bar_lockscreen_enabled";
    public static final String MUSIC_PILL = "ax_dynamic_bar_keyguard_music_pill";

    private LockscreenBarSettings() {}

    public static void disableLegacyBars(ContentResolver resolver) {
        disableSecure(resolver, DYNAMIC_BAR);
        disableSecure(resolver, MUSIC_PILL);
    }

    public static void disableNowBar(ContentResolver resolver) {
        if (Settings.System.getIntForUser(resolver, NOW_BAR, 0, UserHandle.USER_CURRENT) != 0) {
            Settings.System.putIntForUser(resolver, NOW_BAR, 0, UserHandle.USER_CURRENT);
        }
    }

    public static boolean isLegacyBarEnabled(ContentResolver resolver, String key) {
        return Settings.Secure.getIntForUser(resolver, key, 0, UserHandle.USER_CURRENT) != 0;
    }

    private static void disableSecure(ContentResolver resolver, String key) {
        if (isLegacyBarEnabled(resolver, key)) {
            Settings.Secure.putIntForUser(resolver, key, 0, UserHandle.USER_CURRENT);
        }
    }

    // An old configuration can contain both types. Do not turn off either
    // legacy bar unless the user explicitly enables Now Bar.
    public static void resolveConflict(ContentResolver resolver) {
        if (isLegacyBarEnabled(resolver, DYNAMIC_BAR)
                || isLegacyBarEnabled(resolver, MUSIC_PILL)) {
            disableNowBar(resolver);
        }
    }
}
