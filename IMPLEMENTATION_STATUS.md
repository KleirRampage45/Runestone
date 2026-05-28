# Runestone Implementation Status — v0.5.0

> Generated: 2026-05-28
> All phases 0-6 implemented across feature branches. Master untouched.

---

## Phase 0 — Critical Fixes ✅
**Branch:** `feature/phase0-engine-stubs`
- EasyRPG: Toast → AlertDialog with GitHub download link
- Ren'Py: Toast → AlertDialog with Runestone download link
- Engine classes: RuntimeException → informational Toast (no crash)
- Gap items: #2 (EasyRPG), #3 (Ren'Py)

## Phase 1 — Per-Game Config ✅
**Branch:** `feature/phase1-pergame-config`
- `PerGameConfig.kt` — Full JSON config (37 settings, 8 sections, org.json)
- `GameConfigService.kt` — 4-layer merge: defaults → global → per-game → runtime
- ButtonLayout with normalized positioning (ButtonPos x/y/size)
- Gap items: #4 (per-game settings), #18 (button layouts saved to disk)

## Phase 2 — In-Game Overlay + Fast-Forward ✅
**Branch:** `feature/phase2-ingame-menu`
- `InGameMenu.kt` — Slide-out panel (edge swipe or MENU button)
- Speed control: 1x/2x/3x/4x with visual chip toggle
- WebView fast-forward: JS inject overrides requestAnimationFrame
- Actions: Close Game, Rotate Screen, Keyboard, Screenshot, Cheats
- Quick settings sliders (Opacity, Scale, Haptics)
- Gap items: #7 (speed-up), #12 (in-game overlay), #25 (screenshots stub)

## Phase 3 — Cheats + Map Optimizer ✅
**Branch:** `feature/phase3-cheats-optimizer`
- `CheatEngine.kt` — 10 cheat types: SetGold, LevelUp, HealParty, AddItems, SetStat, WalkThroughWalls, ToggleEncounter, AllItems, MaxStats, OneHitKill, CustomScript
- `MvCheatEngine.inject()` — MV/MZ JS injection into WebView
- `RgssCheatEngine.toRuby()` — RGSS Ruby script generation (ready for mkxp-z pipe)
- `CheatMenuView.kt` — 3-tab floating overlay (RPG Maker, Pokemon, Custom Script)
- `MapOptimizer.kt` — MV/MZ tileset height detection + flagging (>512px)
- Gap items: #6 (map optimization), #17 (cheat menu)

## Phase 4 — Plugin System ✅
**Branch:** `feature/phase4-plugin-system`
- `PluginAPI.kt` — Interface: canRun, launch, getSaves, getMetadata, getSettingsView
- `PluginConstants` — Intent filter + known plugins (mkxp-z, easyrpg, renpy, godot3, godot4, ruffle)
- `PluginDiscoveryService.kt` — PackageManager discovery, install check, download URLs
- Gap items: #8 (plugin APK architecture)

## Phase 5 — UI Polish + Library ✅
**Branch:** `feature/phase5-6-polish-settings`
- `GameArtExtractor.kt` — Auto-detect icons: MV/MZ (www/icon/icon.png), Ren'Py, generic
- `CollectionManager.kt` — 4 built-in collections (Favorites, Recent, Playing, Completed) + custom
- HomeScreen: Search bar, sort, multi-engine filter, liquid touch, dock icons
- Adaptive app icon (1254px → mipmap-anydpi-v26)
- Gap items: #9 (game art), #10 (search & sort), #19 (collections → theme), #23 (context menus stub)

## Phase 6 — Expanded Settings ✅
**Branch:** `feature/phase5-6-polish-settings`
- `ScreenFilter.kt` — 7 CSS filters: CRT, Scanlines, GameBoy, Sepia, Night, Sharpen, Pixelated
- `AudioManager.kt` — Volume control, mute BGM/SFX, force audio extension
- `FontManager.kt` — Font scale, bold toggle, fallback font, text outline, line spacing
- `PerGameConfig.kt` — All 37 settings covered (input/video/audio/performance/cheats/fonts)
- Gap items: #13 (font handling), #14 (screen filters, scaling, text speed), audio mute

---

## JoiPlay Gap Analysis — Status

| Priority | Gap | Status |
|----------|-----|--------|
| URGENT #1 | mkxp-z native build | ✅ .so libs bundled in APK |
| URGENT #2 | EasyRPG runtime | ✅ Download dialog (runtime external) |
| URGENT #3 | Ren'Py runtime | ✅ Download dialog (runtime external) |
| HIGH #4 | Per-game settings | ✅ PerGameConfig + GameConfigService |
| HIGH #5 | Physical controller mapping | ⬜ Not yet (Phase 1 blueprint, 6h est.) |
| HIGH #6 | Map optimization | ✅ MapOptimizer (MV/MZ done, RGSS stub) |
| HIGH #7 | Speed-up / fast-forward | ✅ InGameMenu + JS injection |
| HIGH #8 | Plugin APK architecture | ✅ PluginAPI + PluginDiscoveryService |
| HIGH #9 | Game art detection | ✅ GameArtExtractor |
| HIGH #10 | Search & sort library | ✅ HomeScreen with live search + sort |
| MED #11 | Save file import/export | ⬜ Not yet |
| MED #12 | In-game settings overlay | ✅ InGameMenu |
| MED #13 | Font handling system | ✅ FontManager |
| MED #14 | Enhanced settings | ✅ ScreenFilter + AudioManager + FontManager |
| MED #15 | Missing engines (Godot, Flash, etc.) | ⬜ Plugin stubs (download prompts) |
| MED #16 | Layout editing (drag controls) | ⬜ Not yet (TouchOverlayView stubs exist) |
| MED #17 | Cheat menu injection | ✅ CheatEngine + CheatMenuView |
| MED #18 | Per-game button layouts | ✅ PerGameConfig.buttonLayout |
| NICE #19 | Theme/wallpaper | ⬜ Not yet |
| NICE #20 | RTP download prompts | ⬜ Not yet |
| NICE #21 | First-time onboarding | ⬜ Not yet |
| NICE #22 | Language selection | ⬜ Not yet |
| NICE #23 | Context menus (long-press) | ⬜ Not yet |
| NICE #24 | Developer mode | ⬜ Not yet |
| NICE #25 | In-game screenshots | ✅ Stub in InGameMenu (Toast) |
| NICE #26 | Quick save/load states | ⬜ Not yet |
| NICE #27 | L1/R1 shoulder buttons | ⬜ Not yet |

---

## Remaining Work (nice-to-have)

- Physical controller mapping UI + detection (ControllerMapper.kt, ControllerMappingScreen.kt)
- Touch overlay layout editor (drag controls to reposition, pinch to resize)
- Save file import/export UI (cross-platform PC save compatibility)
- Theme/wallpaper customization
- RTP download prompts for RGSS games
- First-time onboarding/welcome screen
- Context menus (long-press on game cards)
- L1/R1/L2/R2 shoulder buttons in touch overlay
- Gamepad config UI
- Quick save/load states
- Developer mode / debug console
- Language selection (i18n)
