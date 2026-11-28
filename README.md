# ImmichFire

Fire TV / Android TV slideshow client for an Immich server, driven entirely by the TV remote.

## Features

- **Source picker:** Most Recent / Albums / Folders (folders show their photo count and are displayed with the external-library root path stripped, so `/mnt/usb/2026/Wedding` reads as `2026/Wedding`)
- **Slideshow:** auto-advance with configurable interval, manual next/prev, play/pause
- **Transitions:** None / Crossfade / Slide / Ken Burns
- **Shuffle & loop**
- **Fit modes:** Fit / Fill / Fill-with-backdrop
- **Phone-based setup:** TV shows a QR code + pairing code → phone opens an Immich-themed form; the default path is **Sign in with your Immich email + password** (an API key named `"ImmichFire (<device model> / <TV IP>)"` is minted via `/api/auth/login` → `/api/api-keys` with only the scopes the app actually uses — `album.read`, `asset.read`, `asset.view`, `library.read` — and stored on the TV; the password is never persisted). An "API key" tab is still available for people who already have a key. The URL field accepts all three usual Immich shapes — `https://immich.example.com`, `http://192.168.0.8:2283`, `http://travelpi.lan:2283` — and auto-prepends `http://` and strips trailing slashes both client- and server-side so a bare hostname still works. No hunt-and-peck on the remote
- **Branded cold-start splash:** Immich flower mark on cream via the AndroidX core-splashscreen API, held on screen until the settings `StateFlow` has emitted its first value so the handoff to the first Compose frame has no empty-background flash. The Compose splash that takes over for ~1.5 s shows version / build date / commit hash in the lower-right corner (all baked in from `BuildConfig` at build time — no runtime git shell-out)
- **Immich-styled UI:** cream background, Immich indigo (`#4250AF`) accents, card-based layout that mirrors the Immich web settings — brand-matched launcher banner and mipmap icon
- **Fresh album list on every launch:** `SourcePickerViewModel.refresh()` re-queries Immich on every `ON_RESUME`, and filters out albums with zero assets or a blank name (filtering + sort live in the repository, not the composable)
- **Newest-first albums:** album list is sorted by `createdAt` descending so the most recently created album is at the top
- **D-pad zoom & pan:** hold **OK** (~0.5 s) during a slideshow to enter Zoom mode at 1.5×; short-press OK steps the zoom up in 0.5× increments to a maximum of 4×, then exits; D-pad arrows pan the zoomed image; Back exits zoom

## Remote mapping (Slideshow)

### Normal mode

| Button        | Action                     |
|---------------|----------------------------|
| D-pad Left    | Previous image             |
| D-pad Right   | Next image                 |
| D-pad Center  | Play / pause auto-advance  |
| D-pad Center (long-press ≥ 0.5 s) | Enter Zoom mode (1.5×, slideshow pauses) |
| D-pad Up      | Toggle info overlay        |
| D-pad Down    | Open controls bar          |
| Back          | Close controls bar (if open), otherwise return to source picker |

### Zoom mode

| Button        | Action                     |
|---------------|----------------------------|
| D-pad arrows  | Pan the image (clamped to viewport edges) |
| D-pad Center  | Step zoom +0.5× (1.5× → 2× → 2.5× → 3× → 3.5× → 4× → exit) |
| Back          | Exit Zoom mode (returns to 1×, slideshow stays paused) |

## First-time setup

1. Install the APK on the Fire TV (sideload or ADB).
2. Open the app. It shows a QR code and a 6-digit pairing code.
3. On a phone on the same Wi-Fi, scan the QR (or type the URL shown).
4. Enter your Immich server URL (e.g. `https://immich.example.com`, `http://192.168.0.100:2283`, or `http://travelpi.lan:2283` — `http://` is assumed if you omit the scheme) and the pairing code, then on the **Sign in** tab enter your Immich email and password and press **Sign in & Save**. The TV mints a dedicated API key (scoped `album.read` / `asset.read` / `asset.view` / `folder.read` / `library.read`) and stores it; your password is not kept. If you already have an API key you'd rather use, switch to the **API key** tab and paste it instead.
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

