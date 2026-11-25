# Singularity dashboard

The page uses the same Material colour roles as the Settings homepage: `surfaceContainer` for the page, `surfaceBright` for filled cards, `onSurface` / `onSurfaceVariant` for text, and the primary colour at 50% opacity for circular icon wells. Android's matching `system_*_light` and `system_*_dark` resources follow the active system palette in both themes. Cards have 24dp corners without the previous outlines or blue gradients. Labels inherit the Settings font family.

The page scrolls naturally to keep controls and larger text readable. The layout contains a compact lockscreen wallpaper card, Monet and wallpaper rows, theme/font shortcuts, Statusbar and QS cards, AOD, three action tiles, a Navigation/Miscellaneous pair, and one full-width Sound card. Team has been removed. Theme Packs opens the existing User Interface settings; its buttons open the existing theme store and font picker.

## Wallpaper preview

The lockscreen card starts at 88dp and can grow for larger text. The wallpaper is a backdrop: its intrinsic image dimensions do not participate in the card's height calculation. `LockscreenWallpaperPreviewView` reports zero background height during the initial unconstrained measurement, then fills the content-sized card during FrameLayout's exact measurement pass. This prevents a tall wallpaper from stretching the card.

No blur or wallpaper effect is applied. A black layer with alpha `0.3` dims the preview by 30% in both themes. The clock and title remain sharp, with white text and a small shadow. The device's wallpaper is never modified.

Decoding runs off the main thread, samples static images to at most 2048 pixels per dimension, and closes the file descriptor. The loader reads the separate lockscreen image first, then falls back to the home wallpaper when they are shared. Live wallpapers use provider thumbnails. If wallpaper access is unavailable, the filled card remains visible. Attachment, window visibility and wallpaper colour changes refresh the image. Detaching unregisters listeners, cancels work and clears stale results.

## Integration

This repository is a source overlay for `packages/apps/Settings/Singularity`. The existing Settings build includes its sources and resources. Build the platform-signed app in the matching Lunaris Android 16 tree:

```sh
source build/envsetup.sh
lunch <your-device-target>
m Settings
```

The original eight preference keys and destinations remain for navigation and search. Only their runtime presentation is replaced. The dashboard uses a plain AndroidX preference adapter so the Settings grouped adapter does not repaint the whole page as one rounded group. Other pages retain their existing adapters. App navigation preserves Settings dispatch, metrics and haptics; missing external activities are handled without crashing.

The platform Settings manifest already declares the wallpaper read permissions used by the loader. No permission, service, or wallpaper-setting action is added.

Trailing colour swatches, theme/font buttons and the AOD clock are hidden below 360dp or above 1.3 font scale to leave room for labels. Those settings remain available through their parent cards. Weighted shortcut rows use `wrap_content` height to avoid collapsing under RecyclerView's unconstrained measurement. The page retains system-bar padding.

## Validation

Run source checks from the repository:

```sh
python3 tools/validate_singularity_dashboard.py
```

They cover original destinations, card IDs, resource references, homepage colour-role aliases, filled surfaces, weighted row sizing, the 30% dim layer, and the single Sound card replacing Team.

The API 36 Robolectric fixture in `tests/robotests/src/org/lunaris/settings/DashboardLayoutTest.java` measures the compiled XML at 320/360/393/600dp, in both themes, at 1.0/1.3/2.0 font scales, and in LTR/RTL. Additional regressions load a tall bitmap, check that it cannot enlarge the lockscreen card, verify the image fills the card, and render a white wallpaper to check the 30% dim. Run it in a host with API 36 Robolectric and the Settings resource APK; this overlay does not define a standalone test module.

The focused local harness compiles resources with AAPT2 / API 36 and Java against AndroidX, with stubs for missing ROM-specific Settings classes. Native Android layout renders are inspected in both themes. It is not a complete ROM build or device test.

After building, verify navigation, theme switching, distinct/shared/live wallpapers, wallpaper changes while Settings is in the background, 12/24-hour time, larger fonts, RTL, TalkBack, system-bar spacing and back navigation on the device.
