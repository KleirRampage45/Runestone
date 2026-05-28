# Runestone vs JoiPlay — Comprehensive Gap Analysis

> Generated: 2026-05-27
> **Runestone v0.2.0** (current) vs **JoiPlay 1.21.000** (latest)

---

## 1. Overview

| Feature | Runestone | JoiPlay |
|---------|-----------|---------|
| **Version** | 0.2.0 (early alpha) | 1.21.000 (mature, 5+ years development) |
| **Platform** | Android only | Android only |
| **License** | GPLv2+ | Proprietary (closed source) |
| **First Release** | ~2026 | ~2019 |
| **Community Size** | 1 developer | Large (Patreon, Discord, Reddit 15K+) |
| **Plugin System** | Engine plugins (internal) | Plugin APKs (modular, installable) |
| **Target Audience** | Undefined yet | Casual Android gamers playing PC fangames |

---

## 2. Supported Game Engines — HEAD-TO-HEAD

### 2.1 Currently Supported by Both

| Engine | Runestone | JoiPlay |
|--------|-----------|---------|
| **RPG Maker XP** | ✅ (via mkxp-z intent) | ✅ (RPG Maker Plugin) |
| **RPG Maker VX** | ✅ (via mkxp-z intent) | ✅ (RPG Maker Plugin) |
| **RPG Maker VX Ace** | ✅ (via mkxp-z intent) | ✅ (RPG Maker Plugin) |
| **RPG Maker MV** | ✅ (WebView engine) | ✅ (RPG Maker Plugin) |
| **RPG Maker MZ** | ✅ (WebView engine) | ✅ (RPG Maker Plugin) |
| **Ren'Py** | 🔶 Stub (plugin required) | ✅ (Ren'Py Plugin) |
| **TyranoBuilder** | ✅ (WebView engine) | ✅ (Built-in) |
| **Construct 2/3** | ✅ (WebView engine) | ✅ (Built-in) |

### 2.2 Supported by JoiPlay ONLY (Runestone gaps)

| Engine | JoiPlay Support | Runestone Priority |
|--------|-----------------|-------------------|
| **Godot 3.6** | ✅ (Godot Plugin, added Nov 2024) | ⚠️ Not planned |
| **Godot 4.3** | ✅ (Godot Plugin, added Nov 2024) | ⚠️ Not planned |
| **Flash / Ruffle** | ✅ (Ruffle Plugin) | ⚠️ Not planned |
| **HTML games** | ✅ (Built-in + Crosswalk Plugin) | ⚠️ Not planned |
| **NScripter** | ✅ (Built-in) | ⚠️ Not planned |
| **Twine** | ✅ (Built-in) | ⚠️ Not planned |
| **VN Maker** | ✅ (Built-in) | ⚠️ Not planned |
| **Electron apps** | ✅ (Basic support, added Jun 2024) | ⚠️ Not planned |

### 2.3 What Runestone Supports That JoiPlay Doesn't

| Engine | Why It Matters |
|--------|----------------|
| None unique | JoiPlay covers everything Runestone does and more |

**GAP: 8+ engine categories that JoiPlay supports but Runestone doesn't.**

---

## 3. Plugin Architecture — MAJOR GAP

### 3.1 How JoiPlay Does Plugins

JoiPlay uses a **modular APK plugin system** — each game runtime is a separate installable APK:

| Plugin | What It Runs | Size |
|--------|-------------|------|
| **RPG Maker Plugin** | XP, VX, VX Ace, MV, MZ | ~15-20 MB |
| **Ren'Py Plugin** (8.x + 7.x versions) | Ren'Py visual novels | ~30+ MB |
| **Ruffle Plugin** | Flash/SWF games | ~10 MB |
| **Godot Plugin** (3.6 + 4.3 versions) | Godot games | ~20+ MB |
| **Crosswalk Plugin** | HTML5 games (legacy WebView) | ~20 MB |

**Key advantages of JoiPlay's approach:**
- Users only install what they need
- Each plugin updates independently
- Third parties can author plugins (plugin template exists)
- Core app stays small (~5 MB)
- Plugin detection is automatic during game import

### 3.2 How Runestone Does Engines

Runestone has a **built-in GameEngine interface** with engines registered in `EngineRegistry.initDefaults()`:

```kotlin
EngineRegistry.register(MkxpZEngine())    // XP/VX/VX Ace
EngineRegistry.register(EasyRpgEngine())   // 2000/2003 (STUB)
EngineRegistry.register(WebViewMzEngine()) // MZ
EngineRegistry.register(WebViewMvEngine()) // MV
EngineRegistry.register(TyranoEngine())   // TyranoBuilder
EngineRegistry.register(ConstructEngine()) // Construct 2/3
EngineRegistry.register(RenpyEngine())    // Ren'Py (STUB)
```

