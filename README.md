# ImmichFire

Fire TV / Android TV slideshow client for an Immich server, driven entirely by the TV remote.

## Features

- **Source picker:** Most Recent / Albums / Folders
- **Slideshow:** auto-advance with configurable interval, manual next/prev, play/pause
- **Transitions:** None / Crossfade / Slide / Ken Burns
- **Shuffle & loop**
- **Fit modes:** Fit / Fill / Fill-with-backdrop
- **Phone-based setup:** TV shows a QR code + pairing code → phone opens a form to enter URL & API key (no hunt-and-peck on the remote)

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

To install on the Fire TV over ADB:

```
adb connect 192.168.0.236:5555
./gradlew installDebug
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
    sources/SourcePickerScreen.kt
    slideshow/
      SlideshowScreen.kt       -- The main experience
      SlideshowViewModel.kt
    settings/SettingsScreen.kt
    theme/Theme.kt
  util/NetworkUtils.kt         -- local IPv4 for the QR URL
  util/QrCode.kt               -- ZXing QR renderer
```

## Known todos

- Image prefetch (enqueue next 2–3 images in Coil) for smoother transitions
- Ken Burns actual pan+zoom animation (currently a long crossfade stand-in)
- FillBlurBackdrop rendering (currently falls back to Fit)
- Paging for Most Recent beyond the first 500
- Immich mDNS / LAN discovery on the phone setup form