To install on the Fire TV over ADB in one shot (`install.bat` auto-locates
`adb.exe`, connects, and runs `install -r -d`):

```
install.bat debug       rem reinstall the current debug APK
install.bat release     rem reinstall the release APK (needs to be signed)
```

The device IP is hard-coded to `192.168.0.236:5555` at the top of
`install.bat` — change it there (or `set DEVICE=<ip:port>` before running)
to target a different Fire TV.

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
  ImmichFireApplication.kt         -- Lazy AppContainer; Coil image loader
                                      sharing the app's single OkHttpClient
  core/
    AppContainer.kt                -- Service locator: shared OkHttp, settings
                                      StateFlow, Credentials StateFlow,
                                      ImmichRepository. Context.appContainer
                                      is the accessor from anywhere.
    Constants.kt                   -- All magic numbers (ports, intervals,
                                      cache sizes, scan budgets, Ken Burns)
  data/
    Settings.kt                    -- ImmichSettings + enums
    SettingsRepository.kt          -- DataStore-backed; one toSettings()
                                      extension powers both the Flow and
                                      update(); prepends http:// if missing
    ImmichRepository.kt            -- Domain seam the ViewModels depend on
    RetrofitImmichRepository.kt    -- Concrete impl, caches Retrofit per
                                      credentials tuple
    ImmichError.kt                 -- Sealed error type + ApiResult<T>
  network/
    ImmichApi.kt                   -- Retrofit interface
    ImmichClient.kt                -- URL helpers only (thumbnailUrl, etc.)
    Models.kt                      -- Serializable DTOs
  setup/
    ConfigServer.kt                -- NanoHTTPD routing: /submit /test /discover
    LanDiscovery.kt                -- mDNS + /24 subnet probe
    SetupHtml.kt                   -- Index + result page templates
  ui/
    ImmichFireApp.kt               -- Nav graph; observes settings StateFlow
                                      for the start destination
    setup/
      SetupScreen.kt               -- QR + pairing code; Resolving / NoNetwork
                                      / Ready state machine for local IP
      SetupViewModel.kt
    sources/
      SourcePickerScreen.kt        -- Single outer LazyColumn; refreshes
                                      albums + folders on each ON_RESUME
      SourcePickerViewModel.kt     -- Also strips external-library import
                                      path prefixes from folder names and
                                      fans out per-folder asset counts in
                                      parallel
    splash/
      SplashScreen.kt              -- Compose hold-screen after the system
                                      splash; shows version / BUILD_DATE /
                                      GIT_COMMIT in the lower-right corner
    slideshow/
      SlideshowScreen.kt           -- Orchestrator (loop, zoom, input)
      SlideshowControls.kt         -- Overlay Chip / ToggleChip / Controls
      SlideshowViewModel.kt
      AssetMetaOverlay.kt          -- Bottom-right date/locality/rating card
      DateFormat.kt
    settings/
      SettingsScreen.kt            -- About card shows version + git hash
      SettingsViewModel.kt
    theme/
      Theme.kt                     -- Immich color palette
      Components.kt                -- ImmichLogo / SectionCard / RowButton /
                                      Chip / Toggle
  util/
    NetworkUtils.kt                -- Local IPv4 (used off-main in Setup)
    QrCode.kt                      -- ZXing QR renderer