**Problems with this approach:**
- All engines bundled in the same APK (bloat)
- No dynamic loading — native .so libraries must be compiled into the APK at build time
- EasyRPG and Ren'Py are **stubs** that crash with "coming soon" toasts
- No native builds for mkxp-z are actually included (build is commented out in build.gradle.kts)
- No way for third parties to add engines
- WebView engines all use system WebView (limited, can break on some devices)

**GAP: Runestone needs a proper plugin APK architecture with runtime discovery.**

---

## 4. Input System — SIGNIFICANT GAP

### 4.1 JoiPlay Input Features

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **Virtual D-Pad** | ✅ Customizable | ✅ Built-in |
| **Action buttons (A/B/X/Y)** | ✅ All 4 + customizable | ✅ (X/Y optional) |
| **Select/Start/Menu** | ✅ All 3 | ✅ All 3 |
| **Button size adjustment** | ✅ Per-game | ✅ Global slider |
| **Button opacity** | ✅ Adjustable | ✅ Adjustable |
| **Layout editing (drag to reposition)** | ✅ Drag & drop controls anywhere | ❌ **NOT IMPLEMENTED** (code has editing stubs) |
| **Per-game layout save** | ✅ Saves per-game button positions | ❌ Only global settings |
| **Multi-touch (hold 2 buttons at once)** | ✅ Full multi-touch | ✅ Basic |
| **Physical controller mapping** | ✅ Built-in "Controller Mapping" button | ❌ **NOT IMPLEMENTED** |
| **Keyboard mapping** | ✅ Custom keymapping via file | ❌ Only software keyboard toggle |
| **Hide virtual gamepad** | ✅ Toggle | ❌ Can't hide |
| **Button preset system** | ✅ Layout presets | ❌ Not implemented |
| **Pinch-to-resize controls** | ✅ Appears to support | ❌ Not implemented |
| **L1/R1 shoulder buttons** | ✅ Remappable | ❌ **NOT IMPLEMENTED** |
| **Turbo / auto-fire** | ✅ | ❌ Not implemented |

### 4.2 Runestone Input Implementation Status

```
TouchOverlayView.kt — 732 lines
  ✅ D-Pad (4 directions + dead zone center)
  ✅ A/B + optional X/Y buttons
  ✅ Select/Start/Settings bar
  ✅ Multi-touch tracking
  ✅ Haptic feedback (intensity adjustable)
  ✅ Visual pressed states
  ❌ Layout editing — code exists but no save mechanism
  ❌ No controller mapping
  ❌ No per-game input config
  ❌ No L1/R1 buttons
```

**GAP: Runestone's input system is functional but lacks JoiPlay's per-game customization, physical controller support, and layout editing.**

---

## 5. Settings & Configuration — SIGNIFICANT GAP

### 5.1 JoiPlay Settings

JoiPlay settings are **per-game** and contain many more options:

#### JoiPlay RPG Maker Plugin Settings
| Setting | JoiPlay | Runestone |
|---------|---------|-----------|
| **Audio extension** (.ogg/.m4a) | ✅ | ✅ |
| **Font size / scaling** | ✅ | ❌ (textScale field exists but unused) |
| **Screen filter** | ✅ | ❌ Not implemented |
| **Screen scaling mode** (integer, smooth, fit) | ✅ Integer + smooth + stretch | ❌ (fields exist but unused) |
| **Force audio to .ogg** | ✅ | ✅ |
| **Optimize Maps** (rebuilds tilemaps) | ✅ Critical feature | ❌ **NOT IMPLEMENTED** |
| **Force Miniz** (for XP/VX/VX Ace) | ✅ | ❌ Not implemented |
| **Video/Skip video** | ✅ | ❌ Not implemented |
| **Text speed** | ✅ | ❌ Not implemented |
| **Battle effects** | ✅ | ❌ Not implemented |
| **Cheat menu injection** | ✅ Built-in | ❌ **NOT IMPLEMENTED** |
| **Debug console** | ✅ | ❌ Not implemented |
| **FPS counter** | ✅ (WebView only) | ✅ (WebView only, on by default) |

#### JoiPlay General Settings
| Setting | JoiPlay | Runestone |
|---------|---------|-----------|
| **Theme/color scheme** | ✅ Multiple themes | ❌ Fixed dark theme only |
| **Wallpaper** | ✅ Custom wallpaper support | ❌ Not implemented |
| **Icon packs** | ✅ | ❌ Not implemented |
| **Language selection** | ✅ | ❌ Fixed English |
| **Developer mode** | ✅ | ❌ Not implemented |

