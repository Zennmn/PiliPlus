# Android liquid glass build and device checklist

The floating-navigation branch uses the vendored AndroidLiquidGlass LiquidBottomTabs
and Backdrop renderer. Enable the existing **悬浮底栏** option, then restart the app.
The default remains off. Android 13 or later is required.

## Build

Run in PowerShell 7 at the project root:

```powershell
rtk proxy pwsh.exe -NoProfile -File tool/build_glass_release.ps1
```

The helper uses Flutter 3.47.6 and a Pub cache under `.build/glass`, applies the existing
Android SDK/Material UI patches there, downloads pinned native player dependencies
with checksum verification, and produces a universal release APK in `dist`.
It also generates Android runtime plugin registration independently of desktop
symlink setup and checks that the registration class is present in the final APK.
Existing JDK21 and Android SDK37/NDK28.2.13676358 are required. It does not install or
launch an emulator. It preserves the project's Flutter renderer setting.

The reusable release key is `android/app/piliplus-glass-release.jks`; its configuration
is `android/key.properties`. Both are ignored by Git. Keep both files together for
future builds. The APK retains `com.example.piliplus`, so an installed APK signed with
a different certificate cannot be replaced directly. No installed application is
automatically removed by the build helper.

## Checks

```powershell
. ./tool/glass_environment.ps1
rtk proxy flutter analyze --no-pub --no-fatal-infos lib tool/checks
rtk proxy flutter test --no-pub test/utils/accounts/deleted_account_test.dart tool/checks/liquid_navigation_events_test.dart tool/checks/liquid_glass_surface_test.dart
```

Runtime/visual checks are assigned to the user, per request:

- Enable floating navigation; scroll an image-rich home/dynamic feed and verify the
  content refracts and blurs through the capsule without freezing or showing a black layer.
- The AM-plus-plus adaptation uses a 56dp capsule (48dp thumb), with 40dp side
  margins, with 4dp background blur. Icon glyphs are 24sp and labels 11sp. The Android floating bar stays
  visible and interactive while scrolling, regardless of the bottom-bar hide setting.
  Press any tab and drag across tabs before release; verify expansion, dispersion,
  highlight and spring return. Tap all tabs and repeat the selected home/dynamic tab
  to check return-to-top/refresh.
- Capsule touches must not click or scroll the feed underneath. The surrounding
  transparent spacing must still scroll the feed. A drag started on the selected
  capsule must continue until release even when the finger leaves the capsule.
- Press the current tab and confirm it stays in place until moved. Press a different
  tab and confirm the thumb moves to the finger. A second finger must wait while
  the first holds, then take over after it lifts; confirm only the final release
  commits navigation and a stationary current-tab tap triggers one reselect action.
- Compare light and dark app themes; test dynamic dot/number badges and tab reorder.
- Test both scroll-hide settings and confirm the glass bar stays in place and
  responds after the search bar has hidden. Test return from a detail page, dialogs/bottom sheets,
  orientation changes, system gesture/three-button navigation and UI/text scaling.
- Test background/resume, audio playback and picture-in-picture; verify no stuck
  native layer and that normal bottom navigation/sidebar still work when selected.

Rendering copies no pixels over MethodChannel. Only Flutter's HC background
FlutterImageView is drawn into the native hardware effect layer; Compose state
messages cannot echo a navigation event. Source attribution is in
`android/liquidglass/NOTICE.md` and the vendored Apache license.
