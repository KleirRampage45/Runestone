# JoiPlay Settings Option Checklist for Runestone

> Source: user-provided JoiPlay settings screenshot captured on 2026-06-01.  
> Goal: track the settings Runestone should add if it wants JoiPlay-style parity.

This document is a product/implementation checklist, not a promise that every setting must be built immediately. Some items map cleanly to existing Runestone settings; others require engine/runtime work, per-game config storage, plugin support, or native runtime support.

## Current Runestone baseline

Runestone currently exposes only a small settings set:

- Layout mode: portrait console, landscape, gamepad
- Touch opacity
- Touch scale
- Haptic feedback
- Haptic intensity
- Show X/Y buttons
- Force audio extension: `.ogg` or `.m4a`

`RunnerSettings` also already has unused fields for integer scaling, smooth scaling, and text scale. Those should be connected before adding duplicate options.

---

## 1. Game selection

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Game Selection | Dropdown | `AI` | Needed | Per-game settings should be scoped to the selected game/profile. |

Implementation notes:

- Add global settings and per-game override layers.
- Settings screen should make it clear whether the user is editing global defaults or the selected game.
- Export/import should preserve per-game overrides.

---

## 2. App settings

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Default Game Folder | Folder picker | Choose | Needed | Default import/discovery folder for games. |
| Theme | Dropdown | Wallpaper | Needed | Runestone currently has a fixed dark theme. |
| Primary Color | Color picker | Choose | Needed | Accent color / brand color customization. |
| Animation Frames | Dropdown | None | Needed | Likely UI animation/frame setting. Needs behavior definition. |
| Enable Cheats | Toggle | On | Needed | Requires runtime-level cheat/debug injection support. |
| Lock Screen | Toggle | Off | Needed | Prevent device sleep while playing, or lock orientation/screen state. Clarify behavior during implementation. |
| Experimental Features | Toggle | Off | Needed | Gate unstable options. |
| Context Fix | Toggle | Off | Needed | Likely RPG Maker / WebView context-loss fix. Needs investigation. |

Implementation notes:

- `Default Game Folder` should use Android Storage Access Framework.
- `Theme`, `Primary Color`, and wallpaper should stay app-wide, not per-game.
- `Enable Cheats`, `Context Fix`, and experimental runtime flags should probably be per-game capable.

---

## 3. Gamepad settings

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Hide Virtual Gamepad | Toggle | Off | Partially covered | Runestone has Gamepad layout mode, but needs a direct in-game/per-game toggle too. |
| Button Opacity | Slider/number | 50 | Exists | Maps to `touchOpacity`; current default differs. |
| Diagonal Movement | Toggle | Off | Needed | Enable 8-direction/diagonal D-pad behavior where supported. |
| Button Size | Slider/number | 100 | Exists | Maps to `touchScale`. |
| Left Button | Key mapping | ENTER / ENTER | Needed | Remappable virtual button. |
| Right Button | Key mapping | ESCAPE / ESCAPE | Needed | Remappable virtual button. |
| Left M Button | Key mapping | F2 / F2 | Needed | Extra/menu key mapping. |
| Right M Button | Key mapping | F8 / F8 | Needed | Extra/menu key mapping. |
| First Button | Key mapping | Z / D | Needed | Remappable action button. |
| Second Button | Key mapping | CTRL_LEFT / C | Needed | Remappable action button. |
| Third Button | Key mapping | Q / V | Needed | Remappable action button. |
| Fourth Button | Key mapping | X / W | Needed | Remappable action button. |
| Fifth Button | Key mapping | SHIFT_LEFT / A | Needed | Remappable shoulder/extra button. |
| Sixth Button | Key mapping | B / S | Needed | Remappable shoulder/extra button. |

Implementation notes:

- JoiPlay shows two values per button, likely keyboard key + gamepad/alternate binding. Runestone should model each button as an action with multiple bindings.
- Add a proper `InputMapping` model instead of hardcoding A/B/X/Y/Start/Select behavior.
- Save mappings globally and per game.
- Add UI for reset-to-default per mapping and per game.
- Add physical controller mapping later using Android `KeyEvent`/`MotionEvent` capture.

Suggested Runestone input action model:

```kotlin
data class InputActionBinding(
    val actionId: String,
    val label: String,
    val keyboardKey: String,
    val alternateKey: String? = null,
    val visibleOnTouchOverlay: Boolean = true,
)
```

---