### 5.2 Runestone Settings (current state)

```
RunnerSettings.kt:
- layoutMode: PORTRAIT_CONSOLE | LANDSCAPE | GAMEPAD
- touchOpacity: Float (0.72)
- touchScale: Float (1.0)
- hapticsEnabled: Boolean
- hapticIntensity: Float (0.55)
- showExtraButtons: Boolean (X/Y)
- integerScaling: Boolean ❌ UNUSED
- smoothScaling: Boolean ❌ UNUSED
- textScale: Float ❌ UNUSED
- forceAudioExt: String (".ogg")
```

**GAP: Runestone has 7 actual settings + 3 unused fields vs. JoiPlay's 20+ per-game and global settings.**

---

## 6. Layout Modes — MODERATE GAP

### 6.1 Comparison

| Layout Mode | Runestone | JoiPlay |
|-------------|-----------|---------|
| **Portrait (game above, controls below)** | ✅ Portrait Console | ✅ Standard layout |
| **Landscape (game fullscreen, overlay controls)** | ✅ Landscape | ✅ |
| **Gamepad mode (hide controls)** | ✅ Gamepad (just game) | ✅ (Hide Virtual Gamepad) |
| **Custom layouts (per-game)** | ❌ Not implemented | ✅ Drag & drop repositioning |
| **Layout preview in settings** | ✅ 3 card previews | ✅ Visual previews |

**GAP: Runestone has the 3 basic modes but no per-game layout customization.**

---

## 7. Save File Management — MODERATE GAP

### 7.1 Comparison

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **Cross-platform save import** | ✅ Drag & drop PC saves | ❌ Not implemented |
| **Save file backup** | ✅ Manual save backup | ✅ Automatic during reimport |
| **Save file browsing UI** | ✅ In-app save browser | ✅ Dialog listing |
| **Cloud saves** | ❌ Not built-in | ❌ Not built-in |
| **Export saves** | ✅ Share/export individual saves | ❌ Not implemented |
| **Import saves from ZIP** | ✅ | ❌ Not implemented |

### 7.2 Runestone Save System

```
SaveManager.kt — 108 lines
  ✅ syncFromActive() — backs up saves before reimport
  ✅ restoreToActive() — restores saves after reimport
  ✅ listSaves() — shows saves + live saves in game dir
  ✅ Remove game data with keepSaves option
  ❌ No manual save import/export UI
  ❌ No PC save file compatibility
```

**GAP: Runestone's save system is functional for backup but lacks user-facing save management features.**

---

## 8. Game Import & Library — MODERATE GAP

### 8.1 Comparison

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **SAF folder import** | ✅ (folder picker) | ✅ (folder picker) |
| **Open .exe directly** | ✅ Can open Game.exe | ❌ Must select folder |
| **RTP download prompt** | ✅ Prompts for missing RTP | ❌ Not implemented |
| **Game icon detection** | ✅ Auto-detect from exe | ❌ Shows engine color |
| **Game title parsing** | ✅ From Game.ini/exe | ✅ From Game.ini (mkxp-z) |
| **Search/filter library** | ✅ Search bar | ❌ Not implemented |
| **Game categories/collections** | ✅ | ❌ Not implemented |
| **Recent games** | ✅ | ❌ Not implemented |
| **Per-game settings** | ✅ Override settings per game | ❌ Global settings only |
| **Game info/summary** | ✅ File count, engine, version | ✅ File count, engine |
| **Delete game** | ✅ | ✅ (with save keep option) |
| **Engine override** | ✅ Manual engine selection | ✅ Per-game engine picker |
| **Map optimize on import** | ✅ | ❌ Not implemented |

**GAP: Runestone's library is functional but lacks per-game settings, search, RTP handling, and polish.**

---

## 9. Game Settings (In-Game Overlay) — MAJOR GAP

### 9.1 During Gameplay

JoiPlay has a powerful **in-game settings overlay** accessible during gameplay:

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **In-game settings panel** | ✅ Slide-out overlay | ❌ Toast message only |
| **Speed-up / fast-forward** | ✅ Adjustable (2x, 3x, etc.) | ❌ **NOT IMPLEMENTED** |
| **Toggle cheats** | ✅ Built-in cheat menu | ❌ **NOT IMPLEMENTED** |
| **Save/Load state** | ✅ Quick save/load | ❌ Not implemented |
| **Take screenshot** | ✅ | ❌ Not implemented |
| **Reset game** | ✅ | ❌ Not implemented |
| **Change settings mid-game** | ✅ Opacity, layout, controls | ❌ Must restart |
| **Exit to library** | ✅ Always available | ✅ Via back button |

