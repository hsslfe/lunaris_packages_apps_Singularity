# Singularity dashboard

The dashboard replaces the top-level list with the supplied card layout: a live lockscreen clock and blurred wallpaper, Monet palette, wallpapers, theme/font shortcuts, status bar and QS shortcuts, AOD preview, and the six shortcut tiles. Sound remains accessible below the grid. The Theme Packs row opens the existing User Interface settings; its two buttons open the theme store and fonts. Team opens the Lunaris AOSP GitHub organization.

The dashboard follows Android's day/night configuration. Light mode uses white cards, a light page background, dark text and dark icons. Night mode uses a black page background and dark cards. Text and icons in the lockscreen card use a matching wallpaper scrim to remain readable. The wallpaper image alone receives `RenderEffect` blur; neither the clock nor the device's actual wallpaper is modified.

## Integration

This repository is a source overlay for `packages/apps/Settings/Singularity`. It has no standalone APK build configuration. The existing Settings `Android.bp` includes `Singularity/src/**/*.java`, `Singularity/src/**/*.kt` and `Singularity/res`, so the new files require no build-file changes. Build the platform-signed Settings app in the matching Lunaris Android 16 tree:

```sh
source build/envsetup.sh
lunch <your-device-target>
m Settings
```

The original eight preference keys and fragment destinations remain in the preference screen for navigation and search. They are hidden only in the dashboard's runtime presentation. App navigation uses the existing Settings preference dispatch, including metrics and haptics. Missing external activities show an unavailable message instead of crashing.

Wallpaper decoding runs on a single worker, samples static images to at most 768 pixels per dimension, and closes the wallpaper file. It first reads the separate lockscreen wallpaper, then the system wallpaper when no separate image exists. Live wallpapers use their provider thumbnail. When access is denied or an image cannot be decoded, the themed card background is displayed. Loading refreshes on attachment, window visibility and wallpaper colour changes. Detaching cancels work, unregisters the listener and clears the image; stale results cannot overwrite a new attachment.

The platform Settings app already declares `MANAGE_EXTERNAL_STORAGE` and `READ_MEDIA_IMAGES`, which the Android wallpaper service accepts for wallpaper reads. No additional permission or service is introduced here. Monet and theme/font shortcuts target the same packages already used by User Interface settings.

At widths below 360dp or font scales above 1.3, the trailing swatches, theme/font buttons and AOD clock are hidden to leave room for labels. Their settings remain accessible through Monet, Theme Packs and AOD. Cards can grow vertically, use start/end positioning, and retain the host list's system-bar padding.

## Validation

Run the source regression checks from this repository:

```sh
python3 tools/validate_singularity_dashboard.py
```

The checks cover preservation of the original destinations, dashboard binding IDs, new resource references, day/night palette completeness and text contrast, and placement of the wallpaper layer separately from the clock.

During implementation, all new resources plus the updated preference screen were compiled and linked using AAPT2 36.0.0 and the Android API 36 framework. All three changed/new Java classes compiled against API 36 and AndroidX libraries. The focused harness supplies stubs for ROM-specific Settings classes and metrics; it is not a complete Settings build.

A full ROM tree and an Android device/emulator were unavailable, so on-device rendering and integration are still unverified. Before merging:

- Build Settings in the matching ROM tree and open every dashboard shortcut.
- Compare light and dark modes, including switching themes while the dashboard is open.
- Set distinct home and lockscreen images, change them while Settings is in the background, then test a shared wallpaper and live wallpaper.
- Check 12/24-hour time, rotation, 320dp width, larger fonts, RTL, TalkBack, and gesture/three-button navigation.
- Navigate back and reopen the page to check toolbar restoration, wallpaper refresh and scroll position.
