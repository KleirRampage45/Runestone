# Runestone vs JoiPlay — Comprehensive Gap Analysis

> **Last Updated:** 2026-06-01
> **Runestone v0.6.13** (`refactor/ui-complete`, branch `refactor/ui-complete`) vs **JoiPlay 1.21.000** (latest)
> **Note:** This document supersedes the v0.2.0 snapshot. Massive work has been done since the original analysis.

---

## 1. Overview

| Feature | Runestone | JoiPlay |
|---------|-----------|---------|
| **Version** | 0.6.13 (rapid development, 43 commits) | 1.21.000 (mature, 5+ years development) |
| **Platform** | Android only | Android only |
| **License** | GPLv2+ — fully open source | Proprietary (closed source) |
| **First Release** | ~May 2026 | ~2019 |
| **Community Size** | 1 developer | Large (Patreon, Discord, Reddit 15K+) |
| **Plugin System** | Engine plugins (internal, built-in) | Plugin APKs (modular, installable) |
| **APK Size** | ~223 MB (15 native .so files bundled) | ~5 MB core + plugins downloaded separately |
| **Target Audience** | Open-source gaming community | Casual Android gamers playing PC fangames |

---

## 2. Supported Game Engines — HEAD-TO-HEAD

### 2.1 Currently Supported by Both

| Engine | Runestone | JoiPlay | Runestone Notes |
|--------|-----------|---------|-----------------|
| **RPG Maker XP** | ✅ Working (mkxp-z native) | ✅ (RPG Maker Plugin) | Bundled .so, direct activity launch |
| **RPG Maker VX** | ✅ Working (mkxp-z native) | ✅ (RPG Maker Plugin) | Same mkxp-z runtime |
| **RPG Maker VX Ace** | ✅ Working (mkxp-z native) | ✅ (RPG Maker Plugin) | Same mkxp-z runtime |
| **RPG Maker 2000** | ✅ Working (EasyRPG native) | ✅ (RPG Maker Plugin) | Bundled .so, full JNI wrapper |
| **RPG Maker 2003** | ✅ Working (EasyRPG native) | ✅ (RPG Maker Plugin) | Same EasyRPG runtime |
| **RPG Maker MV** | ✅ Working (WebView engine) | ✅ (RPG Maker Plugin) | PIXI fixes, audio fallback |
| **RPG Maker MZ** | ✅ Working (WebView engine) | ✅ (RPG Maker Plugin) | Same WebView engine |
| **TyranoBuilder** | ✅ Working (WebView) | ✅ (Built-in) | Detection + WebView launch |
| **Construct 2/3** | ✅ Working (WebView) | ✅ (Built-in) | c2runtime.js/c3runtime.js detection |

### 2.2 What Runestone Supports That the Gap Doc v0.2.0 Said Were Missing

| Engine | Old Claim | Current Status |
|--------|-----------|----------------|
| **Ren'Py** | ❌ "Stub, crash toast" | ⚠️ **Detection working, .so bundled (55MB), saves work.** Launch shows "Coming Soon" dialog (needs Android activity wrapper) |
| **Godot 3/4** | ❌ "Not planned" | ⚠️ **Detection working, .so bundled (142MB).** Same — needs activity wrapper |
| **Flash / Ruffle** | ❌ "Not planned" | ✅ **Fully implemented** — generates HTML with ruffle.js CDN, launches in WebView |
| **NScripter/ONScripter** | ❌ Not mentioned | ⚠️ **Detection working, libonscripter.so (2.3MB) + libsdl.so bundled.** Shows "Coming Soon" |
| **Twine** | ❌ "Not planned" | ✅ **Full detection** (tw-storydata marker in HTML), WebView launch |
| **VN Maker** | ❌ "Not planned" | ✅ **Full detection** (index.html + data/ + .json), WebView launch |
| **HTML5 generic** | ❌ Not mentioned | ✅ Full detection + WebView launch |
| **Electron** | ❌ Not mentioned | ⚠️ Detection works, shows "Not Supported on Android" dialog |

### 2.3 Legacy Engines (Detect Only — No Open-Source Runtime Exists)

| Engine | Runestone | JoiPlay | Notes |
|--------|-----------|---------|-------|
| **RPG Maker 95** | 🔍 Detect only | Likely none | 1997 Japanese-only, no runtime |
| **Dante 98** | 🔍 Detect only | Likely none | 1992 precursor, no runtime |

