# Layout, Controls, and Dual Viewport Roadmap

> **STATUS (2026-06-12): ARCHIVED — IMPLEMENTED.** Sections 1–4 of this roadmap
> have been implemented through the `feat/phase-a-layout-simplification` …
> `feat/phase-d-control-layout-editor` chain (now merged). The runtime
> customization portions are still in active development on
> `fix/runtime-menu-native-layout-polish`. This file is kept for design
> history only. See `VISION-ROADMAP.md` for current status.

## 1. Simplify Layout Modes

Runestone should treat screen layout and input visibility as separate concepts.

Current user-facing layout choices should collapse to:

- `Portrait`
- `Landscape`

`Gamepad` should stop being a layout mode. It is really `controls hidden` plus physical controller support.

Recommended model:

```text
Visual layout:
  Portrait
  Landscape

Runtime controls:
  On
  Off
  Auto later, optional
```

When controls are off:

- The game still accepts physical controllers.
- The game can still receive normal touch/tap input where the engine supports it.
- A small hidden/top runtime bar can bring controls back.
- The runtime menu remains reachable by controller shortcut and by touch.

## 2. Runtime Control Toggle

The next implementation phase should move control visibility into the in-game runtime UI instead of making users leave the game and change per-game settings.

Target behavior:

```text
Swipe/tap hidden top bar
↓
Runtime menu opens
↓
Toggle Controls: On / Off
↓
Overlay appears or disappears immediately
```

This should apply to:

- WebView MV/MZ/HTML engines
- mkxp-z RGSS XP/VX/VX Ace
- EasyRPG 2000/2003, if its wrapper can hide/show the touch layer

Persistence:

- Runtime toggle should update the current game profile.
- Temporary session-only toggle can be added later if needed.

## 3. Runtime Layout Rotation

Users should not need to quit a game, edit global settings, and relaunch just to switch between portrait and landscape.

Target behavior:

```text
Open runtime top bar
↓
Tap Rotate Layout
↓
Current running game switches Portrait ↔ Landscape
↓
Game keeps running
↓
The chosen layout is saved to this game's profile
```

Feasibility by engine:

- WebView engines: highest feasibility. MV/MZ/HTML/Tyrano/Construct can usually keep the same WebView alive while Runestone changes requested orientation and moves/resizes the WebView and overlay containers.
- mkxp-z: feasible, but needs native-wrapper work. SDL surfaces can survive orientation changes if the wrapper controls orientation and relayout carefully. The current mkxp-z wrapper already has portrait/landscape layout logic, but it is launch-time logic today.
- EasyRPG 2000/2003: possible, but must be investigated. EasyRPG's Android activity has its own orientation/display assumptions, so live switching may require wrapper or upstream integration.
- Ren'Py: defer until the Ren'Py runtime path is stable.

Implementation notes:

- Add a runtime action: `Rotate`.
- Add a shared runtime method: `setRuntimeLayout(LayoutMode)`.
- For WebView: keep the engine view alive and rebuild only the Android layout containers and touch overlay.
- For mkxp-z: add a Java bridge method in the native wrapper that requests orientation and reattaches the gamepad/portrait controls without restarting SDL.
- For EasyRPG: prototype with a running game and verify whether surface recreation restarts the interpreter.
- Persist successful runtime rotation to per-game config, not global settings.

Fallback behavior:

- If an engine cannot rotate live, show a small `Restart required` action and save the new per-game layout for next launch.

## 4. Global, Engine, and Per-Game Inheritance

Settings should be layered:

```text
App defaults
↓
Global user defaults
↓
Engine defaults
↓
Per-game overrides
↓
Session/runtime temporary state
```

Example:

- Global default layout is `Landscape`.
- User imports a new MZ game.
- It opens in landscape by default.
- User changes only that game to portrait in per-game settings or from the runtime rotate button.
- Future newly imported games still open in landscape.
- That one MZ game opens in portrait from then on.

This same model should apply to:

- Visual layout.
- Control visibility.
- Touch opacity and scale.
- Button layout.
- Button mappings.
- Controller mappings.
- Keyboard layout.
- Engine-specific runtime flags.
- Theme/profile overrides later.

## 5. Advanced Virtual Controller System

The current fixed virtual controller should become a configurable input layout system.

Target capabilities:

- Any number of buttons.
- D-pad, analog stick, button, toggle, radial/menu, and gesture zones.
- Per-button key/action mapping.
- Per-button size, position, opacity, color, corner radius, label, icon, and font size.
- Optional SVG/icon selection for buttons.
- Per-button haptic strength.
- Optional per-button sound effect.
- Separate portrait and landscape layouts.
- Separate keyboard overlay layouts.
- Engine presets, inherited by games.
- Per-game edits that override only changed fields.

Data model direction:

```text
ControlProfile
  id
  name
  engineScope: global | rgss | easyrpg | mv_mz | renpy | html
  portraitLayout
  landscapeLayout
  keyboardLayout
  theme

ControlElement
  id
  type: dpad | button | stick | gesture | menu | spacer
  action
  rect / anchor / size
  label
  icon
  style
  haptics
  sound
```

Inheritance:

```text
Global control profile
↓
Engine control profile
↓
Game control profile
```

This avoids duplicating an entire layout for every game when only one button changes.

Implementation phases:

### Phase 1: Profile Storage

- Add `ControlProfile`, `ControlLayout`, and `ControlElement` models.
- Store global, engine, and per-game profiles.
- Keep current fixed controls as the default generated profile.

### Phase 2: Runtime Controls Toggle and Rotate

