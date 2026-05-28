# Runestone

**Multi-engine RPG Maker game launcher for Android — ALL engines bundled natively.**

Import, organize, and play RPG Maker and other games on your Android device with **zero external downloads needed**.

---

## 🎮 Supported Engines

### RPG Maker Series
- **XP / VX / VX Ace** — via `libmkxp-z.so` (native arm64-v8a runtime)
- **MV / MZ** — via Android WebView (Chromium-based, no extra runtime needed)
- **EasyRPG** — via `libeasyrpg_android.so` (GPLv3 licensed fork)

### Visual Novels & Other
- **Ren'Py** — via `librenpython.so` (Python runtime for Ren'Py games)
- **Godot** — via `libgodot_android.so` (Godot 4.6.3, MIT licensed)
- **ONScripter** — via `libonscripter.so` (ONS format support)
- **Ruffle** — via embedded Flash runtime (for old Flash games)

---

## 📦 All Engines Bundled — Zero Downloads Needed

Unlike JoiPlay or other launchers, **Runestone bundles ALL required runtimes natively**.

### APK Size
- **~223 MB** — Complete with all bundled engines
- **~5 MB** — Without any bundled engines (WebView-only mode)

### Bundled Runtime Libraries (arm64-v8a)
| Library | Size | Engine | License |
|---------|------|--------|---------|
| `libgodot_android.so` | 142 MB | Godot 4.6.3 | MIT |
| `librenpython.so` | 55 MB | Ren'Py 8.3.4 | MIT |
| `libeasyrpg_android.so` | 15 MB | EasyRPG Player 0.8.1 | GPLv3 |
| `libmkxp-z.so` | 13 MB | RPG Maker XP/VX/VX Ace | GPLv2+ |
| `libruby.so` | 12 MB | Ruby 3.x (for mkxp-z) | GPLv2+ |
| `libonscripter.so` | 2.3 MB | ONScripter | GPLv2+ |
| `libSDL2.so` | 592 KB | SDL 1.2 (ONScripter) | zlib |
| `libSDL2_image.so` | 7 MB | SDL2_image | zlib |
| `libSDL2_ttf.so` | 883 KB | SDL2_ttf | zlib |
| `libSDL2_sound.so` | 903 KB | SDL2_sound | zlib |
| `libopenal.so` | 1 MB | OpenAL audio | LGPL |
| `libc++_shared.so` | 1 MB | C++ STL | Apache 2.0 |
| `libgamebrowser.so` | 76 KB | In-game browser | MIT |
| `libc++_shared_godot.so` | (bundled with Godot) | Godot runtime | MIT |

---

## 🚀 Features

- ✅ **Zero external downloads** — All engines bundled, no runtime downloads needed
- ✅ **Android SAF import** — Import games from any file manager
- ✅ **Per-game settings** — Configurable launch options, controls, and more
- ✅ **Workspace isolation** — Separate saves and data per game
- ✅ **Virtual gamepad** — Touch-based D-pad and action buttons
- ✅ **Touch overlay** — Landscape mode with edge controls
- ✅ **Glassmorphism UI** — Modern, clean interface with animations
- ✅ **Search, filter, sort** — Find games quickly
- ✅ **Adaptive icon** — Auto-generates launcher icon

---

## 🏗️ Architecture

```
Runestone Launcher
├── EngineDetector       → Detects RPG Maker engine from game files
├── mkxp-z (native)      → XP / VX / VX Ace runtime (ARM64 .so)
├── EasyRPG (native)     → EasyRPG Player runtime (ARM64 .so)
├── WebView (system)     → MV / MZ runtime (Chromium via Android WebView)
├── Ren'Py (native)      → Ren'Py Python runtime (ARM64 .so)
├── Godot (native)       → Godot 4.6.3 runtime (ARM64 .so)
├── ONScripter (native)  → ONScripter runtime (ARM64 .so)
├── Ruffle (native)      → Flash runtime (ARM64 .so)
├── SAF Importer         → Import games from any file manager
├── Workspace Manager    → Isolated storage per game
└── UI                   → Home, Settings, Import, Game Activity screens
```

---

## 📋 Goals

- ✅ Lightweight APK (~5 MB without engines, ~223 MB complete)
- ✅ No external dependencies — all runtimes embedded or using system APIs
- ✅ Import games via Android SAF (Storage Access Framework)
- ✅ Per-game settings, workspace isolation, save management
- ✅ Virtual gamepad and touch overlay
- ✅ Clean, modern UI with glassmorphism
- ✅ Search, filter, and sort games
- ✅ Adaptive launcher icon

---

## 📄 License

GNU General Public License v2 or later — see LICENSE.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

### Third-Party Components

| Component | License | Description |
|-----------|---------|-------------|
| mkxp-z | GPLv2+ | RGSS runtime for XP/VX/VX Ace games |
| EasyRPG Player | GPLv3 | RPG Maker MV/MZ interpreter |
| Ren'Py | MIT | Python-based visual novel engine runtime |
| Godot | MIT | Godot 4.6.3 Android runtime |
| Ruffle | MIT | Flash Player emulator |
| ONScripter | GPLv2+ | ONS format visual novel engine |
| Android WebView | Apache 2.0 / BSD | System component for MV/MZ HTML5 rendering |
| Ruby | GPLv2+ | Ruby 3.x runtime for mkxp-z |
| SDL | zlib | Simple DirectMedia Layer |
| OpenAL | LGPL | Audio library |

See THIRD_PARTY.md for details.

---

## 📱 Installation

### From GitHub
1. Clone the repository
2. Build the debug APK:
   ```bash
   ./gradlew clean :app:assembleDebug
   ```
3. Install on device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Requirements
- Android 5.0+ (API 21+)
- arm64-v8a architecture
- 1 GB+ RAM recommended

---

## 🎮 Usage

1. Launch Runestone
2. Tap **Import** and select your game folder
3. Configure settings per game (controls, launch options, etc.)
4. Tap **Play** to launch

### Tips
- Use **Settings** to configure global preferences
- Use **Search** to find games quickly
- Use **Workspace** to manage saves and data
- Landscape mode shows virtual D-pad on the left

---

## 🔄 Development

### Building
```bash
# Clone with submodules
git clone --recursive https://github.com/KleirRampage45/Runestone.git
cd Runestone

# Build debug APK
./gradlew clean :app:assembleDebug

# Build release APK (requires signing)
./gradlew clean :app:assembleRelease
```

### Testing
```bash
# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# Run tests
./gradlew test
```

### Contributing
See CONTRIBUTING.md for guidelines.

---

## 📚 References

- [Grimmobile](https://github.com/KleirRampage45/blacksouls-android) — Original Black Souls launcher architecture
- [mkxp-z](https://github.com/RetroArch/mkxp-z) — RPG Maker XP/VX/VX Ace core
- [EasyRPG Player](https://github.com/EasyRPG/EasyRPG) — RPG Maker MV/MZ interpreter
- [Ren'Py](https://www.renpy.org/) — Visual novel engine
- [Godot Engine](https://godotengine.org/) — Open source game engine
- [Ruffle](https://ruffle.rs/) — Flash Player emulator

---

## 🐛 Issues & Support

Found a bug or have a feature request? Open an issue on GitHub.

---

## 📜 License

GNU General Public License v2 or later.

See LICENSE for details.
