# Runestone Changelog

## v0.6.13 (2026-06-01) — feat/asuka-gap-closure
### Added
- **L1/R1 shoulder buttons** in TouchOverlayView — small pill buttons at top of control panel
  - L1 maps to KEYCODE_BUTTON_L1, R1 maps to KEYCODE_BUTTON_R1
  - JS dispatch: Q(81) for L1, W(87) for R1 (WebView MV/MZ games)
  - Fixed position (not part of layout editor)
- **ONScripter (NScripter) engine — WORKING**
  - 5 Java JNI bridge files from onscripter-engine-android (ONScripterView, DemoGLSurfaceView, GLSurfaceView_SDL, Audio, NativeONSException)
  - OnscripterActivity.kt — thin activity wrapper with font detection, save dir, HQ audio
  - Registered in AndroidManifest, wired to NScripterEngine launch
- **Per-game settings system** (merged from feature/phase1-pergame-config)
  - PerGameConfig.kt: JSON-based layered config (game, input, video, audio, performance, cheats, fonts sections)
  - GameConfigService.kt: Load/save/apply/merge per-game configs
  - No extra dependencies — uses org.json
- **Optional Addons system** — SharedPreferences-based engine toggles
  - EngineRegistry.isOptionalEnabled()/setOptionalEnabled()
  - Godot moved to optional (disabled by default, enable in Settings > Addons)
- **Ren'Py deep research doc** at docs/renpy-how-it-works.md
  - Full architecture breakdown of Android bootstrap
  - 4 core challenges identified for generic launcher

### Changed
- **APK size: 225MB → 82MB** — removed Godot .so from default build
  - libgodot_android.so (142MB) and libc++_shared_godot.so (1.4MB) moved to optional-libs/godot/
  - Re-enable by copying back to jniLibs or via Addons download (future)
- **Gap analysis fully rewritten** — RUNESTONE-vs-JOIPLAY-GAP-ANALYSIS.md now reflects v0.6.13 reality
- **Documentation overhaul** — AGENTS.md expanded, README.md updated, DESIGN.md updated

### Fixed
- Build now correctly excludes optional .so files from APK packaging

## v0.6.12 (2026-05-31)
### Changed
- Replaced custom 3D carousel layout with standard LinearLayoutManager + PagerSnapHelper
- Carousel cards lowered to 59% screen height
- PLAY/SETTINGS actions standardized to 150dp width
- Carousel metadata anchored above bottom dock clearance

### Fixed
- Canonical EasyRPG command-line arguments for RM2000/2003 games
- Unavailable Godot/Ren'Py/ONScripter wrappers show coming-soon dialogs instead of crashes
- Store installs normalize flat/single-root ZIP archives into complete original/ workspaces
- Download cleanup after URL-resolution failures
- Paused store downloads resume correctly after worker stop
- MediaFire legacy URL conversion + redirect fallback handling

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
