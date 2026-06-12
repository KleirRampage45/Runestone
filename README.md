# Runestone

**Multi-engine RPG Maker & visual novel game launcher for Android.**

Import, organize, and play games from **16 different engines** on your Android
device. The default APK ships every core runtime bundled natively — no
downloads, no plugin hunting, no surprises. Optional heavy runtimes (Godot)
live behind an Addons toggle.

- **Version:** v0.8.2.2d
- **APK size:** ~82 MB (default)
- **License:** GPLv2+
- **Repo:** https://github.com/KleirRampage45/Runestone
- **Dev branch:** `develop` (consolidates all merged work since v0.6.13)

---

## Supported Engines (11 working, 2 detect-ready, 3 legacy)

| Engine | Status | Runtime | How |
|--------|--------|---------|-----|
| RPG Maker XP | Working | mkxp-z native | Bundled .so |
| RPG Maker VX | Working | mkxp-z native | Bundled .so |
| RPG Maker VX Ace | Working | mkxp-z native | Bundled .so |
| RPG Maker 2000 | Working | EasyRPG native | Bundled .so, full JNI |
| RPG Maker 2003 | Working | EasyRPG native | Bundled .so, full JNI |
| RPG Maker MV | Working | WebView | System Chromium |
| RPG Maker MZ | Working | WebView | System Chromium |
| TyranoBuilder | Working | WebView | System Chromium |
| Construct 2/3 | Working | WebView | System Chromium |
| HTML5 Games | Working | WebView | System Chromium |
| Twine | Working | WebView | System Chromium |
| VN Maker | Working | WebView | System Chromium |
| NScripter / ONScripter | Working | libonscripter.so | Bundled .so, full JNI bridge |
| Flash (Ruffle) | Working | Ruffle.js CDN | WebView + CDN |
| Ren'Py | Detection + saves | librenpython.so (55 MB) | Activity wrapper pending |
| Godot | Optional addon | libgodot_android.so (142 MB) | Enable in Settings > Addons |

**Legacy (detect only — no open-source runtime exists):**
RPG Maker 95 (1997), Dante 98 (1992), Electron apps (too heavy for mobile).

---

## Quick Start

```bash
git clone --recursive https://github.com/KleirRampage45/Runestone.git
cd Runestone
./gradlew clean :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** Android 8.0+ (API 26), arm64-v8a, 2 GB+ RAM recommended.

---

## APK Size Breakdown

| Component | Size | Notes |
|-----------|------|-------|
| librenpython.so | 55 MB | Ren'Py Python runtime (kept for future wrapper) |
| libmkxp-z.so + libruby.so | ~25 MB | RGSS interpreter for XP/VX/VX Ace, NDK r27 build |
| libeasyrpg_android.so | ~15 MB | RM2000/2003 runtime |
| libinnoextract_jni.so + boost deps | ~6 MB | RTP extraction (innoextract compiled as JNI) |
| libonscripter.so + libsdl.so | ~3 MB | ONScripter native runtime |
| SDL2 libraries (5 libs) | ~7 MB | SDL2_image, ttf, sound, openal, c++_shared |
| Java/Kotlin code | ~5 MB | App code, JNI bridges, UI |
| Resources + assets | ~3 MB | Icons, fonts, HTML templates |
| **Default APK total** | **~82 MB** | Everything except Godot |
| Godot (optional) | +142 MB | In `optional-libs/godot/`, enable via Addons |

---

## Features

- **13 working engines** — All major RPG Maker versions, VNs, HTML5, Flash,
  NScripter
- **Zero downloads** — 82 MB APK has every core runtime you need
- **SAF import** — Import games from any file manager or cloud storage
- **Sparse patch workspace** — Patches apply in place with reversible
  per-file backups (no full-copy storage cost)
- **Virtual gamepad** — D-pad + A/B/X/Y + L1/R1 shoulder buttons +
  SELECT/START/SETTINGS, with a runtime layout menu
- **Layout modes** — Portrait, Landscape; runtime controls toggleable without
  leaving the game
- **3D carousel UI** — Glassmorphism card carousel with bloom, grain, DOF
  effects
- **Game store** — Download games from a built-in catalogue (Pixeldrain,
  archive.org mirrors; Mediafire disabled)
- **Search, filter, sort** — By engine, name, date, recently played
- **Per-game settings** — Individual input / video / audio / cheat / font
  config per game via `PerGameConfig` JSON
- **Save protection** — Auto-backup saves before reimport, auto-restore
- **RTP installer** — Auto-detects VX Ace and prompts the official RTP
  download (innoextract JNI, no `execve`)
- **RESUME / STOP** — Pause games and resume later without losing progress
- **Open source** — GPLv2+, fork it, contribute, audit it, trust it

---

## Architecture

```
Runestone (82 MB APK)
├── Engine Plugin System (GameEngine interface)
│   ├── MkxpZEngine       — RGSS1/2/3 via libmkxp-z.so (NDK r27)
│   ├── EasyRpgEngine     — RM2000/2003 via libeasyrpg_android.so
│   ├── OnscripterEngine  — NScripter via libonscripter.so (new in v0.6.13)
│   ├── WebView engine    — MV/MZ/Tyrano/Construct/HTML/Twine/VN Maker/Ruffle
│   └── Ren'Py / Godot    — optional addons
├── Game Store
│   └── Static JSON catalogue + Pixeldrain / archive.org downloads
├── Import Pipeline
│   └── SAF → workspace isolation → engine detection → settings
├── Native Runtimes (jniLibs/arm64-v8a/)
│   └── 12+ .so files, all bundled; Godot is opt-in
└── UI (Kotlin, programmatic, glassmorphism)
    └── Home (carousel/grid/list/tiles) + Settings + Game Activity + Controls
