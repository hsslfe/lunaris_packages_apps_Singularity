# Singularity dashboard

The dashboard replaces the top-level list with the supplied card layout: a live lockscreen clock and blurred wallpaper, Monet palette, wallpapers, theme/font shortcuts, status bar and QS shortcuts, AOD preview, and the six shortcut tiles. Sound remains accessible below the grid. The Theme Packs row opens the existing User Interface settings; its two buttons open the theme store and fonts. Team opens the Lunaris AOSP GitHub organization.

The dashboard follows Android's day/night configuration. Light mode uses pure white page/card surfaces with dark text and icons. Night mode uses pure black page/card surfaces. Icon wells and borders use neutral greys rather than wallpaper-tinted colours. Text and icons in the lockscreen card use a matching wallpaper scrim to remain readable. The wallpaper image alone receives `RenderEffect` blur; neither the clock nor the device's actual wallpaper is modified.

## Integration

This repository is a source overlay for `packages/apps/Settings/Singularity`. It has no standalone APK build configuration. The existing Settings `Android.bp` includes `Singularity/src/**/*.java`, `Singularity/src/**/*.kt` and `Singularity/res`, so the new files require no build-file changes. Build the platform-signed Settings app in the matching Lunaris Android 16 tree:

```sh
source build/envsetup.sh
lunch <your-device-target>
m Settings
```

The dashboard uses the standard AndroidX preference adapter for this page. Settings' grouped preference adapter would repaint the entire dashboard as one rounded, tinted group after the dashboard binds. Other Settings screens keep their existing adapters.

The original eight preference keys and fragment destinations remain in the preference screen for navigation and search. They are hidden only in the dashboard's runtime presentation. App navigation uses the existing Settings preference dispatch, including metrics and haptics. Missing external activities show an unavailable message instead of crashing.

Wallpaper decoding runs on a single worker, samples static images to at most 768 pixels per dimension, and closes the wallpaper file. It first reads the separate lockscreen wallpaper, then the system wallpaper when no separate image exists. Live wallpapers use their provider thumbnail. When access is denied or an image cannot be decoded, the themed card background is displayed. Loading refreshes on attachment, window visibility and wallpaper colour changes. Detaching cancels work, unregisters the listener and clears the image; stale results cannot overwrite a new attachment.

The platform Settings app already declares `MANAGE_EXTERNAL_STORAGE` and `READ_MEDIA_IMAGES`, which the Android wallpaper service accepts for wallpaper reads. No additional permission or service is introduced here. Monet and theme/font shortcuts target the same packages already used by User Interface settings.

At widths below 360dp or font scales above 1.3, the trailing swatches, theme/font buttons and AOD clock are hidden to leave room for labels. Their settings remain accessible through Monet, Theme Packs and AOD. Shortcut cards use `wrap_content` height within their weighted horizontal rows. Using `match_parent` here made Android measure the rows at 1px under RecyclerView's unconstrained height, causing missing labels and overlapping icons. Cards can grow vertically, use start/end positioning, and retain the host list's system-bar padding.

## Validation

Run the source regression checks from this repository:

```sh
python3 tools/validate_singularity_dashboard.py
```

The source checks also prevent pure black/white surfaces from becoming tinted and prevent weighted shortcut cards from returning to `match_parent` height. The checks cover preservation of the original destinations, dashboard binding IDs, new resource references, day/night palette completeness and text contrast, and placement of the wallpaper layer separately from the clock.

During implementation, all new resources plus the updated preference screen were compiled and linked using AAPT2 36.0.0 and the Android API 36 framework. All three changed/new Java classes compiled against API 36 and AndroidX libraries. The focused harness supplies stubs for ROM-specific Settings classes and metrics; it is not a complete Settings build.

The Android API 36 Robolectric fixture in `tests/robotests/src/org/lunaris/settings/DashboardLayoutTest.java` inflates the compiled dashboard XML and measures it with the same unconstrained height as a scrolling RecyclerView. It covers 320/360/393/600dp widths, both themes, 1.0/1.3/2.0 font scales and LTR/RTL: 48 configurations. It checks card height, label bounds, text overflow and exact black/white page colours. Run it in an API 36 Robolectric host with the Settings resource APK; this source overlay does not define a standalone test module.

The focused harness reproduced the old Statusbar card at 1px high. With the fix, the normal-size Statusbar and shortcut cards measure 60dp and 82dp respectively. Android native-graphics renders were inspected in both themes. These are layout fixtures; they do not exercise the full Settings activity or load a device wallpaper.

A full ROM tree and an Android device/emulator were unavailable, so full on-device integration is still unverified. Before merging:

- Build Settings in the matching ROM tree and open every dashboard shortcut.
- Compare light and dark modes, including switching themes while the dashboard is open.
- Set distinct home and lockscreen images, change them while Settings is in the background, then test a shared wallpaper and live wallpaper.
- Check 12/24-hour time, rotation, 320dp width, larger fonts, RTL, TalkBack, and gesture/three-button navigation.
- Navigate back and reopen the page to check toolbar restoration, wallpaper refresh and scroll position.
