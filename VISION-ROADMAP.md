# Runestone — Vision & Roadmap

> **Updated:** 2026-06-12
> **Version:** v0.8.2.2d (code 30) — 82 MB default APK, 13 working engines
> **Branch:** `develop`

This document is the **current** view of the project. Superseded plans and
historical snapshots live in `docs/archive/`.

---

## Current Status (v0.8.2.2d)

| Area | Status | Where |
|------|--------|-------|
| **mkxp-z Native (XP/VX/VX Ace)** | Working — NDK r27 build | `app/src/main/jniLibs/arm64-v8a/libmkxp-z.so` |
| **EasyRPG (2000/2003)** | Working — bundled .so, full JNI bridge | `engine/EasyRpgEngine.kt` + `EasyRpgPlayerActivity` |
| **ONScripter (NScripter)** | Working — bundled .so, full JNI bridge | `engine/OnscripterEngine.kt` + `OnscripterActivity` |
| **MV / MZ (WebView)** | Working — PIXI fixes, audio fallback, keyboard | `engine/WebViewMvEngine.kt` / `WebViewMzEngine.kt` |
| **TyranoBuilder, Construct, HTML, Twine, VN Maker** | Working — WebView | `engine/` |
| **Flash (Ruffle)** | Working — ruffle.js CDN | `engine/RuffleEngine.kt` |
| **Ren'Py** | Detection + saves work; `librenpython.so` bundled; activity wrapper pending | `engine/RenpyEngine.kt` |
| **Godot** | Optional — `libgodot_android.so` in `optional-libs/godot/`; enable in Settings > Addons | `engine/GodotEngine.kt` |
| **Virtual Controls** | D-pad + A/B/X/Y + L1/R1 + SELECT/START/SETTINGS | `input/TouchOverlayView.kt` |
| **Layout Modes** | Portrait / Landscape; runtime controls toggle in-game | `feat/phase-a..d-*` (merged) |
| **Runtime Menu** | Slide-out menu with controls / layout / pause / stop | `feat/phase-b-runtime-layout-menu` (in `fix/runtime-menu-native-layout-polish`) |
| **Control Profiles + Layout Editor** | Per-game profile storage + drag-edit | `feat/phase-c-control-profiles` + `feat/phase-d-control-layout-editor` |
| **Save Protection** | Auto-backup before reimport, auto-restore | `data/SaveManager.kt` |
| **Patch System** | Sparse in-place patches with per-file backups | `docs/SPARSE-PATCH-WORKSPACE.md` |
| **RTP Installer** | Auto-detects VX Ace, downloads official RTP, extracts via `libinnoextract_jni.so` | `rtp/RtpInstaller.kt` |
| **Game Store** | Static JSON catalogue (Pixeldrain + archive.org mirrors) | `provider/SourcesManager.kt` |
| **Per-game Settings** | JSON-based layered config (game, input, video, audio, performance, cheats, fonts) | `data/PerGameConfig.kt` + `data/GameConfigService.kt` |
| **Search / Filter / Sort** | Glass overlay filter, text search, sort modes | `ui/HomeScreen.kt` |
| **RESUME bar** | Glass banner with RESUME / STOP | `ui/HomeScreen.kt` |
| **Glassmorphism UI** | Dock, filter overlay, stop dialog, card effects | `ui/` |
| **Detail Panel** | Per-game metadata panel with engine info | `ui/HomeScreen.kt` |
| **Adaptive Icon** | From user's `icon.png` | `mipmap-anydpi-v26/` |
| **Visual Filters** | Post-processing chain (Phase 1, native lib) | `feature/visual-filters` (not yet on `develop`) |

---

## Active Branches (WIP)

These branches are not yet merged into `develop`. The integration branch
receives them after review and rebase.