```

---

## Repository Layout

```
.
├── app/                       Android Gradle module
│   ├── build.gradle.kts       versionName / versionCode
│   └── src/main/...
├── native/                    NDK submodules
│   ├── mkxp-z-android/        (submodule) RGSS runtime
│   └── easyrpg-android/       (submodule) RM2000/2003 runtime
├── optional-libs/             .so files for opt-in engines
│   └── godot/                 libgodot_android.so + libc++_shared_godot.so
├── docs/
│   ├── SPARSE-PATCH-WORKSPACE.md   current install/patch model
│   ├── design/                     live design specs (3D carousel, theme, GL FX)
│   └── archive/                    superseded research, plans, analyses
├── AGENTS.md                  conventions for AI agents working on the repo
├── CHANGELOG.md               per-version change log
├── CONTRIBUTING.md            contribution guidelines
├── DESIGN.md                  high-level design document
├── README.md                  this file
├── THIRD_PARTY.md             third-party license attributions
├── VISION-ROADMAP.md          current and future work
└── bugreport.md               user-reported issues log
```

---

## Development

```bash
# Build
./gradlew clean :app:assembleDebug

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Optional: enable Godot addon
cp optional-libs/godot/libgodot_android.so app/src/main/jniLibs/arm64-v8a/
cp optional-libs/godot/libc++_shared_godot.so app/src/main/jniLibs/arm64-v8a/
```

**Native rebuilds** (only needed if you change mkxp-z or innoextract source):

```bash
cd native/mkxp-z-android
# follow that repo's NDK r27 build instructions
```

---

## License

GNU General Public License v2 or later. See `LICENSE`.

Runestone bundles several third-party components — see `THIRD_PARTY.md` for
full attribution.

- mkxp-z (GPLv2+) — RGSS runtime
- EasyRPG Player (GPLv3) — RM2000/2003 runtime
- Ren'Py (MIT) — Python-based VN engine
- ONScripter (GPLv2+) — NScripter runtime
- Ruffle (MIT) — Flash emulator
- Ruby (GPLv2+) — Ruby interpreter
- SDL2 (zlib) — Graphics/audio/input
- OpenAL (LGPL) — Audio library
- innoextract (GPLv2+) — Inno Setup extractor, used for the VX Ace RTP
- Godot (MIT) — Optional addon
- Boost (BSL-1.0) — bundled as static libs with `libmkxp-z.so` and
  `libinnoextract_jni.so`

---

*82 MB. Open source. No cloud. No bullshit.*