```

All network I/O goes through `ImmichRepository` returning
`ApiResult<T>`; composables never talk to Retrofit directly. The single
`OkHttpClient` in `AppContainer` is shared between Retrofit and Coil, so
TLS sessions and connection pools are reused across API calls and
thumbnail loads.

## Advanced slideshow behaviour

- **Image prefetch:** the next 3 images are enqueued into Coil's memory/disk cache the moment the current index advances, so network latency doesn't slip through into a blank frame. Prefetch and foreground requests share the same `Bitmap.Config.RGB_565` so the prefetched decode is actually reusable from the memory cache.
- **Ken Burns:** when the transition is set to *KenBurns*, each slide gets its own `Animatable`-driven `graphicsLayer` transform — a slow zoom from 1.08× to 1.25× with a slight pan whose direction varies per slide index (NW → SE, NE → SW, etc.). The transform is read inside a `graphicsLayer { }` lambda so animation frames trigger redraw, not recomposition. A 900 ms crossfade on top keeps the motion continuous across slide changes.
- **Fill with blurred backdrop:** when the fit mode is *FillBlurBackdrop* (and the device is Android 12 / API 31+), the slideshow stacks a dedicated `ContentScale.Crop` request behind the foreground — a separate 480×480 RGB_565 image blurred at 20 dp with 1.1× scale, so the letterbox is a tinted out-of-focus wash instead of black. On older devices the mode silently falls back to *Fit*.
- **Paged Most Recent:** the "Most recent" source streams `api/search/metadata` page-by-page (500 per page, up to 20 pages = 10 000 images). Each page is appended into state as soon as it arrives, so the slideshow starts on page 1 while later pages continue to fill in. Shuffle is applied once after paging completes so the currently-playing slide doesn't change under the user.
- **LAN discovery on the phone setup form:** the phone setup page has a **Scan LAN** button that asks the Fire TV to look for Immich servers two ways in parallel:
  1. **mDNS / Bonjour** for `_immich._tcp.local.` (and `_http._tcp.local.` services whose name contains "immich" as a fallback). JmDNS is bound to the TV's own NIC, and a WiFi `MulticastLock` is held while querying so power saving doesn't drop responses. Supports TXT records `scheme` and `path` for custom hosts.
  2. **/24 subnet probe** of `/api/server/ping` on port 2283 (configurable via `?ports=2283,80,8080`).
  Results are merged, deduplicated by URL, and shown as tap-to-fill buttons tagged `[mDNS]` or `[scan]`. mDNS matches sort first.

## Cache & storage footprint

The app is tuned to keep its on-device footprint small — important on a Fire TV Stick that only ships with ~8 GB of internal storage.

| What | Where | Bound |
|---|---|---|
| Decoded image bitmaps | Coil memory cache | **15 %** of the app's heap (down from the usual 25 %), auto-evicted LRU; slideshow bitmaps use `RGB_565` so each ~1920×1080 preview is ~4 MB not ~8 MB |
| Downloaded thumbnails | Coil disk cache (`cacheDir/image_cache`) | **64 MB** hard cap (down from 512 MB), auto-evicted LRU; cleared on uninstall |
| Settings (URL, API key, prefs) | `DataStore` preferences | A few hundred bytes; `allowBackup=false` so it is never uploaded to Google Drive by Android Auto Backup |
| HTTP logs | *disabled* | `HttpLoggingInterceptor` removed entirely — the API-key header is never written to logcat |

Coil's LRU handles memory-cache eviction itself; the slideshow does not
wipe the cache on exit, so hopping to Settings and back doesn't throw
away warm bitmaps the user will see again seconds later.

## Advertising an Immich server over mDNS

Immich doesn't advertise itself on mDNS out of the box. On a Linux host running `avahi-daemon`, create `/etc/avahi/services/immich.service`:

```xml
<?xml version="1.0" standalone='no'?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name replace-wildcards="yes">Immich on %h</name>
  <service>
    <type>_immich._tcp</type>
    <port>2283</port>
    <txt-record>scheme=http</txt-record>
    <txt-record>path=</txt-record>
  </service>
</service-group>
```

Reload Avahi (`systemctl reload avahi-daemon`) and the TV's **Scan LAN** button will find the server via mDNS on the next scan.

## Known todos

- Additional ports in the LAN scan (currently only 2283 by default)