**GAP: Runestone's in-game experience is bare — no speed-up, no cheats, no quick save.**

---

## 10. Native Runtime Integration — CRITICAL GAP

### 10.1 JoiPlay's Native Approach

JoiPlay's RPG Maker Plugin is a **mature mkxp-z fork** with:
- Custom RGSS interpreter with Android-specific optimizations
- Win32API stubs for RPG Maker system calls
- Font rendering with fallback system
- Ruby 3.1.3 support
- Custom key mapping built into the interpreter
- Optimized tile rendering
- RGSS archive extraction support
- 70% compatibility rate for XP/VX/VX Ace games

### 10.2 Runestone's Native Approach

Runestone's mkxp-z integration is **stub-level**:

```
GameActivity.kt:
  launchRgssGame() -> starts com.hatkid.mkxpz.MainActivity via intent
  
build.gradle.kts:
  // Native build is optional - enable after running setup-native-build.sh
  // externalNativeBuild {
  //     ndkBuild {
  //         path = file("../native/mkxp-z-android/app/jni/Android.mk")
  //     }
  // }
```

The native mkxp-z build is **not currently compiled** into the APK. RGSS games would fail to launch if the submodule's APK isn't installed separately.

**GAP: Runestone has no working native runtime for RGSS (XP/VX/VX Ace) games.**

---

## 11. Cheat System — MAJOR GAP

### 11.1 JoiPlay Cheat Features

- **Built-in cheat menu** for RPG Maker MV/MZ
- Can enable/disable mid-game
- Common cheats: gold, HP, items, level, stats
- Third-party cheat plugin support
- Speed-up/turbo mode (critical for Pokémon fangames)

### 11.2 Runestone Cheat Features

**None implemented.**

**GAP: Complete absence of cheat functionality.**

---

## 12. UI/UX Design — SUBSTANTIVE GAP

### 12.1 Comparison

| UI Feature | JoiPlay | Runestone |
|------------|---------|-----------|
| **Hero cards for games** | ✅ Grid/list view | ✅ Large hero cards |
| **Game art/banner** | ✅ Auto-detect Game.exe icon | ❌ Engine color only |
| **Search bar** | ✅ | ❌ Not implemented |
| **Sort options** | ✅ Name, recent, size | ❌ Alphabetical only |
| **Dark theme** | ✅ | ✅ (only option) |
| **Theme customization** | ✅ Color/wallpaper options | ❌ Fixed dark theme |
| **Material Design** | ✅ Modern UI | ✅ Custom dark gothic style |
| **Animation/transitions** | ✅ Smooth | ✅ Basic |
| **Settings organization** | ✅ Categorized sections | ✅ Categorized sections |
| **Onboarding/welcome** | ✅ First-time setup guide | ❌ Blank state (just says "No games imported yet") |
| **Context menus** | ✅ Long-press options | ❌ Tap + overlay needed |
| **Gamepad config UI** | ✅ Dedicated mapping screen | ❌ Not implemented |

**GAP: Runestone's UI is stylish but missing search, sorting, themes, and onboarding.**

---

## 13. Map Optimization — CRITICAL MISSING FEATURE

### 13.1 What It Is

"Optimize Map" is a JoiPlay feature that **recreates maps and tilesets to reduce tileset height**. This is a workaround for tile rendering issues on Android devices. Many RPG Maker games (especially Pokémon Essentials games) have maps that don't render correctly without this.

### 13.2 Why It Matters

This is one of the most frequently recommended fixes in JoiPlay communities. Without it, many games have broken tile rendering (black tiles, missing sprites, etc.).

**GAP: Runestone has no map optimization at all.**

---

## 14. Font Handling — MODERATE GAP

### 14.1 JoiPlay

- Custom font files loaded from game directory
- Font fallback system for missing characters
- Auto-detect and use game's fonts
- Text scaling option

### 14.2 Runestone

- WebView engines rely on system fonts
- mkxp-z uses whatever fonts the binary supports
- No font management at the launcher level
- `textScale` field exists in `RunnerSettings` but is unused

**GAP: Runestone has no font handling system.**

---

## 15. Audio Handling — COMPARABLE

| Feature | JoiPlay | Runestone |
|---------|---------|-----------|
| **Force audio extension (.ogg/.m4a)** | ✅ | ✅ |
| **Audio format conversion** | ✅ In-plugin | ❌ At WebView network level |
| **Mute toggle** | ✅ | ❌ Not implemented |
| **Volume control** | ✅ System volume | ✅ System volume |

