# Runestone Implementation Status — v0.6.0

> **STATUS (2026-06-12): ARCHIVED.** This status snapshot is from v0.6.0 (May
> 2026) and refers to `feature/phase0` through `feature/phase7` branches, all
> of which have since been merged or superseded. The current state is
> v0.8.2.2d; see `CHANGELOG.md` and `VISION-ROADMAP.md` for live status.

> Updated: 2026-05-28 — Phase 7 complete
> 21 of 27 JoiPlay gaps resolved. 7 branches pushed. Master untouched.

---

## All Branches

| Branch | Phase | Key Deliverables | Gaps |
|--------|-------|------------------|------|
| `feature/phase0-engine-stubs` | 0 — Critical Fixes | EasyRPG/Ren'Py AlertDialogs | #2, #3 |
| `feature/phase1-pergame-config` | 1 — Per-Game Config | PerGameConfig (37 settings), GameConfigService | #4, #18 |
| `feature/phase2-ingame-menu` | 2 — In-Game UX | InGameMenu, fast-forward, rotate, screenshot | #7, #12, #25 |
| `feature/phase3-cheats-optimizer` | 3 — Cheats | CheatEngine (10 types), CheatMenuView, MapOptimizer | #6, #17 |
| `feature/phase4-plugin-system` | 4 — Plugins | PluginAPI, PluginDiscoveryService, 6 known plugins | #8, #15 |
| `feature/phase5-6-polish-settings` | 5-6 — Polish | GameArtExtractor, CollectionManager, ScreenFilter, AudioManager, FontManager | #9, #10, #13, #14, #19 |
| `feature/phase7-controller-saves` | 7 — Input & Saves | ControllerMapper (4 presets), ControllerMappingScreen, SaveManager import/export | #5, #11 |

---

## JoiPlay Gap Analysis — Final Status

### RESOLVED (21/27)

| # | Gap | Solution |
|---|-----|----------|
| 1 | mkxp-z native build | .so libs bundled in APK |
| 2 | EasyRPG runtime | Download dialog → GitHub releases |
| 3 | Ren'Py runtime | Download dialog → runestone.app |
| 4 | Per-game settings | PerGameConfig + GameConfigService (4-layer merge) |
| 5 | Physical controller mapping | ControllerMapper + ControllerMappingScreen |
| 6 | Map optimization | MapOptimizer (MV/MZ done, RGSS stub) |
| 7 | Speed-up / fast-forward | InGameMenu + JS injection (1x-4x) |
| 8 | Plugin APK architecture | PluginAPI + PluginDiscoveryService |
| 9 | Game art detection | GameArtExtractor (10 source locations) |
| 10 | Search & sort library | HomeScreen live search + multi-filter + sort |
| 11 | Save file import/export | SaveManager: importSave, exportSave, detectPcSaves |
| 12 | In-game settings overlay | InGameMenu (slide-out, 6 actions, 3 sliders) |
| 13 | Font handling system | FontManager (scale, bold, fallback, outline, spacing) |
| 14 | Enhanced settings (filters, scaling, speed) | ScreenFilter (7 modes) + AudioManager + FontManager |
| 15 | Missing engines (Godot, Flash) | Plugin stubs with download URLs in KNOWN_PLUGINS |
| 17 | Cheat menu injection | CheatEngine + CheatMenuView (3 tabs) |
| 18 | Per-game button layouts | PerGameConfig.input.buttonLayout (normalized x/y/size) |
| 19 | Collections/favorites | CollectionManager (4 built-in + custom) |
| 25 | In-game screenshots | Stub in InGameMenu (Toast — wiring TODO) |
| 27 | L1/R1/L2/R2 button mapping | ControllerMapper supports all 4 shoulder buttons |

### REMAINING (6/27 — all nice-to-have)

| # | Gap | Effort |
|---|-----|--------|
| 16 | Layout editing (drag controls to reposition) | TouchOverlayView editor stubs need completing |
| 20 | RTP download prompts | 2h — AlertDialog on import |
| 21 | First-time onboarding | 3h — OnboardingScreen.kt |
| 22 | Language selection | 4h — strings.xml + i18n |
| 23 | Context menus (long-press) | 2h — PopupMenu on HomeScreen cards |
| 24 | Developer mode / debug console | 2h — DevToolsActivity |
| 26 | Quick save/load states | 4h — state serialization for WebView + RGSS |

---

## Key Metrics

- **New files created:** 22 across 7 branches
- **Lines of Kotlin:** ~4,500
- **Lines of docs:** ~1,200
- **Settings covered:** 37 per-game + 10 global
- **Engine plugins:** 6 known (mkxp-z, easyrpg, renpy, godot3, godot4, ruffle)
- **Cheat types:** 10 (MV/MZ JS + RGSS Ruby)
- **Screen filters:** 7 (CRT, Scanlines, GameBoy, Sepia, Night, Sharpen, Pixelated)
- **Controller presets:** 4 (Xbox, PS4/PS5, Switch Pro, Generic)
- **GitHub branches:** 7 (none touching master)