### 2.4 Summary

| Category | The Gap Doc Said | Actual Count |
|----------|-----------------|--------------|
| Working engines | 7 (3 stubs) | **10 fully working** (mkxp-z, EasyRPG, MV, MZ, Tyrano, Construct, HTML, Twine, VN Maker, Ruffle) |
| Detect-only with .so bundled | — | **4** (Ren'Py, Godot 3/4, ONScripter) |
| Detect-only (info dialogs) | — | **3** (Electron, RM95, Dante98) |
| Total engines recognized | 7 | **16** |
| Working native runtimes | 0 (stub) | **2** (mkxp-z + EasyRPG, both with bundled .so) |

---

## 3. Plugin Architecture

### 3.1 Current Runestone Approach

Runestone bundles **all engine runtimes directly in the APK** via `jniLibs/arm64-v8a/`. The 15 native .so files are:

| Library | Size | Purpose |
|---------|------|---------|
| `libmkxp-z.so` | ~12 MB | RGSS interpreter (XP/VX/VX Ace) |
| `libruby.so` | ~12 MB | Ruby interpreter for RGSS |
| `libeasyrpg_android.so` | ~15 MB | RM2000/2003 runtime |
| `libgodot_android.so` | **142 MB** | Godot Engine runtime |
| `librenpython.so` | **55 MB** | Ren'Py Python runtime |
| `libonscripter.so` | 2.3 MB | ONScripter runtime |
| `libsdl.so` | bundled | SDL for ONScripter |
| `libSDL2.so` | std | SDL2 core |
| `libSDL2_image.so` | std | SDL2 image |
| `libSDL2_ttf.so` | std | SDL2 fonts |
| `libSDL2_sound.so` | std | SDL2 sound |
| `libopenal.so` | std | OpenAL audio |
| `libc++_shared.so` | std | C++ runtime |
| `libc++_shared_godot.so` | std | Godot's C++ runtime |
| `libgamebrowser.so` | bundled | EasyRPG game browser |

**Benefits of bundling everything:**
- Zero downloads, zero plugin hunting — install and play
- Works offline
- No compatibility issues between plugin versions

**Downsides:**
- 223 MB APK (bulky for users who only want one engine)
- No third-party plugin system
- Every new runtime compiles into the same APK
- Can't add engines without app update

### 3.2 JoiPlay's Approach

JoiPlay uses a **modular APK plugin system** — each game runtime is a separate installable APK:

| Plugin | Size |
|--------|------|
| RPG Maker Plugin | ~15-20 MB |
| Ren'Py Plugin (8.x + 7.x) | ~30+ MB |
| Ruffle Plugin | ~10 MB |
| Godot Plugin (3.6 + 4.3) | ~20+ MB |
| Crosswalk Plugin | ~20 MB |

**Benefits:** Small core app (5 MB), per-plugin updates, third-party authoring.

**This gap remains:** Runestone has no plugin APK architecture. Everything is monolithic.

---

## 4. Input System

### 4.1 Feature Comparison

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **Virtual D-Pad** | ✅ Customizable | ✅ Built-in |
| **Action buttons (A/B)** | ✅ All 4 + customizable | ✅ A/B with optional X/Y |
| **Select/Start/Menu** | ✅ All 3 | ✅ All 3 (Settings button too) |
| **Button size adjustment** | ✅ Per-game | ✅ Global slider only |
| **Button opacity** | ✅ Adjustable | ✅ Adjustable |
| **Layout editing (drag to reposition)** | ✅ Drag & drop anywhere | ❌ Not implemented |
| **Per-game layout save** | ✅ Saves per-game positions | ❌ Global settings only |
| **Multi-touch** | ✅ Full | ✅ Basic |
| **Physical controller mapping** | ✅ Built-in UI | ❌ Not implemented |
| **Keyboard mapping** | ✅ Custom keymapping file | ✅ Software keyboard toggle |
| **Hide virtual gamepad** | ✅ Toggle | ✅ Gamepad mode hides it |
| **Button preset system** | ✅ Layout presets | ❌ Not implemented |
| **Pinch-to-resize controls** | ✅ | ❌ Not implemented |
| **L1/R1 shoulder buttons** | ✅ | ❌ Not implemented |
| **Turbo / auto-fire** | ✅ | ❌ Not implemented |

### 4.2 Runestone Input — Current State

`TouchOverlayView.kt` supports:
- ✅ D-Pad (4 directions + dead zone center)
- ✅ A/B + optional X/Y buttons
- ✅ Select/Start/Settings bar
- ✅ Multi-touch tracking
- ✅ Haptic feedback (intensity adjustable)
- ✅ Visual pressed states
- ✅ Opacity + scale global controls
- ✅ Gamepad mode (no overlay)
- ❌ No layout editing/drag repositioning
- ❌ No controller mapping
- ❌ No per-game input config
- ❌ No L1/R1 buttons

**Gap:** Input is functional but lacks per-game customization, physical controller support, and layout editing.

---

## 5. Settings & Configuration

### 5.1 Runestone Current Settings

**Global settings** (RunnerSettings):
- `layoutMode`: PORTRAIT_CONSOLE | LANDSCAPE | GAMEPAD
- `touchOpacity`: Float (0.72)
- `touchScale`: Float (1.0)
- `hapticsEnabled`: Boolean
- `hapticIntensity`: Float (0.55)
- `showExtraButtons`: Boolean (X/Y)
- `forceAudioExt`: String (.ogg)

**Per-game settings:**
- Engine override in Manage Files screen
- Dedicated per-game settings screen exists (`feature/phase1-pergame-config` branch)

### 5.2 Gaps vs JoiPlay

| Setting | JoiPlay | Runestone |
|---------|---------|-----------|
| **Audio extension** (.ogg/.m4a) | ✅ | ✅ |
| **Font size / scaling** | ✅ | ❌ Not implemented |
| **Screen filter** | ✅ | ❌ Not implemented |
| **Screen scaling mode** | ✅ Integer + smooth + stretch | ❌ Not implemented |
| **Force audio to .ogg** | ✅ | ✅ |
| **Optimize Maps** | ✅ Critical feature | ❌ NOT IMPLEMENTED |
| **Force Miniz** (XP/VX/VX Ace) | ✅ | ❌ Not implemented |
| **Video/Skip video** | ✅ | ❌ Not implemented |
| **Text speed** | ✅ | ❌ Not implemented |
| **Battle effects** | ✅ | ❌ Not implemented |
| **Cheat menu injection** | ✅ Built-in | ❌ NOT IMPLEMENTED |
| **Debug console** | ✅ | ❌ Not implemented |
| **FPS counter** | ✅ (WebView only) | ✅ (WebView only, on by default) |
| **Theme/color scheme** | ✅ Multiple themes | ❌ Fixed dark theme only |
| **Language selection** | ✅ | ❌ Fixed English |

**Gap:** Runestone has ~7 active settings vs JoiPlay's 20+. Per-game settings branch exists but isn't merged.

---

## 6. In-Game Experience

### 6.1 During Gameplay

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **In-game settings overlay** | ✅ Slide-out panel | ⚠️ Toast message only |
| **Speed-up / fast-forward** | ✅ Adjustable (2x, 3x, etc.) | ❌ NOT IMPLEMENTED |
| **Toggle cheats** | ✅ Built-in cheat menu | ❌ NOT IMPLEMENTED |
| **Save/Load state** | ✅ Quick save/load | ❌ Not implemented |
| **Take screenshot** | ✅ | ❌ Not implemented |
| **Reset game** | ✅ | ❌ Not implemented |
| **Change settings mid-game** | ✅ Opacity, layout, controls | ❌ Must restart |
| **Exit to library** | ✅ Always available | ✅ HOME button + RESUME later |
| **Keyboard toggle** | ✅ | ✅ KBD button |
| **STOP game** | ✅ | ✅ With confirmation dialog |
| **Pause/resume** | ✅ | ✅ Paused game state preserved |

**Gap:** Runestone's in-game experience is basic — HOME, KBD, and a toast for settings but no speed-up, no cheats, no quick save/load.

---

## 7. Save File Management

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **Cross-platform save import** | ✅ Drag & drop PC saves | ❌ Not implemented |
| **Save file backup** | ✅ Manual backup | ✅ Auto-backup before reimport |
| **Save file browsing UI** | ✅ In-app save browser | ✅ Dialog listing |
| **Cloud saves** | ❌ Not built-in | ❌ Not built-in |
| **Export saves** | ✅ Share/export | ❌ Not implemented |
| **Import saves from ZIP** | ✅ | ❌ Not implemented |

`SaveManager.kt` handles backup/restore during reimport, `listSaves()` enumerates save files for mkxp-z (.rxdata, .rvdata, .rvdata2), EasyRPG (.lsd), and Ren'Py (.save, .rpy-save) engines.

**Gap:** Functional for backup but lacks user-facing import/export features.

---

## 8. Game Library & Management

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **SAF folder import** | ✅ Folder picker | ✅ Folder picker |
| **Store / game catalogue** | ❌ | ✅ **Store system** with download + extract + install pipeline |
| **Game icon detection** | ✅ Auto-detect from exe | ❌ Engine color only |
| **Game title parsing** | ✅ From Game.ini/exe | ✅ From Game.ini (mkxp-z) / System.json (MV/MZ) |
| **Search/filter** | ✅ Search bar | ✅ Sort (A→Z, Z→A, recent, date) + glassmorphism filter overlay |
| **Game categories/collections** | ✅ | ❌ Not implemented |
| **Recent games** | ✅ | ❌ Not implemented |
| **Per-game settings** | ✅ Override per game | ✅ Engine override exists, dedicated per-game config branch |
| **Game info/summary** | ✅ File count, engine, version | ✅ File count, engine |
| **Delete game** | ✅ | ✅ With save-keep option |
| **Engine override** | ✅ Manual selection | ✅ Per-game engine picker |
| **Map optimize on import** | ✅ | ❌ Not implemented |
| **RTP download prompt** | ✅ | ❌ Not implemented |
| **RESUME bar** | ❌ | ✅ Custom glassmorphism RESUME banner above dock |

**Runestone has a unique feature JoiPlay lacks:** A **game store** with provider system (static JSON catalogue, download from Pixeldrain/Mediafire/etc., extract, and auto-detect engine).

**Gap (still real):** No game art, no categories, no recent games, no RTP handling, no map optimization.

---

## 9. Native Runtime Integration

### 9.1 mkxp-z (RPG Maker XP/VX/VX Ace) — ✅ Full Working

- `libmkxp-z.so` (GPLv2+) bundled in APK
- `EasyRpgPlayerActivity` extends `com.hatkid.mkxpz.MainActivity`
- Launches via Intent with full settings passthrough (layout, opacity, haptics, audio)
- Save detection: .rxdata (XP), .rvdata (VX), .rvdata2 (VX Ace)
- Title detection from Game.ini

### 9.2 EasyRPG (RPG Maker 2000/2003) — ✅ Full Working

- `libeasyrpg_android.so` (GPLv3) extracted from v0.8.1 APK
- Full JNI wrapper: `getAssetManager()`, `getArguments()`, `getRtpPath()`, keyboard replacement
- Passes `--project-path`, `--config-path`, `--save-path`, `--log-file` CLI args
- **5 iterations of crash fixes** (v0.6.2→v0.6.5): JNI methods, SDL2 ABI conflict, keyboard input, portrait mode
- Save detection: Save01.lsd, Save02.lsd, etc.

### 9.3 Ren'Py — ⚠️ .so Bundled, Wrapper Missing

- `librenpython.so` (55MB, MIT) is **in the APK**
- Detection fully implemented (renpy/ folder, .rpy files, VERSION.txt, config.name parsing)
- `getSaves()` fully implemented (.save, .rpy-save files)
- **Launch shows "Coming Soon" dialog** — needs Android activity class with SDL2 bridge

### 9.4 Godot — ⚠️ .so Bundled, Wrapper Missing

- `libgodot_android.so` (**142MB**, MIT) is **in the APK**
- Detection implemented (project.godot, .pck files)
- Title parsing from project.godot config entries
- **Launch shows "Coming Soon" dialog**

### 9.5 ONScripter — ⚠️ .so Bundled, Wrapper Missing

- `libonscripter.so` (2.3MB) + `libsdl.so` bundled
- Detection implemented (nscript.dat, .nsa, numbered .txt)
- **Launch shows "Coming Soon" dialog**

---

## 10. Store / Catalogue System

**This is a feature Runestone has that JoiPlay doesn't.**

| Feature | Status |
|---------|--------|
| Static JSON catalogue | ✅ Works |
| Pixeldrain downloads | ✅ Working |
| Mediafire downloads | ⚠️ Only works on desktop UA (blocked on mobile) |
| Rootz downloads | ✅ Working |
| FuckingFast downloads | ✅ Working |
| ZIP extraction with progress | ✅ Working |
| Game detection after install | ✅ Auto-detect engine type |
| Single-copy storage | ✅ Games served from original/, saves in saves/ |

**Pitfall found:** Mediafire HTML-parsing fails on Android (mobile page lacks CDN links). All 12 games in the default catalogue use Mediafire — needs migration to Pixeldrain.

---

## 11. Map Optimization — CRITICAL GAP

JoiPlay's "Optimize Map" recreates tilesets to reduce tileset height. Many Pokémon fangames have broken tile rendering without it.

**Runestone does not implement this.** This is one of the most requested features in the JoiPlay community.

---

## 12. Font Handling

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| Custom font loading from game dir | ✅ | ❌ Not implemented |
| Font fallback for missing chars | ✅ | ❌ (Relies on system fonts) |
| Text scaling | ✅ | ❌ (textScale field exists but unused) |

---

## 13. Audio Handling

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| Force audio extension (.ogg/.m4a) | ✅ | ✅ |
| Audio format conversion | ✅ In-plugin | ❌ WebView only |
| Mute toggle | ✅ | ❌ Not implemented |
| Volume control | ✅ System volume | ✅ System volume |

---

## 14. UI/UX

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| Hero cards | ✅ Grid/list | ✅ Large hero cards |
| Game art/banner | ✅ Auto from exe | ❌ Engine color only |
| Search bar | ✅ | ⚠️ Sort + filter search in glass overlay |
| Sort options | ✅ | ✅ A→Z, Z→A, recent, date added |
| Dark theme | ✅ | ✅ Glassmorphism (only option) |
| Theme customization | ✅ Color/wallpaper | ❌ Fixed dark glass |
| Material Design | ✅ Modern UI | ✅ Custom dark glass gothic |
| Animation/transitions | ✅ Smooth | ✅ Tap bounce, slide, fade |
| Settings organization | ✅ Categorized | ✅ Categorized |
| Onboarding/welcome | ✅ Setup guide | ❌ Empty state "No games imported yet" |
| Detail panel | ❌ | ✅ Game metadata, engine info, file count |
| 3D carousel layout | ❌ | ✅ Scroll effects, bloom, grain, DOF |
| RESUME bar | ❌ | ✅ Glass banner with RESUME + STOP |
| Glassmorphism | ❌ | ✅ Full glass design system |
| Dock bar (always visible) | ❌ | ✅ Glass dock with sort, filter, store, settings |

**Runestone's UI is more advanced than JoiPlay's in some areas** (glassmorphism, 3D carousel effects, dock bar, store), but lacks the basics JoiPlay has (game art, themes, onboarding, search).

---

## 15. Summary of Remaining Gaps

### URGENT (blocking basic functionality — these are .so-level gaps)

| # | Gap | Status |
|---|-----|--------|
| 1 | **Ren'Py activity wrapper** | .so (55MB) bundled, detection works, saves work. Need an Android Activity that loads `librenpython.so` appropriately |
| 2 | **Godot activity wrapper** | .so (142MB) bundled. Need same — activity wrapper to launch Godot games |
| 3 | **ONScripter activity wrapper** | .so (2.3MB) bundled. Need jni bridge to libonscripter |

### HIGH PRIORITY (needed for competitive feature set)

| # | Gap | Why |
|---|-----|-----|
| 4 | **Per-game settings** (merged, not branch) | Every game needs different input/settings |
| 5 | **Physical controller mapping** | Bluetooth gamepad users can't play without it |
| 6 | **Map optimization** | Many games have broken tiles without it — critical for Pokémon fangames |
| 7 | **Speed-up / fast-forward** | Essential for fangame replayability |
| 8 | **Mediafire → Pixeldrain migration** | Store is broken for all 12 default games on real devices |
| 9 | **Game art detection** | Cards are empty colored panels |
| 10 | **Search bar** (text search in library) | Sort + filter overlay exists but no standalone text search |

### MEDIUM PRIORITY

| # | Gap | Notes |
|---|-----|-------|
| 11 | Plugin APK architecture | Currently 223MB monolithic APK |
| 12 | Save file import/export (PC ↔ Android) | |
| 13 | In-game settings overlay (opacity, layout without restart) | Currently just a Toast |
| 14 | Font handling system | textScale exists but unused |
| 15 | Enhanced settings (screen filter, integer scaling, text speed) | |
| 16 | Layout editing (drag controls to reposition) | |
| 17 | Cheat menu injection | |
| 18 | Per-game button layout saved to disk | |
| 19 | Map optimizer tool | |

### NICE TO HAVE

| # | Gap | Notes |
|---|-----|-------|
| 20 | Theme/wallpaper customization | |
| 21 | RTP download prompts | |
| 22 | First-time onboarding | |
| 23 | Language selection | |
| 24 | Context menus (long-press) | |
| 25 | Developer mode / debug console | |
| 26 | In-game screenshots | |
| 27 | Quick save/load states | |
| 28 | L1/R1 shoulder buttons in touch overlay | |
| 29 | Turbo / auto-fire | |

---

## 16. Architectural Comparison — What Runestone Does Right

| Aspect | Runestone | JoiPlay |
|--------|-----------|---------|
| **Open source** | ✅ GPLv2+ — anyone can fork/contribute | ❌ Closed source |
| **Engine design** | ✅ Clean `GameEngine` interface, 16 engine types | ✅ Plugin APKs |
| **Code quality** | ✅ Well-structured Kotlin, no XML layouts | ❌ (Reportedly messy internals) |
| **Single-copy storage** | ✅ No file duplication (original/ + saves/) | ❌ (Users report duplication issues) |
| **Save protection** | ✅ Protected saves/ directory, auto-backup/reimport | ❌ (Saves live in game dir) |
| **Game store** | ✅ Static JSON catalogue + download pipeline | ❌ No built-in store |
| **UI design** | ✅ Custom glassmorphism, 3D carousel effects | ✅ Material Design |
| **All runtimes bundled** | ✅ 223MB, zero downloads, works offline | ⚠️ Must install plugins separately |
| **RESUME bar** | ✅ Glass banner with RESUME/STOP | ❌ No pause state |
| **Detail panel** | ✅ Per-game metadata panel | ❌ |

---

## 17. Feature Count Summary (Updated)

| Category | Runestone v0.2.0 (old doc) | Runestone v0.6.13 (current) | JoiPlay | Remaining Gap |
|----------|---------------------------|----------------------------|---------|---------------|
| Working Engines | 4 + 3 stubs | **10** working + 4 detect-ready | 15+ | 5 incomplete wrappers |
| Settings | ~7 | ~7 (branch adds per-game) | ~25+ | 18+ |
| Input Options | 10 | 10 (no new features) | 20+ | 10+ |
| UI Features | 6 | **12+** (carousel, dock, store, detail panel, glass effects) | 14+ | 2 (art, search) |
| Save Management | 4 | 4 (no change) | 7+ | 3+ |
| Plugin Architecture | No | No | Yes | Full gap |
| In-Game Features | 1 | **3** (HOME, KBD, STOP) | 6+ | 3+ (speed, cheat, overlay) |
| Cheats | 0 | 0 | Yes | Full gap |
| Map Optimization | 0 | 0 | Yes | Full gap |
| Game Store | 0 | **Yes** (catalogue + download) | No | Runestone advantage |
| Glassmorphism UI | 0 | **Yes** (design system) | No | Runestone advantage |

---

## 18. Recommended Roadmap

### Phase A — Finish Activity Wrappers (next priority)

These are low-hanging fruit — the .so files are already bundled, just need Android Activity classes:

1. **Ren'Py activity wrapper** — 55MB librenpython.so already waiting
2. **Godot activity wrapper** — 142MB libgodot_android.so already waiting
3. **ONScripter** — 2.3MB libonscripter.so already waiting

### Phase B — Feature Parity (core gaps)

4. **Merge per-game settings** branch into main
5. **Physical controller mapping UI** (branch exists: `feature/phase7-controller-saves`)
6. **Speed-up toggle** (JS injection for WebView, Ruby for mkxp-z)
7. **Migrate default catalogue from Mediafire to Pixeldrain**

### Phase C — Polish & Missing Features

8. Game art extraction (from Game.exe icons or game folder thumbnails)
9. Text search bar in library
10. In-game settings overlay (change opacity/layout without restarting)
11. Map optimization tool
12. Font handling (custom font loading + fallback)

### Phase D — Expansion

13. Plugin APK architecture (modular runtimes)
14. Cheat menu injection
15. Theme/wallpaper customization
16. Save file import/export (PC compatibility)
17. L1/R1 shoulder buttons
18. First-time onboarding flow

---

*Built with Kotlin. No XML. GPLv2+. Updated 2026-06-01 — branch `refactor/ui-complete`, v0.6.13.*
