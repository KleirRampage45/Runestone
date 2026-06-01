# Runestone Changelog

## v0.6.11 (2026-05-31)
### Fixed
- Replaced the custom 3D carousel layout and snap implementation with a standard horizontal `LinearLayoutManager` and `PagerSnapHelper`.
- Moved carousel 3D transforms into a scroll listener and lowered the cards to 59% of screen height.
- Added tap-to-toggle PLAY and SETTINGS actions directly on carousel cards while keeping the detail panel metadata-only.
- Settings layout and UI mode selectors now update their selected backgrounds immediately.
- Standardized PLAY and SETTINGS action widths to 150dp across card views.
- Reserved the full bottom dock clearance so scroll content and the resume bar stay visible above it.
- Anchored carousel metadata above the bottom dock so engine and file-count text remain unobscured.
- Passed the canonical EasyRPG command-line arguments when launching RPG Maker 2000/2003 games.
- Replaced unavailable Godot, Ren'Py, and ONScripter wrapper launches with clear coming-soon dialogs instead of runtime crashes.
- Fixed store installs so flat and single-root ZIP archives are normalized into complete `original/` workspaces.
- Fixed download cleanup after URL-resolution failures and corrected resume accounting when a server ignores byte ranges.
- Fixed paused store downloads so an immediate RESUME request waits for the previous worker to stop and then continues.
- Added compatibility redirects for stale vgperson MediaFire URLs in the default catalogue.
- Replaced hardcoded MediaFire URL redirects with generic legacy URL conversion, broader page parsing, and redirect fallback handling.

## v0.6.7 (2026-05-28)
### Changed
- **Filter/Sort menu** — complete redesign as a glassmorphism overlay with:
  - Full-screen dark blurred backdrop (RenderEffect on Android 12+)
  - Slide-up card animation with OvershootInterpolator
  - DONE button moved to title row
  - Clear (X) button in search input — clears text and resets filter
  - REVERT button now restores ALL initial state (filter, sort, AND search text)
  - Sort rows now show checkmark ✓ on selection
  - Animated transitions when switching sort modes (slide + fade)
  - Glass styling on engine chips (transparent bg, subtle border)
  - Backdrop tap or Done applies and closes
- **STOP confirmation dialog** — replaced Android AlertDialog with a custom glass overlay:
  - Dark blurred backdrop (RenderEffect)
  - Centered glass card with slide-up entrance animation
  - "STOP GAME" button in red glass styling
  - "CANCEL" button in muted glass styling
  - Both buttons have dismiss animations (fade + scale)
  - No more blue screen on STOP — uses showHome() instead of finish()
- **STOP no longer causes blue screen** — uses showHome() to refresh the home screen without finishing MainActivity. Game stays running underneath but resume bar disappears entirely.

## v0.6.6 (2026-05-28)
### Added
- **STOP button** in resume bar — red button alongside green RESUME. Shows confirmation dialog: "Any unsaved progress will be lost. Save data on disk is NOT affected." On confirm, clears pause state and returns to game, which detects the STOP flag and finishes itself
- **Shared-preference stop_game flag** — both mkxp-z and EasyRPG engines check for this in `onResume()`. When the launcher sets it, the game finishes cleanly on next resume

### Fixed
- **Multi-game launch**: launching a new game while the previous one is paused now uses `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` and finishes MainActivity — old game is removed from the stack instead of staying underneath
- **Keyboard for ALL engines**: mkxp-z MainActivity's KBD button now calls `SDLActivity.showTextInput()` instead of `toggleSoftInput()` — same fix as EasyRPG in v0.6.5. Properly creates `mTextEdit`, requests focus, and shows IME so text input works in Blacksouls too
- **Keyboard icon**: KBD button text changed from "KBD" to "⌨" in both mkxp-z and EasyRPG
- **OFF crash**: added `mkdirs()` for config path directory — EasyRPG native code couldn't write `config.ini` if the `/easyrpg/` dir didn't exist
- **Config path dirs**: ensured `mkdirs()` is called for both config and save paths

### Changed
- Version bump: 0.6.5 → 0.6.6 (code 12)

## v0.6.5 (2026-05-28)
### Fixed
- **Portrait mode**: `launchEasyRpgGame()` now passes `EXTRA_LAYOUT_MODE`, `GAME_PATH`, and all touch/haptic extras to the EasyRPG activity — game respects your chosen layout mode
- **Config warning**: added `--config-path` and `--save-path` CLI arguments pointing to app's private data dir — eliminates "Could not determine config path" startup noise
- **Keyboard input**: replaced mkxp-z KBD button behavior — now calls `SDLActivity.showTextInput()` which properly creates `mTextEdit`, requests focus, and shows the IME. Previously just called `toggleSoftInput()` with no text input target
- `getRtpPath()`: returned null → SIGABRT in `FileFinder_RTP` constructor