| Branch | Purpose | Next Step |
|--------|---------|-----------|
| `feat/mkxpz-controller-overhaul` | Native RTP path resolution + visual filter pipeline in `libmkxp-z.so` | Awaiting integration into `develop` |
| `fix/runtime-menu-native-layout-polish` | v0.8.3 — innoextract JNI static NDK build + runtime menu polish + 12 new commits including phase-a..d | **Rebase onto develop before merge.** |
| `feature/visual-filters` | Visual filter Phase 1 — data model, settings UI, native pipeline | Fast-forwardable; small change set |

Stale `feature/phase0..10-*` branches (1-commit pointers to old work)
remain in the local repo as historical artifacts. Their content is
already on `develop` via the merge chain; do not base new work on them.

---

## Remaining Gaps (vs JoiPlay, 2026-06-12 view)

### Shipped in v0.8.x

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

### Remaining (still WIP)

- **Ren'Py wrapper** — needs `PythonActivity.java` + `renpy/common/`
  engine files (~40 MB). Blocking the engine from launching.
- **Per-game settings UI in SettingsScreen** — `PerGameConfig` is stored
  and applied, but the Settings screen does not yet let the user edit
  per-game values.
- **Hero card art** — currently solid color per engine; needs scraped
  artwork (IGDB / SteamGridDB or on-device game art extraction).
- **Carousel polish** — occasional scroll-snap glitch; 3D transitions
  can stutter on very fast scrolls.
- **Standalone search bar** — text search lives inside the
  filter/sort overlay; no persistent search bar on the home screen.
- **In-game settings overlay** — engine-level settings still require
  the user to leave the game and re-enter; runtime menu covers layout
  and pause, not yet per-engine.
- **Plugin APK architecture** — Runestone is still monolithic (82 MB);
  splitting core vs engine plugin APKs is a v2.0 goal.

---

## Architecture To-Do

- [ ] **Ren'Py wrapper** — Port `PythonActivity.java` from
      python-for-android + bundle `renpy/common/` engine files.
- [ ] **Godot wrapper** — Re-enable when wrapper ready. Maven dep
      `org.godotengine:godot:4.6.3.stable`. Currently optional.
- [ ] **Plugin APK system** — Split into core (launcher UI) + engine
      plugin APKs.
- [ ] **Font fallback system** — For non-Latin characters in WebView
      games.
- [ ] **Visual filter Phases 2+** — Bloom, grain, DOF, chromatic
      aberration, ambient occlusion. See `docs/design/` for the design
      specs and `feature/visual-filters` for Phase 1.
- [ ] **Theme system** — `docs/design/light-theme-spec.md` describes the
      planned model but is not yet implemented.

---

## FUTURE — Posible, Modular

### Windows (Wine) Support — *exploratorio*

- **Prioridad:** Baja (post-v1.0, post-Ren'Py wrapper)
- **Enfoque:** Modular — APK se queda ligero (~82MB). El runtime (Wine +
  Box86/Box64 + DXVK) se descarga de GitHub Releases en el primer uso, igual
  que el patrón de Godot en `optional-libs/`.
- **Stack:** Wine (LGPL) + Box86/Box64 (MIT) + DXVK (LGPL)
- **Activación:** Engine opcional en Settings > Addons → "Windows Runtime"
  → download button
- **Alcance:** No es competir con Winlator. Es para los pocos VNs/RPGs
  nativos de Windows que no tienen port a Android ni engine open-source
  equivalente.
- **Referencia:** Winlator (brunodev85/winlator) como prueba de concepto.

### Plugin APK System (v2.0)

- Dividir core (launcher UI, ~15MB) de engines (descargables por separado).
- Esto beneficia TANTO a Wine como a Godot, Ren'Py y cualquier engine
  pesado.
- El core siempre es liviano. El usuario descarga solo lo que necesita.

### Wolf RPG Editor Native Interpreter (post-v1.0 research)

- **Prioridad:** Baja hasta cerrar Ren'Py, store/install reliability,
  controles, y plugin APKs.
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
