# Layout, Controls, and Dual Viewport Roadmap

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

## 3. Dual Viewport / Dual Screen Idea

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

## 4. Recommended Phasing

### Phase A: Layout Simplification

- Remove `Gamepad` from visible layout choices.
- Migrate old `GAMEPAD` settings to `Landscape + controls off`.
- Keep enum compatibility internally until old saved settings are migrated.
- Update global and per-game settings labels.

### Phase B: In-Game Controls Toggle

- Add `Controls On/Off` to runtime top bar / quick menu.
- Apply immediately in WebView, mkxp-z, and EasyRPG where supported.
- Persist to per-game config.

### Phase C: MV/MZ Dual Viewport Prototype

- Add an experimental MV/MZ preload plugin.
- Start with read-only second viewport/minimap, not multiplayer.
- Gate behind experimental settings.

### Phase D: Engine-Specific Dual Viewport Research

- Investigate mkxp-z render target or Ruby viewport injection.
- Investigate EasyRPG minimap/status overlay feasibility.
- Do not promise generic dual camera for every engine.

## 5. Product Decision

The clean near-term direction is:

```text
Portrait or Landscape
+ controls toggle inside runtime
+ controller shortcuts always available
```

Dual viewport should be a later experimental feature, starting with MV/MZ because the browser/canvas runtime gives the safest prototype path.
