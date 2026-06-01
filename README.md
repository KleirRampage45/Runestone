# Runestone

**Multi-engine RPG Maker & visual novel game launcher for Android.**

Import, organize, and play games from **16 different engines** on your Android device.
**82MB APK** with all core runtimes bundled natively — no downloads needed.

---

## Supported Engines (11 working, 2 detect-ready, 2 legacy)

| Engine | Status | Runtime | How |
|--------|--------|---------|-----|
| RPG Maker XP | ✅ WORKING | mkxp-z native | Bundled .so |
| RPG Maker VX | ✅ WORKING | mkxp-z native | Bundled .so |
| RPG Maker VX Ace | ✅ WORKING | mkxp-z native | Bundled .so |
| RPG Maker 2000 | ✅ WORKING | EasyRPG native | Bundled .so, full JNI |
| RPG Maker 2003 | ✅ WORKING | EasyRPG native | Bundled .so, full JNI |
| RPG Maker MV | ✅ WORKING | WebView | System Chromium |
| RPG Maker MZ | ✅ WORKING | WebView | System Chromium |
| TyranoBuilder | ✅ WORKING | WebView | System Chromium |
| Construct 2/3 | ✅ WORKING | WebView | System Chromium |
| HTML5 Games | ✅ WORKING | WebView | System Chromium |
| Twine | ✅ WORKING | WebView | System Chromium |
| VN Maker | ✅ WORKING | WebView | System Chromium |
| **NScripter / ONScripter** | ✅ **WORKING** | libonscripter.so | **New!** Bundled .so |
| Flash (Ruffle) | ✅ WORKING | Ruffle.js CDN | WebView + CDN |
| **Ren'Py** | ⚠️ Wrapper pending | librenpython.so (55MB) | Detection + saves work |
| **Godot** | 🔧 Optional addon | libgodot_android.so (142MB) | Enable in Settings > Addons |

### Legacy (detect only — no open-source runtime)
- RPG Maker 95 (1997) / Dante 98 (1992)
- Electron apps (too heavy for mobile)

---

## Quick Start

```bash
git clone --recursive https://github.com/KleirRampage45/Runestone.git
cd Runestone
./gradlew clean :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** Android 8.0+ (API 26), arm64-v8a, 2GB+ RAM recommended.

---

## APK Size Breakdown

| Component | Size | Notes |
|-----------|------|-------|
| librenpython.so | 55 MB | Ren'Py Python runtime (kept for future wrapper) |
| libmkxp-z.so + libruby.so | ~25 MB | RGSS interpreter for XP/VX/VX Ace |
| libeasyrpg_android.so | ~15 MB | RM2000/2003 runtime |
| libonscripter.so + libsdl.so | ~3 MB | ONScripter native ✅ WORKING |
| SDL2 libraries (5 libs) | ~7 MB | SDL2_image, ttf, sound, openal, c++_shared |
| Java/Kotlin code | ~5 MB | App code, JNI bridges, UI |
| Resources + assets | ~3 MB | Icons, fonts, HTML templates |
| **Total** | **~82 MB** | |
| Godot (optional) | +142 MB | In optional-libs/godot/, enable via Addons |

---

## Features

- **11 working engines** — All major RPG Maker versions + VNs + HTML5 + Flash
- **Zero downloads** — 82MB APK has everything you need
- **SAF import** — Import games from any file manager or cloud storage
- **Virtual gamepad** — D-pad + A/B/X/Y + L1/R1 shoulder buttons + SELECT/START/SETTINGS
- **3 layout modes** — Portrait Console, Landscape (overlay), Gamepad (no overlay)
- **3D carousel UI** — Glassmorphism card carousel with bloom, grain, DOF effects
- **Game store** — Download games from built-in catalogue (12 free games, more to come)
- **Search, filter, sort** — By engine, name, date, recently played
- **Per-game settings** — Individual input/video/audio/cheat/font config per game
- **Save protection** — Auto-backup saves before reimport, auto-restore
- **RESUME/STOP** — Pause games and resume later without losing progress
- **Open source** — GPLv2+, fork it, contribute, audit it, trust it

---

## Architecture

```
Runestone (82MB APK)
├── Engine Plugin System (GameEngine interface)
│   ├── MkxpZEngine       — RGSS1/2/3 via libmkxp-z.so
│   ├── EasyRpgEngine     — RM2000/2003 via libeasyrpg_android.so
│   ├── OnscripterEngine  — NScripter via libonscripter.so
│   ├── WebViewEngine     — MV/MZ/Tyrano/Construct/HTML/Twine/VN Maker/Ruffle
│   └── Ren'Py/Godot      — Coming soon (wrapper or optional)
├── Game Store
│   └── Static JSON catalogue + Pixeldrain/Mediafire(RIP)/Rootz downloads
├── Import Pipeline
│   └── SAF → workspace isolation → engine detection → settings
├── Native Runtimes (jniLibs/arm64-v8a/)
│   └── 12 .so files, all bundled
└── UI (Kotlin, programmatic, glassmorphism)
    └── Home (carousel/grid/list/tiles) + Settings + Game Activity + Controls
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

---

## License

GNU General Public License v2 or later. See LICENSE.

Runestone bundles several third-party components — see THIRD_PARTY.md for full attribution.

- mkxp-z (GPLv2+) — RGSS runtime
- EasyRPG Player (GPLv3) — RM2000/2003 runtime
- Ren'Py (MIT) — Python-based VN engine
- ONScripter (GPLv2+) — NScripter runtime
- Ruffle (MIT) — Flash emulator
- Ruby (GPLv2+) — Ruby interpreter
- SDL2 (zlib) — Graphics/audio/input
- OpenAL (LGPL) — Audio library
- Godot (MIT) — Optional addon

---

*82MB. Open source. No cloud. No bullshit.*
