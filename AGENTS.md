# Runestone — AGENTS.md

## Project Identity
Multi-engine RPG Maker & visual novel game launcher for Android.
Open source (GPLv2+). All runtimes bundled natively.

## Current Version
v0.6.13 — `refactor/ui-complete` — feat/asuka-gap-closure (active dev branch)

## Convenciones para Agentes de IA

### Reglas de Código (NO NEGOCIABLE)
- **NO emojis en código, UI, comentarios o commits.** Usa texto o SVG para iconos.
- UI glassmorphism: fondos semitransparentes con esquinas redondeadas, borde sutil.
- Sin layouts XML — todo el UI es programático (Kotlin puro).
- `animTap()` en cada elemento táctil: scale bounce con OvershootInterpolator.
- Sin dependencias externas pesadas — org.json para configs, sin Gson/Moshi.
- Commit messages en inglés, descriptivos.

### Arquitectura de Motores
```
GameEngine (interface)
├── MkxpZEngine      — XP/VX/VX Ace (libmkxp-z.so, GPLv2+)
├── EasyRpgEngine    — 2000/2003 (libeasyrpg_android.so, GPLv3)
├── WebViewMvEngine  — MV (system WebView)
├── WebViewMzEngine  — MZ (system WebView)
├── TyranoEngine     — TyranoBuilder (WebView)
├── ConstructEngine  — Construct 2/3 (WebView)
├── HtmlGameEngine   — HTML5 genérico (WebView)
├── TwineEngine      — Twine (WebView)
├── VnMakerEngine    — VN Maker (WebView)
├── RuffleEngine     — Flash/SWF (ruffle.js CDN)
├── NScripterEngine  — ONScripter (libonscripter.so + libsdl.so) ✅ WORKING
├── RenpyEngine      — Ren'Py (librenpython.so 55MB, wrapper pending)
├── GodotEngine      — Godot (OPTIONAL, requiere habilitar en Addons)
├── ElectronEngine   — Detect only (muy pesado para mobile)
└── + Legacy: RM95, Dante98
```

### Optional Engines System
Los motores opcionales NO se registran por defecto.
Se habilitan via `Settings > Addons` (SharedPreferences `runestone-optional-engines`).
Godot es el primero en esta categoría. Sus .so (142MB) están en `optional-libs/godot/`.

### Convenciones de Build
- APK: `./gradlew clean :app:assembleDebug`
- SDK 35, minSdk 26, arm64-v8a only
- NDK r23 para builds nativos
- versionCode y versionName en app/build.gradle.kts

### Convenciones de Git
- NO push con código roto. Verificar build antes de commit.
- NO emojis en mensajes de commit.
- feat:, fix:, docs: prefijos estándar.
- Submodules: native/mkxp-z-android, native/easyrpg-android.

### Known Issues / WIP
- **Store:** Mediafire broken on Android (no CDN links en mobile UA). Migrar catálogo a Pixeldrain.
- **Carousel UI:** Scroll snap inestable, transiciones 3D a veces glitch.
- **Hero cards:** Sin arte de juego — solo color de engine. Necesita metadata scraping.
- **Ren'Py Wrapper:** Necesita PythonActivity.java + renpy/common/ engine files.
- **Physical controller:** feature/phase7-controller-saves branch exists, no mergeado aún.
- **Per-game settings:** PerGameConfig + GameConfigService mergeados, SettingsScreen no actualizado para usarlos.
- **Search text input:** Existe en el overlay filter/sort — no hay barra de búsqueda standalone.

### Contacto / Repo
- https://github.com/KleirRampage45/Runestone
- Branch activa: refactor/ui-complete
- Dev branch: feat/asuka-gap-closure