**GAP: Runestone's audio handling is basic but functional for WebView games.**

---

## 16. Summary of Priority Gaps

### URGENT (needed for basic functionality)

| # | Gap | Why |
|---|-----|-----|
| 1 | **Working mkxp-z native build** | RGSS games (XP/VX/VX Ace) are currently unplayable |
| 2 | **Working EasyRPG runtime** | RM2000/2003 support is promised but non-functional |
| 3 | **Working Ren'Py runtime** | Same — stub only |

### HIGH PRIORITY (needed for competitive feature set)

| # | Gap | Why |
|---|-----|-----|
| 4 | **Per-game settings** | Every game needs different input layouts |
| 5 | **Physical controller mapping** | Handheld/gamepad users can't play otherwise |
| 6 | **Map optimization** | Many games have broken tiles without it |
| 7 | **Speed-up / fast-forward** | Critical for fangame replayability |
| 8 | **Plugin APK architecture** | Core app is bloated; native engines can't be added dynamically |
| 9 | **Game art detection** | Cards are empty colored panels — need real artwork |
| 10 | **Search & sort library** | Essential for collections of 10+ games |

### MEDIUM PRIORITY

| # | Gap |
|---|-----|
| 11 | Save file import/export (cross-platform from PC) |
| 12 | In-game settings overlay (opacity, layout without restarting) |
| 13 | Font handling system |
| 14 | Enhanced settings (screen filter, integer scaling, text speed) |
| 15 | Missing engine support: Godot, Flash, NScripter, Twine, VN Maker, Electron |
| 16 | Layout editing (drag controls to reposition) |
| 17 | Cheat menu injection |
| 18 | Per-game button layouts saved to disk |

### NICE TO HAVE

| # | Gap |
|---|-----|
| 19 | Theme/wallpaper customization |
| 20 | RTP download prompts |
| 21 | First-time onboarding |
| 22 | Language selection |
| 23 | Context menus (long-press) |
| 24 | Developer mode / debug console |
| 25 | In-game screenshots |
| 26 | Quick save/load states |
| 27 | L1/R1 shoulder buttons in touch overlay |

---

## 17. Architectural Comparison — What Runestone Does Right

Despite the gaps, Runestone has **some architectural advantages**:

| Aspect | Runestone | JoiPlay |
|--------|-----------|---------|
| **Open source** | ✅ GPLv2+ | ❌ Closed source |
| **License** | ✅ GPLv2+ — anyone can fork/contribute | ❌ Proprietary |
| **Modular engine design** | ✅ Clean `GameEngine` interface | ✅ Plugin APKs |
| **Kotlin (modern Android)** | ✅ Kotlin, no XML layouts | ✅ Kotlin |
| **Single-copy storage** | ✅ No file duplication | ❌ (Users report duplication issues) |
| **Save protection** | ✅ Protected saves/ directory | ❌ (Saves live in game dir) |
| **Code quality** | ✅ Well-structured, clean code | ❌ (Reportedly messy internals) |

---

## 18. Feature Count Summary

| Category | Runestone | JoiPlay | Gap |
|----------|-----------|---------|-----|
| Supported Engines | 7 (1 functional native) | 15+ | 8 engines |
| Settings (global + per-game) | ~7 | ~25+ | 18+ |
| Input Options | 10 | 20+ | 10+ |
| UI Features | 6 | 14+ | 8+ |
| Save Management | 4 | 7+ | 3+ |
| Plugin Architecture | No | Yes (6 plugins) | Full gap |
| In-Game Features | 1 | 6+ | 5+ |
| Cheats | 0 | Yes | Full gap |
| Map Optimization | 0 | Yes | Full gap |

---

## 19. Recommended Roadmap

### Phase A — Core Functionality (1-2 weeks)
1. ✅ Build mkxp-z native .so into the APK (uncomment ndkBuild)
2. Implement EasyRPG Player native build
3. Create a minimal Ren'Py plugin system

### Phase B — Feature Parity (2-4 weeks)
4. Per-game settings storage (JSON per game)
5. Physical controller mapping UI + detection
6. Map optimization tool (inject simplified tilesets)
7. Speed-up toggle (via JS injection for WebView, Ruby for mkxp-z)

### Phase C — Polish (4-8 weeks)
8. Plugin APK architecture (separate installable runtime APKs)
9. Game art extraction (from Game.exe or Game.ini metadata)
10. Search bar + sort options
11. Save file import/export

### Phase D — Expansion (8+ weeks)
12. In-game settings overlay
13. Cheat menu injection
14. Godot plugin
15. Theme customization
16. Cross-platform save compatibility
