# Runestone — AGENTS.md

## Project Identity

Multi-engine RPG Maker & visual novel game launcher for Android.
Open source (GPLv2+). All core runtimes bundled natively.

## Current Version

**v0.8.2.2d** (code 30) — `develop` branch is the consolidation branch
holding all merged work since the v0.6.13 cycle.

| Branch | Purpose | Status |
|--------|---------|--------|
| `develop` | Integration / active development | HEAD. Receives merged feat/fix branches. |
| `master` | Stable release tracking | Frozen at v0.6.13 era; do not push. |
| `feat/mkxpz-controller-overhaul` | Native RTP path fix | Active. Branched today, 45 commits ahead. |
| `fix/runtime-menu-native-layout-polish` | v0.8.3 follow-up (innoextract JNI static NDK build + menu polish) | Active. 12 commits ahead of develop, **rebase onto develop before merge**. |
| `feature/visual-filters` | Visual filter Phase 1 | Active. 2 commits ahead, fast-forwardable. |
| `feature/phase*` (0..10) | Old refactor/ui-complete-era work | **Stale**. Most work landed via other paths; safe to delete after verification. |

## Convenciones para Agentes de IA

### Reglas de Código (NO NEGOCIABLE)

- **NO emojis en código, UI, comentarios o commits.** Usa texto o SVG para iconos.
- UI glassmorphism: fondos semitransparentes con esquinas redondeadas, borde sutil.
- Sin layouts XML — todo el UI es programático (Kotlin puro).
- `animTap()` en cada elemento táctil: scale bounce con OvershootInterpolator.
- Sin dependencias externas pesadas — `org.json` para configs, sin Gson/Moshi.
- Commit messages en inglés, descriptivos.

### Arquitectura de Motores

```
GameEngine (interface)
├── MkxpZEngine       — XP/VX/VX Ace (libmkxp-z.so, GPLv2+, NDK r27)
├── EasyRpgEngine     — 2000/2003 (libeasyrpg_android.so, GPLv3)
├── OnscripterEngine  — NScripter (libonscripter.so + libsdl.so) WORKING
├── WebViewMvEngine   — MV (system WebView)
├── WebViewMzEngine   — MZ (system WebView)
├── TyranoEngine      — TyranoBuilder (WebView)
├── ConstructEngine   — Construct 2/3 (WebView)
├── HtmlGameEngine    — HTML5 genérico (WebView)
├── TwineEngine       — Twine (WebView)
├── VnMakerEngine     — VN Maker (WebView)
├── RuffleEngine      — Flash/SWF (ruffle.js CDN)
├── RenpyEngine       — Ren'Py (librenpython.so 55MB, wrapper pending)
├── GodotEngine       — Godot (OPTIONAL, requiere habilitar en Addons)
├── ElectronEngine    — Detect only (muy pesado para mobile)
└── + Legacy: RM95, Dante98
```

### Optional Engines System

Los motores opcionales NO se registran por defecto. Se habilitan via
`Settings > Addons` (SharedPreferences `runestone-optional-engines`).
Godot es el primero en esta categoría. Sus `.so` (142 MB) están en
`optional-libs/godot/`.

### Sparse Patch Workspace

Games live at `files/games/{storageName}/original/`. Patches apply in
place with per-file backups under `files/games/{storageName}/patches/`.
Full details: `docs/SPARSE-PATCH-WORKSPACE.md`.

### RTP / innoextract JNI

The VX Ace RTP installer (an Inno Setup archive) is extracted using
`libinnoextract_jni.so` (Boost + liblzma + libiconv compiled as a JNI
library — see `app/src/main/jniLibs/arm64-v8a/libinnoextract_jni.so`).
This replaced the old "external binary + execve" approach that broke on
OEMs with `noexec` mount flags. Build configuration is in
`app/build.gradle.kts` (`innoextract` externalNativeBuild block).

### Convenciones de Build

- APK: `./gradlew clean :app:assembleDebug`
- SDK 35, minSdk 26, **arm64-v8a only**
- NDK **r27** for `libmkxp-z.so` and `libinnoextract_jni.so` (NDK r23 is
  declared in `ndkVersion` for backwards compatibility, but the native
  builds are pinned to r27 via the externalNativeBuild configs)
- `versionCode` and `versionName` in `app/build.gradle.kts`

### Convenciones de Git

- NO push con código roto. Verificar build antes de commit.
- NO emojis en mensajes de commit.
- `feat:`, `fix:`, `docs:`, `chore:`, `refactor:` prefijos estándar.
- Submodules: `native/mkxp-z-android`, `native/easyrpg-android`.
- Branch from `develop`, not `master`.

### Documentación

The `docs/` tree holds live documentation. Anything that describes a
shipped feature, a completed plan, or a completed investigation lives
in `docs/archive/` with a `STATUS` header noting that it is superseded.
Don't move things into the archive without adding a status header.

### Known Issues / WIP

- **Store:** Mediafire is broken on Android (no CDN links on mobile UA).
  Catalogue has been migrated to Pixeldrain + archive.org mirrors.
- **Carousel UI:** Scroll snap occasionally unstable; 3D transitions can
  glitch on very fast scroll.
- **Hero cards:** Currently solid color per engine — no scraped art yet.
- **Ren'Py Wrapper:** Detection + saves work; needs `PythonActivity.java`
  + `renpy/common/` engine files before launch.
- **Per-game settings UI:** `PerGameConfig` + `GameConfigService` merged
  but the Settings screen UI to edit per-game values is not yet wired up.
- **Search text input:** Lives in the filter/sort overlay; no standalone
  search bar on the home screen.
- **Runtime layout polish:** `fix/runtime-menu-native-layout-polish`
  carries the v0.8.3 follow-up and is **not yet rebased onto develop**.

### Contacto / Repo

- https://github.com/KleirRampage45/Runestone
- Active integration branch: `develop`
- Working branches: `feat/mkxpz-controller-overhaul`,
  `fix/runtime-menu-native-layout-polish`, `feature/visual-filters`