## 4. Ren'Py settings

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Auto Save | Toggle | Off | Needed | Ren'Py runtime integration required. |
| HW Video | Toggle | On | Needed | Hardware video decoding for Ren'Py playback. |
| Use Prescaled Variant | Toggle | Off | Needed | Ren'Py-specific asset scaling behavior. |
| VSync | Toggle | Off | Needed | Runtime rendering option. |
| Use Low Memory | Toggle | Off | Needed | Memory-saving Ren'Py mode. |
| Low Quality | Toggle | Off | Needed | Lower rendering/media quality. |
| Multi Pixel Reduction | Toggle | On | Needed | Screenshot text is small; exact label may be `Multi Pixel Reduction`. Verify against JoiPlay before implementation. |
| Records Skip | Toggle | Off | Needed | Screenshot text is small; exact behavior unclear. Verify before implementation. |

Implementation notes:

- Runestone currently treats Ren'Py as a future/stub engine. These settings should live behind the Ren'Py plugin/runtime feature gate.
- Do not expose broken Ren'Py-specific toggles until the engine can consume them.

---

## 5. HTML settings

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Use HTTP Server | Toggle | Off | Needed | Serve HTML games through local HTTP instead of `file://`. |
| Preload | Toggle | On | Needed | Exact label is slightly unclear in screenshot; likely preload HTML files/resources. |
| WebGL | Toggle | On | Needed | Enable/disable WebGL in WebView where possible. |
| Desktop Mode | Toggle | Off | Needed | Desktop user agent / viewport behavior. |
| Allow External Modules | Toggle | Off | Needed | Allow loading external JS/modules/assets. Security-sensitive. |

Implementation notes:

- `Use HTTP Server` can help MV/MZ/Tyrano/Construct games that break under `file://` restrictions.
- `Allow External Modules` should default off and show a warning because it can load remote or unexpected code.
- `Desktop Mode` should probably alter user agent, viewport, and screen metrics together.

---

## 6. RPG settings

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Dialog Logs | Toggle | Off | Needed | Capture/show message log history. |
| Use Ruby 1.8 | Toggle | On | Needed | Relevant to XP/VX/VX Ace / mkxp-z compatibility. |
| Custom Font | File picker | Choose | Needed | Custom TTF/OTF font injection. |
| Smooth Scaling | Toggle | On | Field exists | Connect `smoothScaling` to actual renderer/runtime. |
| VSync | Toggle | Off | Needed | Runtime rendering option. |
| Frame Skip | Toggle | Off | Needed | Runtime performance option. |
| Pixel Format Speed | Dropdown/value | Unclear | Needed | Exact screenshot value is unclear. Verify in JoiPlay. |
| Shaders | Toggle | Off | Needed | Enable shader/filter stack where runtime supports it. |
| Path Cache | Toggle | Off | Needed | Cache file/resource path lookup. |
| Reach Path Distance | Toggle | On | Needed | Exact label/meaning unclear. Verify in JoiPlay. |
| Enable Preload Scripts | Toggle | On | Needed | Preload compatibility/injection scripts before game boot. |
| Window Size | Text/value | 640x480 | Needed | Default logical RPG Maker window size. |
| Virtual Screen Alignment | Dropdown | Align to Center of Top Half | Needed | Useful for portrait mode and phone screens. |
| Font Size | Slider/value | 0.75 | Field exists | Connect `textScale`/font scale to runtime. |
| Crop Left Y? | Toggle | Off | Needed | Screenshot label unclear; verify exact label. Could be crop/clip option. |
| Update Graphics | Toggle | Off | Needed | Likely compatibility toggle for RPG Maker graphics update loop. |
| Use WebGL2 | Toggle | Off | Needed | MV/MZ WebView renderer option. |
| Decrypter and Readfiles | Toggle | On | Needed | Support encrypted RPG Maker assets and readfile compatibility. |
| Use Preload JS | Toggle | Off | Needed | Exact label unclear; likely preload JS/plugin injection. |

Implementation notes:

- Split RPG settings into RGSS-native settings and MV/MZ-WebView settings. A single flat RPG section will get confusing.
- Settings like `Use Ruby 1.8`, `Window Size`, shaders, path cache, and font behavior belong to the mkxp-z/RGSS runtime.
- Settings like `Use WebGL2`, preload JS, and decrypter/readfiles belong to MV/MZ WebView runtime.
- `Smooth Scaling`, `Font Size`, and `Window Size` should be per-game because different games need different values.

---

## 7. Essentials settings

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Preserve Files | Toggle | On | Needed | Keep extracted/generated runtime files instead of deleting them. |
| Input Overrides | Toggle | Off | Needed | Per-game input override system. |
| Timers Tied to Input | Toggle | On | Needed | Compatibility/performance timing behavior. |
| Disable Audio Emulation | Toggle | On | Needed | Runtime-specific audio compatibility toggle. |

