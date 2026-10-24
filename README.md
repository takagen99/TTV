# TTV — Custom Android TV Launcher

A replacement home screen launcher for Android TV and Google TV devices. Features dynamic wallpapers pulled from TMDB, a clean app grid with show/hide management, HDMI quick-switch tiles, and a home-button intercept via Android Accessibility Service.

> Package: `com.takagen.launcher` · Language: Java · minSdk 21 (Android 5.0) · targetSdk 30 · compileSdk 35

---

## Features

### App Grid
- Separate rows for **TV apps** (banner style, 16:9) and **Mobile apps** (icon style).
- Adjustable row height for both categories.
- **Show / Hide apps** — manage which apps appear on the launcher.
- **Favourites** — pin apps to a favourites row via Room database.
- Drag-to-reorder (planned/partial).

### Dynamic Wallpapers
- Fetches trending and themed wallpapers from **TMDB** (The Movie Database API).
- Categories: Trending, Anime, Movies, TV Series.
- **Local folder** — use images from a folder on the device instead.
- Auto-cycles wallpapers at a configurable interval (5 – 3600 seconds).
- Wallpapers are cached and applied as full-bleed backgrounds.

### Quick Settings Overlay
- Expandable overlay panel (swipe or button to open) with:
  - **WiFi** settings shortcut
  - **HDMI input** selector (HDMI 1/2/3, Composite)
  - Accessibility settings shortcut
  - **Auto-start on boot** toggle
  - Row height sliders for TV and Mobile app sections

### Settings
- TMDB API key configuration.
- Wallpaper source (TMDB category or local folder).
- Language selection: **English** / **中文 (Chinese)**.
- Backup and restore all settings as JSON.
- File browser for media navigation.
- System info: Android version, device model, RAM and storage.

### System Integration
- **Boot auto-start** via `BootReceiver` + `BootForegroundService`.
- **Home button interception** via `AccessibilityKeyDetector` (AccessibilityService) — prevents the stock launcher from hijacking the home button on Android 11+.

---

## Tech Stack

- **Language:** Java 11
- **UI:** XML layouts, Material Design, AndroidX
- **Architecture:** MVP-inspired with data binding
- **Database:** Room 2.6.1 (favourites + hidden apps)
- **Image Loading:** Glide 4.16
- **JSON:** Gson 2.11
- **TV Libraries:** androidx.leanback 1.0.0
- **Build:** AGP 8.5.1, Gradle wrapper included

---

## Project Structure

```
app/src/main/java/com/takagen/launcher/
├── App.java                     # Application class + handler setup
├── Init.java                    # Context initialization singleton
├── Setting.java                 # SharedPreferences wrapper
├── adapter/                     # RecyclerView adapters (main grid, rows, hidden-app list)
├── bean/                        # Data models: AppInfo, AppItem, FavApp (Room), HiddenApp (Room)
├── db/                          # Room database (FavAppDB) + DAOs
├── receiver/
│   └── BootReceiver.java        # Starts boot foreground service
├── service/
│   ├── AccessibilityKeyDetector.java  # Home-button intercept (must be enabled in Accessibility Settings)
│   └── BootForegroundService.java     # Foreground service for auto-start
├── ui/
│   ├── MainActivity.java        # Main launcher screen
│   ├── SettingsActivity.java    # Settings (3 sections: General, Wallpaper, System)
│   ├── AppsHideActivity.java    # Show/hide app manager
│   ├── QuickSettingsOverlay.java# Overlay panel with quick tiles
│   ├── FileBrowserActivity.java # Media file browser
│   ├── FolderBrowserActivity.java # Folder picker (wallpaper source)
│   ├── BackupRestoreActivity.java # Settings backup/restore
│   └── dialog/InputDialog.java  # Generic text input dialog
└── utils/
    ├── Clock.java               # Clock/date ticker
    ├── KeyUtil.java             # Key event helpers
    ├── LocaleHelper.java        # Language switching (English/Chinese)
    ├── Prefers.java             # Shared preferences helpers
    ├── ResUtil.java             # Resource utilities
    ├── Utils.java               # System UI hiding, general helpers
    └── Wallpaper.java           # TMDB fetch, local folder scan, wallpaper cycling
```

---

## Deployment (ADB Sideloading)

TTV is not distributed via the Play Store. Deploy to an Android TV device via ADB:

```bash
# Install
adb connect <device-ip>
adb install -r TTV.apk

# Set as default launcher (requires root or Android TV device that allows it)
adb shell cmd package set-home-activity com.takagen.launcher/.ui.MainActivity
```

See `read.txt` in the repo root for the exact ADB commands used during development.

### Enable Accessibility Service (required for home-button intercept)

On the TV device: **Settings → Device Preferences → Accessibility → TTV Key Detector → Enable**

Without this, pressing Home will open the stock launcher.

---

## TMDB API Key

Wallpaper fetching requires a free TMDB API key:

1. Register at [www.themoviedb.org](https://www.themoviedb.org).
2. Go to Settings → API and copy your **API Read Access Token (v4)**.
3. In the TTV launcher, open **Settings → TMDB API Key** and paste the token.

Without a key, wallpapers default to a static background or a locally selected folder.

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | TMDB API calls |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on device boot |
| `FOREGROUND_SERVICE` | Keep boot service running |
| `READ_EXTERNAL_STORAGE` | Local wallpaper folder access |
| `BIND_ACCESSIBILITY_SERVICE` | Home button interception |
| `SYSTEM_ALERT_WINDOW` | Quick-settings overlay |

---

## Localization

UI is available in **English** and **Simplified Chinese (中文)**, switchable in Settings without restarting the launcher.