- Add runtime top-bar controls for `Controls On/Off`, `Rotate`, `Keyboard`, and `Home`.
- Persist layout/control changes to the current game profile.

### Phase 3: Layout Editor

- Drag buttons.
- Resize buttons.
- Change opacity/scale.
- Reset to engine/global profile.

### Phase 4: Styling

- Colors, radius, font size, label/icon, SVG icon choice.
- Optional haptic strength and button sounds.

### Phase 5: Full Input Mapping

- Per-button actions.
- Keyboard/gamepad binding capture.
- Engine-specific actions, such as confirm/cancel/menu/page/fast-forward.

## 6. Virtual Keyboard Profiles

The virtual keyboard should follow the same profile system as the controller.

Target capabilities:

- Portrait keyboard profile.
- Landscape keyboard profile.
- Compact/numpad/symbol layers.
- User-editable key size, rows, symbols, labels, icons, haptics, and sounds.
- Controller navigation mode for keyboard focus.
- Engine presets for common text-entry behavior.

This matters because some games use custom text input, and a single fixed keyboard will never feel right everywhere.

## 7. App Theme and UI Customization

Long term, Runestone can support user-made UI themes in the same layered way.

Possible user-customizable areas:

- App background images.
- Background image cycling.
- Home/card styles.
- Button colors and borders.
- Glass strength/opacity.
- Font choices.
- Accent color.
- Runtime top bar styling.
- Controller/keyboard profile styling.

Theme packs should be data-driven, not code plugins at first.

Possible format:

```text
theme-pack/
  theme.json
  backgrounds/
  icons/
  sounds/
```

Security and UX rules:

- No executable theme code in the first version.
- Validate paths and file sizes.
- Allow reset to built-in theme.
- Keep app-critical buttons readable even with custom styling.

## 8. Dual Viewport / Dual Screen Idea

This is feasible in some engines, but it should be treated as experimental engine-specific work, not a generic launcher feature.

### MV/MZ

Best candidate.

MV/MZ already render through a browser canvas. A plugin can render additional scenes, tilemaps, sprites, or camera views into another canvas or viewport.

Possible uses:

- Main screen plus minimap.
- Main screen plus inventory/status panel.
- Two side-by-side cameras for two characters.
- Local multiplayer experiments with independent camera targets.

Implementation path:

```text
Runestone optional plugin
↓
Inject preload JS into MV/MZ game
↓
Create secondary canvas or render texture
↓
Expose layout options: split horizontal / split vertical / picture-in-picture
```

Risk:

- Game-specific plugins can conflict with scene/camera code.
- Dual control routing needs per-game scripting.
- Performance depends on map size, effects, and plugin stack.

### VX Ace / XP / VX through mkxp-z

Possible, but harder.

RGSS normally assumes one `Graphics` screen and one active scene. Dual viewports would require Ruby script patches and/or mkxp-z runtime support.

Possible approaches:

- Ruby script injection that creates secondary `Viewport` objects and draws a minimap/status scene.
- mkxp-z render-target support for a second camera, if exposed to RGSS.
- Game-specific patches for inventory/map panels.

Risk:

- Many RGSS games monkeypatch scenes/windows.
- Two independent active maps/cameras is not a normal RGSS model.
- Save/state assumptions can break if two playable actors are advanced separately.

### RPG Maker 2000/2003 / EasyRPG

Hardest candidate.

EasyRPG is a native interpreter for the original 2k/2k3 runtime. A second viewport would likely require EasyRPG engine changes rather than a light script patch.

Possible limited versions:

- Native minimap overlay from map data.
- Debug/status panel.
- Split view only for custom-compatible games later.

Risk:

- No standard script layer like MV/MZ.
- Engine-level rendering and event interpreter assumptions are single viewport.

## 9. Recommended Phasing

### Phase A: Layout Simplification

- Remove `Gamepad` from visible layout choices.
- Migrate old `GAMEPAD` settings to `Landscape + controls off`.
- Keep enum compatibility internally until old saved settings are migrated.
- Update global and per-game settings labels.

### Phase B: In-Game Runtime Bar

- Add `Controls On/Off` to runtime top bar / quick menu.
- Add `Rotate Portrait/Landscape`.
- Apply immediately in WebView, mkxp-z, and EasyRPG where supported.
- Persist to per-game config.

### Phase C: Control Profiles

- Add global, engine, and per-game control profile storage.
- Convert the current fixed overlay into the default generated profile.
- Add simple import/export later.

### Phase D: Control Layout Editor

- Move, resize, restyle, and remap controls.
- Support separate portrait and landscape layouts.
- Add keyboard profile editing later.

### Phase E: Theme Packs

- Add theme JSON and local media assets.
- Support custom backgrounds and button styles.
- Keep themes data-only at first.

### Phase F: MV/MZ Dual Viewport Prototype

- Add an experimental MV/MZ preload plugin.
- Start with read-only second viewport/minimap, not multiplayer.
- Gate behind experimental settings.

### Phase G: Engine-Specific Dual Viewport Research

- Investigate mkxp-z render target or Ruby viewport injection.
- Investigate EasyRPG minimap/status overlay feasibility.
- Do not promise generic dual camera for every engine.

## 10. Product Decision

The clean near-term direction is:

```text
Portrait or Landscape
+ controls toggle inside runtime
+ rotate layout inside runtime where supported
+ global → engine → game inheritance
+ controller shortcuts always available
```

Dual viewport should be a later experimental feature, starting with MV/MZ because the browser/canvas runtime gives the safest prototype path.