Implementation notes:

- These are likely compatibility toggles. Store them per game and hide advanced ones by default.
- `Input Overrides` should integrate with the gamepad/action mapping model.

---

## 8. Ruffle settings

| JoiPlay setting | Type | Screenshot value | Runestone status | Notes |
|---|---:|---|---|---|
| Renderer Backend | Dropdown | OpenGL | Needed | Relevant only if Runestone adds Flash/Ruffle support. |
| Quality | Dropdown | High | Needed | Ruffle render quality. |
| Scale Mode | Dropdown | Show All | Needed | Flash stage scaling behavior. |
| Letterbox | Dropdown/toggle | On | Needed | Preserve aspect ratio with letterboxing. |
| Load Behavior | Dropdown | Streaming | Needed | Asset/SWF loading mode. |

Implementation notes:

- Hide this entire section unless a Ruffle/Flash engine plugin is installed.
- Keep Ruffle settings isolated from RPG/WebView settings.

---

## 9. Bottom actions

| JoiPlay action | Type | Runestone status | Notes |
|---|---:|---|---|
| Reset to Default | Button | Needed | Reset current section or current game profile. |
| Clear Runtime Packages | Button | Needed | Delete downloaded/extracted runtime packages/cache. |
| Save/apply floating action | Button | Needed | Runestone currently applies many settings immediately; decide whether to use instant apply or explicit save. |

Implementation notes:

- `Reset to Default` should support both app-wide settings and per-game overrides.
- `Clear Runtime Packages` should show size and confirmation before deletion.

---

## 10. Suggested settings architecture

Recommended layers:

1. `GlobalAppSettings`
   - Theme
   - Primary color
   - Wallpaper
   - Default game folder
   - Experimental features
2. `GlobalRunnerDefaults`
   - Default touch opacity/scale
   - Default key mappings
   - Default scaling/audio/runtime options
3. `PerGameSettings`
   - Overrides for one game/profile
   - Selected engine
   - Input mappings
   - Runtime compatibility flags
4. `EngineSpecificSettings`
   - `RpgRgssSettings`
   - `RpgMvMzSettings`
   - `RenpySettings`
   - `HtmlSettings`
   - `RuffleSettings`

Example model sketch:

```kotlin
data class RunestoneSettings(
    val app: GlobalAppSettings = GlobalAppSettings(),
    val defaults: GlobalRunnerDefaults = GlobalRunnerDefaults(),
    val games: Map<String, PerGameSettings> = emptyMap(),
)

data class PerGameSettings(
    val gameId: String,
    val input: InputSettings = InputSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val audio: AudioSettings = AudioSettings(),
    val rpgRgss: RpgRgssSettings? = null,
    val rpgMvMz: RpgMvMzSettings? = null,
    val renpy: RenpySettings? = null,
    val html: HtmlSettings? = null,
    val ruffle: RuffleSettings? = null,
)
```

---

## 11. Priority order

### P0 — Needed for Runestone usability parity

- Per-game settings profile system
- Hide virtual gamepad toggle
- Button opacity and size saved per game
- Full button remapping
- Default game folder
- Smooth scaling wired to runtime
- Font size/text scale wired to runtime
- Window size / virtual screen alignment
- Reset-to-default

### P1 — High compatibility value

- Use HTTP Server
- WebGL / WebGL2 toggles
- Desktop Mode
- Decrypter and Readfiles
- Path Cache
- Enable Preload Scripts
- Custom Font
- VSync / Frame Skip
- Prevent sleep / lock screen behavior
- Clear runtime packages/cache

### P2 — Engine/plugin-specific parity

- Ren'Py settings
- Ruffle settings
- Ruby 1.8 runtime toggle
- HW Video
- Low memory / low quality modes
- Flash/Ruffle renderer options

### P3 — Nice-to-have polish

- Theme / wallpaper
- Primary color picker
- Animation frames
- Experimental features toggle
- Cheat/debug UI
- Dialog logs

---

## 12. Items that need verification

The screenshot is very narrow, so these labels should be verified against JoiPlay before code implementation:

- `Multi Pixel Reduction`
- `Records Skip`
- `Preload` under HTML settings
- `Pixel Format Speed`
- `Reach Path Distance`
- `Crop Left Y?`
- `Use Preload JS`
- The exact meaning of two-value button mappings such as `Z / D`, `CTRL_LEFT / C`, etc.

Do not block architecture work on these uncertainties; model them as advanced compatibility flags with clear names once verified.
