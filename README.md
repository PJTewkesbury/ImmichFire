# ImmichFire

Fire TV / Android TV slideshow client for an Immich server, driven entirely by the TV remote.

## Features

- **Source picker:** Most Recent / Albums / Folders
- **Slideshow:** auto-advance with configurable interval, manual next/prev, play/pause
- **Transitions:** None / Crossfade / Slide / Ken Burns
- **Shuffle & loop**
- **Fit modes:** Fit / Fill / Fill-with-backdrop
- **Phone-based setup:** TV shows a QR code + pairing code → phone opens a form to enter URL & API key (no hunt-and-peck on the remote)
- **Immich-styled UI:** cream background, Immich indigo (`#4250AF`) accents, card-based layout that mirrors the Immich web settings — brand-matched launcher banner and mipmap icon
- **Fresh album list on every launch:** the source picker re-queries Immich on every `ON_RESUME`, and filters out albums with zero assets or a blank name

## Remote mapping (Slideshow)

| Button        | Action                     |
|---------------|----------------------------|
| D-pad Left    | Previous image             |
| D-pad Right   | Next image                 |
| D-pad Center  | Play / pause auto-advance  |
| D-pad Up      | Toggle info overlay        |
| D-pad Down    | Toggle controls bar        |
| Back          | Return to source picker    |

## First-time setup

1. Install the APK on the Fire TV (sideload or ADB).
2. Open the app. It shows a QR code and a 6-digit pairing code.
3. On a phone on the same Wi-Fi, scan the QR (or type the URL shown).
4. Fill in your Immich URL (e.g. `http://192.168.0.100:2283`) and API key, enter the pairing code, press **Save**.
5. The TV auto-advances to the source picker.

## Building

Open the folder in Android Studio. On first open:
- Let it generate the Gradle wrapper (or run `gradle wrapper` once from an existing Gradle install).
- Let it sync the dependencies.

From the command line (`build.bat` auto-locates Android Studio's bundled JDK):

```
build.bat debug         rem assemble the debug APK
build.bat release       rem assemble an unsigned release APK
build.bat clean
```

To install on the Fire TV over ADB:

```
adb connect 192.168.0.236:5555
./gradlew installDebug
```

Note: the Fire TV launcher caches the app's banner. If you change the banner
artwork, uninstall the app first (`adb uninstall com.intec.immichfire`) before
reinstalling, otherwise the launcher keeps the old tile.

## Branding assets

The 5-petal flower + play-TV badge artwork is rendered from a single WPF script
(`branding/generate-banner.ps1`) into every density the TV launcher and Android
need:

- `app/src/main/res/drawable-nodpi/banner.png` — 1280×720 Leanback banner (cream
  background, flower on the left, "immich" wordmark in brand purple on the right)
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` —
  rounded cream square launcher icons at 48/72/96/144/192 px
- `app/src/main/res/drawable/ic_immich_mark.xml` — in-app vector of the same
  mark, used by the `ImmichLogo` composable in the top-left of each screen

To regenerate after editing the script:

```
powershell.exe -STA -NoProfile -File branding\generate-banner.ps1
```

## Project layout

```
app/src/main/java/com/intec/immichfire/
  MainActivity.kt
  ImmichFireApplication.kt     -- Coil image loader setup
  data/                        -- Settings + DataStore
  network/                     -- Retrofit Immich API
  setup/ConfigServer.kt        -- NanoHTTPD config server (QR flow)
  ui/
    ImmichFireApp.kt           -- Nav graph
    setup/SetupScreen.kt       -- First-run QR + pairing code
    sources/SourcePickerScreen.kt  -- refreshes albums on every ON_RESUME,
                                      filters out empty/untitled albums
    slideshow/
      SlideshowScreen.kt       -- The main experience
      SlideshowViewModel.kt
    settings/SettingsScreen.kt -- Back button pinned at top (scroll anchor)
    theme/
      Theme.kt                 -- Immich color palette (lightColorScheme)
      Components.kt            -- ImmichLogo / SectionCard / RowButton /
                                  Chip / Toggle
  util/NetworkUtils.kt         -- local IPv4 for the QR URL
  util/QrCode.kt               -- ZXing QR renderer
```

## Known todos

- Image prefetch (enqueue next 2–3 images in Coil) for smoother transitions
- Ken Burns actual pan+zoom animation (currently a long crossfade stand-in)
- FillBlurBackdrop rendering (currently falls back to Fit)
- Paging for Most Recent beyond the first 500
- Immich mDNS / LAN discovery on the phone setup form
