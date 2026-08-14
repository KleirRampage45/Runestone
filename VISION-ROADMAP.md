# Runestone — Vision & Roadmap

> **Updated:** 2026-06-20
> **Version:** v0.8.7 (code 35) — 82 MB default APK, 13 working engines
> **Branch:** `develop`

This document is the **current** view of the project. Superseded plans and
historical snapshots live in `docs/archive/`.

---

## Current Status (v0.8.7)

| Area | Status | Where |
|------|--------|-------|
| **mkxp-z Native (XP/VX/VX Ace)** | Working — NDK r27 build | `app/src/main/jniLibs/arm64-v8a/libmkxp-z.so` |
| **EasyRPG (2000/2003)** | Working — bundled .so, full JNI bridge | `engine/EasyRpgEngine.kt` + `EasyRpgPlayerActivity` |
| **ONScripter (NScripter)** | Working — bundled .so, full JNI bridge | `engine/OnscripterEngine.kt` + `OnscripterActivity` |
| **MV / MZ (WebView)** | Working — PIXI fixes, audio fallback, keyboard | `engine/WebViewMvEngine.kt` / `WebViewMzEngine.kt` |
| **TyranoBuilder, Construct, HTML, Twine, VN Maker** | Working — WebView | `engine/` |
| **Flash (Ruffle)** | Working — ruffle.js CDN | `engine/RuffleEngine.kt` |
| **Ren'Py** | Working — `librenpython.so` bundled, `PythonSDLActivity` wrapper | `engine/RenpyEngine.kt` |
| **Godot** | Optional — plugin APK proof of concept; `isPluginInstalled()` detection | `engine/GodotEngine.kt` + `godot-plugin/` |
| **Virtual Controls** | D-pad + A/B/X/Y + L1/R1 + SELECT/START/SETTINGS | `input/TouchOverlayView.kt` |
| **Layout Modes** | Portrait / Landscape; runtime controls toggle in-game | Merged in `develop` |
| **Runtime Menu** | Slide-out menu with controls / layout / pause / stop | `runtime/WebViewGameSession.kt` |
| **Control Profiles + Layout Editor** | Per-game profile storage + drag-edit | `input/ControlProfileStore.kt` |
| **Save Protection** | Auto-backup before reimport, auto-restore | `workspace/SaveManager.kt` |
| **Patch System** | Sparse in-place patches with per-file backups | `docs/SPARSE-PATCH-WORKSPACE.md` |
| **RTP Installer** | Auto-detects VX Ace, downloads official RTP, extracts via `libinnoextract_jni.so` | `rtp/RtpInstaller.kt` |
| **Game Store** | Static JSON catalogue (Pixeldrain + archive.org mirrors) | `provider/SourcesManager.kt` |
| **Per-game Settings** | JSON-based layered config (game, input, video, audio, performance, cheats, fonts) | `data/PerGameConfig.kt` + `data/GameConfigService.kt` |
| **Search / Filter / Sort** | Standalone search bar in sticky header + filter/sort overlay | `ui/HomeScreen.kt` |
| **RESUME bar** | Glass banner with RESUME / STOP | `ui/HomeScreen.kt` |
| **Glassmorphism UI** | Dock, filter overlay, stop dialog, card effects | `ui/` |
| **Detail Panel** | Per-game metadata panel with engine info | `ui/HomeScreen.kt` |
| **Adaptive Icon** | From user's `icon.png` | `mipmap-anydpi-v26/` |
| **Visual Filters** | Post-processing chain (Phase 1, native lib) | `feature/visual-filters` (not yet on `develop`) |
| **MainActivity** | Refactored from 2489 to 664 lines. Delegates to `OverlayNavigationController`, `StoreCoordinator`, `ImportManager`, `GameSessionManager` | `navigation/`, `store/`, `importer/`, `session/` |
| **GameActivity** | Refactored from 1258 to 373 lines. Delegates to `WebViewGameSession`, `NativeGameLauncher`, `InputDispatcher` | `runtime/`, `engine/NativeGameLauncher.kt`, `input/` |
| **Coroutines** | Replaced all `Thread{}.start()` with `AppScope.io.launch` | `util/AppScope.kt` |
| **i18n** | English, Español, Português. `I18n.get()` helper + `attachBaseContext()` locale apply | `res/values{-es,-pt}/strings.xml`, `util/I18n.kt` |
| **Theme System** | `ThemeProvider` singleton, `ThemeColors` DARK/LIGHT, toggle in Settings | `ui/theme/` |
| **Hero Card Art** | RAWG API scrape + `CoverExtractor` fallback (Title.png, www/img/titles1/, .rpgmvp) | `services/GameMetadataService.kt`, `services/CoverExtractor.kt` |
| **Onboarding Wizard** | 4-step first-launch: language, engines, RAWG key, RTP | `ui/OnboardingScreen.kt` |
| **Plugin APK** | `godot-plugin` module with intent-filter; `isPluginInstalled()` detection | `godot-plugin/`, `engine/EngineRegistry.kt` |
| **Unit Tests** | 22 tests for EngineRegistry + WorkspaceManager + CoverExtractor | `app/src/test/` |
| **Language Parity Rule** | AGENTS.md mandates i18n for every new UI string | `AGENTS.md` |

