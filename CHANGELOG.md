# Runestone Changelog

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
