# Runestone — Vision & Roadmap v3

> "Glass" update — May 2026
> Glassmorphism UI, dock bar, filter/sort, mkxp-z native engine

---

## Current Status (v0.3.0 — "Glass")

| Area | Status |
|------|--------|
| **UI** | Glassmorphism dock bar, custom filter dropdown, sort options, scalable hero cards |
| **mkxp-z Native** | ✅ Working — launches XP/VX/VX Ace via pre-built .so libraries |
| **MV/MZ WebView** | ✅ Working — full WebView runtime with audio fallback, PIXI fixes |
| **Virtual Controls** | ✅ D-pad + buttons + keyboard summon + edit mode |
| **Layout Modes** | ✅ Portrait Console / Landscape / Gamepad |
| **Save Protection** | ✅ Auto-backup before reimport, auto-restore |
| **Game Detection** | ✅ From Game.ini (RGSS) and System.json (MV/MZ) |
| **Engine Override** | ✅ Per-game picker in Manage Files |
| **Help & About** | ✅ In Settings |

---

## UI Vision — "Liquid Glass"

### What's implemented:
- Glassmorphism dock bar (transparent blurred backgrounds)
- Custom filter dropdown (expands from button, search-ready)
- Sort modes (A→Z, Z→A, recent, date added)
- Tap animations (scale bounce on buttons)
- Dark amber palette (#030304 bg, #CFAE7E accent)

### Planned:
- [ ] **Chromatic aberration / lens effects** — Overlay shader on hero cards for 3D depth
- [ ] **Full blur support** — RenderScript/BlurMaskFilter on backgrounds (Android 12+)
- [ ] **Glass effects on all panels** — Apply to Settings, Manage Files, Import progress
- [ ] **Smooth page transitions** — Slide/fade between screens
- [ ] **3D carousel layout** — Horizontal scroll with perspective transform
- [ ] **Grid layout option** — Compact hero cards, play on hover
- [ ] **List layout option** — Minimal rows with metadata

---

## Feature Gap vs JoiPlay

See `RUNESTONE-vs-JOIPLAY-GAP-ANALYSIS.md` for full comparison.

### URGENT
1. ~~Working mkxp-z native build~~ ✅ DONE (pre-built .so from Grimmobile)
2. Working EasyRPG runtime (RM2000/2003)
3. Working Ren'Py runtime

### HIGH PRIORITY
4. **Per-game settings** — Different layouts, opacity per game
5. **Physical controller mapping** — Bluetooth gamepad support
6. **Map optimization** — Tile rendering fixes for Pokémon fangames
7. **Speed-up / fast-forward** — JS injection for WebView, Ruby for mkxp-z
8. **Plugin APK architecture** — Separate installable runtimes
9. **Game art detection** — Auto hero card images from game files
10. **Search & sort library** — 🔶 Sort implemented, search pending
11. **Hero card image picker** — Custom per-game hero art

### MEDIUM PRIORITY
12. Save file import/export (PC ↔ Android)
13. In-game settings overlay (change layout without restart)
14. Font handling system
15. Enhanced per-game settings panel
16. Missing engine support: Godot, Flash, NScripter, Twine
17. Layout editing (drag controls to reposition)
18. Cheat menu injection
19. Per-game button layout save

### NICE TO HAVE
20. Theme/wallpaper customization
21. RTP download prompts
22. First-time onboarding wizard
23. Language selection
24. Context menus (long-press)
25. Developer mode / debug console
26. In-game screenshots
27. Quick save/load states
28. L1/R1 shoulder buttons

---

## Input System Roadmap

| Feature | Priority | Effort |
|---------|----------|--------|
| Per-game layout save | HIGH | Medium |
| Physical controller mapping UI | HIGH | Large |
| Drag-to-reposition controls | MEDIUM | Medium |
| L1/R1 buttons | LOW | Small |
| Turbo / auto-fire | LOW | Small |
| Button preset system | LOW | Small |

---

## Layout Roadmap

| Feature | Priority | Effort |
|---------|----------|--------|
| Grid layout (compact cards) | HIGH | Medium |
| List layout (rows) | MEDIUM | Small |
| 3D carousel | NICE | Large |
| Per-game layout override | HIGH | Medium |

---

## Hero Card Evolution

1. **v0.3.0** — Glass cards with engine color + game name ✅
2. **v0.4.0** — Auto-detect game icon from .exe / game folder
3. **v0.5.0** — SteamGridDB / IGDB metadata lookup
4. **v0.6.0** — Custom hero card picker per game

---

## Architecture To-Do

- [ ] Plugin APK system (like JoiPlay's modular plugins)
- [ ] Native EasyRPG build (C++ → NDK)
- [ ] Native Ren'Py build
- [ ] Font fallback system (for non-Latin games)
- [ ] RTP auto-downloader

---

*Built with Kotlin. No XML. GPLv2+. Runs on your phone, not some cloud server.*