---

## Active Branches (WIP)

| Branch | Purpose | Next Step |
|--------|---------|-----------|
| `feat/mkxpz-controller-overhaul` | Native RTP path resolution + visual filter pipeline in `libmkxp-z.so` | Awaiting integration into `develop` |
| `fix/runtime-menu-native-layout-polish` | v0.8.3 — innoextract JNI static NDK build + runtime menu polish | **Rebase onto develop before merge.** |
| `feature/visual-filters` | Visual filter Phase 1 — data model, settings UI, native pipeline | Fast-forwardable; small change set |

Stale `feature/phase0..10-*` branches remain in the local repo as historical
artifacts. Their content is already on `develop` via the merge chain.

---

## Shipped in v0.8.x

- mkxp-z native build (bundled, NDK r27)
- ONScripter wrapper (full)
- EasyRPG fixes (config path, mkdirs, CLI args, JNI methods)
- Per-game settings merge (`PerGameConfig` + `GameConfigService`)
- Store: Pixeldrain + archive.org catalogue
- Save import/export and patch hardening
- Auto-RTP install for VX Ace
- innoextract JNI (no more `execve`)
- Per-game runtime controls
- Cutout safe area + controller combos
- Fullscreen gamepad shell
- Lazy controller navigation
- Performance regression fix (fullscreen + home render)
- Touch focus regression fix
- MainActivity refactor (2489 → 664 lines)
- GameActivity refactor (1258 → 373 lines)
- Coroutines migration (Thread → AppScope.io.launch)
- i18n: English, Español, Português with runtime locale switching
- ThemeProvider with DARK/LIGHT/SYSTEM modes
- Hero card art: RAWG API + CoverExtractor fallback
- Onboarding wizard (language, engines, RAWG key, RTP)
- Plugin APK proof of concept (`godot-plugin`)
- 22 unit tests (EngineRegistry, WorkspaceManager, CoverExtractor)

---

## Remaining Gaps

- **Carousel polish** — occasional scroll-snap glitch; 3D transitions can
  stutter on very fast scrolls.
- **In-game settings overlay** — engine-level settings still require
  leaving the game; runtime menu covers layout and pause only.
- **Per-game controller profiles UI** — drag-to-edit exists but not wired
  into settings flow.
- **Visual filter Phase 1** — exists on `feature/visual-filters` branch,
  not yet merged to `develop`.

---

## Architecture To-Do

- [ ] **Room database** — Replace SharedPreferences (play stats, game
      cache, settings) with SQLite via Room.
- [ ] **Jetpack Compose migration** — Progressive, starting with
      SettingsScreen (~1400 lines of `addView`).
- [ ] **ViewModel + StateFlow** — Reactive state instead of
      `runOnUiThread` + `refreshGames()`.
- [ ] **CI/CD** — GitHub Actions: lint → build → test → APK artifact.
- [ ] **Dependency Injection** — Hilt/Koin to stop instantiating
      everything in `onCreate`.
- [ ] **Scoped storage** — Migrate from `filesDir/games/` to
      `MediaStore` or persistent SAF for Android 14+.
- [ ] **Game save cloud backup** — Optional Google Drive / Dropbox API.
- [ ] **Home screen widget** — Shortcut to resume last played game.
- [ ] **Font fallback system** — For non-Latin characters in WebView
      games.
- [ ] **Visual filter Phases 2+** — Bloom, grain, DOF, chromatic
      aberration, ambient occlusion.
- [ ] **Plugin APK system** — Split core (~15 MB) from engine plugin APKs.
- [ ] **Migrate screens to ThemeProvider** — Each screen still has
      hardcoded `Color.rgb()` calls.

---

## FUTURE — Posible, Modular

### Windows (Wine) Support — *exploratorio*

- **Prioridad:** Baja (post-v1.0)
- **Enfoque:** Modular — APK se queda ligero (~82MB). El runtime (Wine +
  Box86/Box64 + DXVK) se descarga de GitHub Releases en el primer uso.
- **Stack:** Wine (LGPL) + Box86/Box64 (MIT) + DXVK (LGPL)
- **Activación:** Engine opcional en Settings > Addons → "Windows Runtime"
- **Alcance:** No es competir con Winlator. Es para los pocos VNs/RPGs
  nativos de Windows que no tienen port a Android.
- **Referencia:** Winlator (brunodev85/winlator) como prueba de concepto.

### Wolf RPG Editor Native Interpreter (post-v1.0 research)

- **Prioridad:** Baja hasta cerrar store/install reliability y plugin APKs.
- **Objetivo:** Ejecutar juegos Wolf RPG Editor directamente desde
  `Data.wolf`/archivos del juego, sin Winlator/GameHub/Wine.
- **Alcance tecnico:** Implementar o portar un runtime real: parser de
  datos Wolf, sistema de eventos, render de mapas, sprites, texto, audio,
  input, saves, locale/encoding, archivos empaquetados.
- **Riesgo:** No hay un runtime Android open-source maduro equivalente a
  EasyRPG/mkxp-z. JoiPlay tampoco soporta Wolf nativamente.

---

*Built with Kotlin. No XML. GPLv2+. 82MB APK. Runs on your phone, not some
cloud server.*