### Changed
- Version bump: 0.6.4 → 0.6.5 (code 11)

## v0.6.4 (2026-05-28)
### Fixed
- EasyRPG crash: missing `getRtpPath()` instance method → `NoSuchMethodError` → SIGABRT in `Scene_Logo::DetectGame()` → app restart loop

### Changed
- Version bump: 0.6.3 → 0.6.4 (code 10)

## v0.6.3 (2026-05-28)
### Fixed
- EasyRPG crash: game path passed as raw `argv[0]` but skipped per Unix convention — now passes `--project-path <dir>` which `ParseCommandLine()` properly handles
- Config dialog showing "Invalid --project-path" instead of crashing

### Changed
- Version bump: 0.6.2 → 0.6.3 (code 9)

## v0.6.2 (2026-05-28)
### Fixed
- EasyRPG crash: `getAssetManager()` missing static JNI method → `NoSuchMethodError` → `SIGABRT` in `filesystem_apk.cpp`
- EasyRPG crash: `getHandleForPath()` missing static JNI method → `NoSuchMethodError` → `SIGABRT` in `filesystem_saf.cpp`

### Changed
- Version bump: 0.6.1 → 0.6.2 (code 8)

## v0.6.1 (2026-05-28)
### Fixed
- Single-selection UI: tapping a game card now deselects the previous card
- Tapping the same card again deselects it (toggle behavior)
- EasyRPG wrapper: added `getArguments()` override to pass game path as CLI args
- EasyRPG wrapper: added `save_path` intent extra support

### Changed
- Version bump: 0.6.0 → 0.6.1 (code 7)

## v0.6.0 (2026-05-28)
### Fixed
- **Critical**: EasyRPG `libSDL2.so` (GNU libstdc++) was overwriting mkxp-z `libSDL2.so` (NDK libc++), causing dual C++ runtime load → SIGABRT in mkxp-z
- EasyRPG crash: `ClassNotFoundException: org.easyrpg.player.GameActivity` — Java sources not compiled
- EasyRPG crash: activity name mismatch (GameActivity → EasyRpgPlayerActivity)
- Manifest missing `android:icon` and `android:roundIcon` — adaptive icon not displayed

### Added
- EasyRPG JNI wrapper (`EasyRpgPlayerActivity`) — minimal surface for `libeasyrpg_android.so`
- Godot `libgodot_android.so` (142MB) bundled
- Ren'Py `librenpython.so` (55MB) bundled
- ONScripter `libonscripter.so` (2.3MB) + `libsdl.so` bundled
- EasyRPG .so files (15MB) from v0.8.1 APK
- GodotActivity, OnscripterActivity, RenpyActivity registered in manifest
- All engines now EngineTier.BUNDLED (zero downloads needed)
- Adaptive launcher icon from user's icon.png

### Changed
- APK: 62MB → 223MB (16 engines bundled, 15 .so files)
- EasyRPG source removed from Gradle (too entangled with AppCompat/ini4j)
- GodotEngine, RenpyEngine, NScripterEngine launch paths → native bundled
- EngineType tiers: DOWNLOAD → BUNDLED for all previously-DOWNLOAD engines

## v0.3.0 (2026-05-28) — feature/phase11-bundle-all
### Added
- EasyRPG bundled natively (libeasyrpg_android.so from v0.8.1 APK)
- EngineType expanded to 19 engines with EngineTier
- LegacyEngineDetector (RM95/Dante98)
- RuffleEngine (Flash, MIT)
- WebEngines (HTML5/Twine/VN Maker/NScripter/Electron)
- GodotEngine detection (project.godot/.pck)
- LICENSE-BUNDLING-STRATEGY.md
- IMPLEMENTATION_STATUS.md

### Changed
- EasyRPG launch code: Toast → bundled native Intent
- APK grew from ~10MB to 62MB

## v0.2.0 (2026-05-27) — initial Runestone skeleton
### Added
- mkxp-z native engine (13MB) for RPG Maker XP/VX/VX Ace
- Ruby .so (12MB) for mkxp-z
- SDL2 libraries (graphics/audio)
- WebView-based MV/MZ engine
- Game import via SAF
- Portrait console layout mode
- Gamepad overlay
- 14 engine plugins initialized
