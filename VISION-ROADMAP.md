# Runestone — Vision & Roadmap

> **Updated:** 2026-06-01
> **Version:** v0.6.13 (82MB APK, 11 working engines)

---

## Current Status (v0.6.13)

| Area | Status |
|------|--------|
| **mkxp-z Native (XP/VX/VX Ace)** | ✅ Working — bundled .so |
| **EasyRPG (2000/2003)** | ✅ Working — bundled .so, full JNI bridge |
| **MV/MZ WebView** | ✅ Working — PIXI fixes, audio fallback, keyboard |
| **ONScripter (NScripter)** | ✅ WORKING — new wrapper, bundled .so |
| **Ruffle (Flash)** | ✅ Working — ruffle.js CDN loader |
| **TyranoBuilder, Construct, HTML, Twine, VN Maker** | ✅ Working — WebView |
| **Ren'Py** | ⚠️ Detection + saves work, librenpython.so bundled, wrapper pending |
| **Godot** | ⚠️ Detection works, .so moved to optional-libs/ (142MB) |
| **Virtual Controls** | ✅ D-pad + A/B/X/Y + L1/R1 + SELECT/START/SETTINGS |
| **Layout Modes** | ✅ Portrait Console / Landscape / Gamepad / 3D Carousel / List / Tiles |
| **Save Protection** | ✅ Auto-backup before reimport, auto-restore |
| **Game Store** | ✅ Static JSON catalogue + download pipeline (Mediafire broken on mobile) |
| **Per-game Settings** | ✅ PerGameConfig merged (input, video, audio, performance, cheats, fonts) |
| **Search/Filter/Sort** | ✅ Glass overlay filter, text search, sort modes |
| **RESUME bar** | ✅ Glass banner with RESUME/STOP |
| **Glassmorphism UI** | ✅ Dock, filter overlay, stop dialog, card effects |
| **Detail Panel** | ✅ Per-game metadata panel with engine info |
| **Adaptive Icon** | ✅ From user's icon.png |

---

## Remaining Gaps (vs JoiPlay)

### URGENT
1. ~~mkxp-z native build~~ ✅ DONE
2. ~~ONScripter wrapper~~ ✅ DONE
3. **Ren'Py wrapper** — librenpython.so bundled, needs PythonActivity.java + renpy/common/ engine files (~40MB)
4. ~~EasyRPG fixes~~ ✅ DONE

### HIGH PRIORITY
5. **Store: mediafire → Pixeldrain migration** — all 12 default games broken on real devices
6. **Hero card art detection** — auto-extract game icons from Game.exe / game folders
7. **Metadata scraping** — IGDB/SteamGridDB API for game covers, descriptions
8. **Physical controller mapping** — Bluetooth gamepad support (branch: feature/phase7-controller-saves)
9. **Speed-up / fast-forward** — JS injection for WebView, Ruby for mkxp-z
10. **Map optimization** — Tile rendering fixes for Pokémon fangames

### MEDIUM PRIORITY
11. **Per-game settings UI in SettingsScreen** — PerGameConfig merged but no UI to configure it
12. **Carousel stability** — Scroll snap sometimes janky, transitions glitch on fast scroll
13. **Search bar standalone** — Text search exists in overlay, but no persistent search bar on home screen
14. **In-game settings overlay** — Change opacity/layout without restarting
15. **Plugin APK architecture** — Currently monolithic (82MB). Split into optional addon APKs
16. **L1/R1 pass-through to native mkxp-z games** — Currently only works for WebView games

### NICE TO HAVE
17. **Game categories / collections**
18. **Recent games section**
19. **First-time onboarding flow**
20. **Theme/wallpaper customization**
21. **RTP download prompts**
22. **Cheat menu injection**
23. **Quick save/load states**
24. **In-game screenshots**

---

## Architecture To-Do

- [ ] **Ren'Py wrapper** — Port PythonActivity.java from python-for-android + bundle renpy/common/ engine files
- [ ] **Godot** — Re-enable when wrapper ready. Maven dep `org.godotengine:godot:4.6.3.stable`. Currently optional.
- [ ] **Plugin APK system** — Split into core (launcher UI) + engine plugin APKs
- [ ] **Font fallback system** — For non-Latin characters in WebView games
- [ ] **Mediafire → Pixeldrain migration** — Only way store works on real devices

---

## FUTURE — Posible, Modular

### 🪟 Windows (Wine) Support — *exploratorio*
- **Prioridad:** Baja (post-v1.0, post-Ren'Py wrapper)
- **Enfoque:** Modular — APK se queda ligero (~82MB). El runtime (Wine + Box86/Box64 + DXVK) se descarga de GitHub Releases en el primer uso, igual que el patrón de Godot en optional-libs/
- **Stack:** Wine (LGPL) + Box86/Box64 (MIT) + DXVK (LGPL)
- **Activación:** Engine opcional en Settings > Addons → "Windows Runtime" → download button
- **Alcance:** No es competir con Winlator. Es para los pocos VNs/RPGs nativos de Windows que no tienen port a Android ni engine open-source equivalente (ej: juegos de RPG Maker que usan DLLs custom, o VNs en engines propietarios donde no hay runtime libre)
- **Nota:** La ingeniería es significativa — portear Wine a ARM64 Android, contenedores por juego, GPU passthrough, input mapping, y mantenimiento continuo. No se aborda hasta tener los gaps reales cerrados.
- **Referencia:** Winlator (brunodev85/winlator) como prueba de concepto de que es posible

### 🔌 Plugin APK System (v2.0)
- Dividir core (launcher UI, ~15MB) de engines (descargables por separado)
- Esto beneficia TANTO a Wine como a Godot, Ren'Py y cualquier engine pesado
- El core siempre es liviano. El usuario descarga solo lo que necesita.

### Wolf RPG Editor Native Interpreter (post-v1.0 research)
- **Prioridad:** Baja hasta cerrar Ren'Py, store/install reliability, controles, y plugin APKs.
- **Objetivo:** Ejecutar juegos Wolf RPG Editor directamente desde `Data.wolf`/archivos del juego, sin Winlator/GameHub/Wine.
- **Alcance tecnico:** Implementar o portar un runtime real: parser de datos Wolf, sistema de eventos, render de mapas, sprites, texto, audio, input, saves, locale/encoding, archivos empaquetados, y compatibilidad con juegos existentes como Mad Father.
- **Riesgo:** No hay un runtime Android open-source maduro equivalente a EasyRPG/mkxp-z. JoiPlay tampoco soporta Wolf nativamente; las rutas funcionales actuales en Android suelen usar capas Windows como Winlator/GameHub.
- **No confundir con:** Integracion externa "Open with Windows runner". Eso puede existir antes, pero no cuenta como soporte nativo Wolf.

---

*Built with Kotlin. No XML. GPLv2+. 82MB APK. Runs on your phone, not some cloud server.*
