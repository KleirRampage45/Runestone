# Visual Filters Roadmap

Runestone multi-engine visual filter and post-processing system.
All phases build on the same data model and config bridge established in Phase 1.

## Architecture Summary

**Config bridge:** Kotlin writes `runestone-filters.json` to the game directory before engine launch. Native C++ reads it at startup and checks for file changes each frame. WebView engines receive CSS filter values via `evaluateJavascript()`.

**Data model (already exists on `feature/visual-filters` branch):**
- `FilterModels.kt` — `FilterPreset`, `FilterPassTemplate`, `ResolvedFilterConfig`, `ResolvedPass`, `AspectMode`, `PerfTier`
- `FilterManager.kt` — preset registry, override merge, `resolve(video)` produces final config
- `FilterConfigWriter.kt` — writes JSON to game directory
- `PerGameConfig.VideoSection` — extended with `screenFilter`, `gamma`, `saturation`, `sharpness`, `aspectMode`

**Injection points:**
- mkxp-z: native C++ in `graphics.cpp` after scene composite, before screen blit (ping-pong FBO chain)
- WebView engines: CSS `filter` property injected via `evaluateJavascript()`
- MV/MZ: PixiJS `ColorMatrixFilter` or custom filter on the Pixi stage
- EasyRPG: separate native library, own injection point
- Ren'Py/Godot: engine-specific wrappers

---

## Phase 1: Clean Sharp on mkxp-z (FOUNDATION)

**Status:** Source complete on `feature/visual-filters` branch. Needs proper rebase onto `feat/mkxpz-controller-overhaul`.

**What it delivers:**
- `FilterChain` C++ class with ping-pong FBO rendering, shader registry, JSON config reader
- 5 GLSL shaders: `filterQuad.vert`, `filterPassthrough.frag`, `filterSharpBilinear.frag`, `filterBrightnessContrast.frag`, `filterSharpen.frag`
- Injection in `graphics.cpp::redrawScreen()` after `screen.composite()` before blit
- Kotlin preset picker in per-game settings (tap-to-cycle: Off / Clean Sharp)
- Sliders for brightness, contrast, gamma, saturation, sharpness
- Config JSON bridge (`runestone-filters.json`)

**Files to rebase (from `feature/visual-filters`):**
- `app/src/main/java/com/runestone/app/filters/FilterModels.kt` (NEW)
- `app/src/main/java/com/runestone/app/filters/FilterManager.kt` (NEW)
- `app/src/main/java/com/runestone/app/filters/FilterConfigWriter.kt` (NEW)
- `app/src/main/java/com/runestone/app/data/PerGameConfig.kt` (MODIFIED — extend VideoSection)
- `app/src/main/java/com/runestone/app/ui/PerGameSettingsScreen.kt` (MODIFIED — add preset picker + sliders)
- `app/src/main/java/com/runestone/app/GameActivity.kt` (MODIFIED — write config before launch)
- `native/mkxp-z-android/app/jni/mkxp-z/src/display/filter_chain.h` (NEW)
- `native/mkxp-z-android/app/jni/mkxp-z/src/display/filter_chain.cpp` (NEW)
- `native/mkxp-z-android/app/jni/mkxp-z/src/display/graphics.cpp` (MODIFIED — injection point)
- `native/mkxp-z-android/app/jni/mkxp-z.mk` (MODIFIED — add filter_chain.cpp)
- `native/mkxp-z-android/app/jni/mkxp-z/shader/filter*.vert`, `filter*.frag` (NEW, 5 files)
- `native/mkxp-z-android/app/jni/mkxp-z/make_xxd.sh` (NEW — embed shaders as byte arrays)

**Known issues from first build (already fixed on `feature/visual-filters`):**
- `shader_` prefix mismatch between xxd-generated arrays and C++ code
- `#include "util/encoding.h"` removed (breaks build)
- Dead vertex shader code in `compile()` removed
- `const std::map` + `operator[]` changed to `.at()`
- `quadIBO` stored as member, cleaned up in destructor
- `drawQuad()` must use `gl.DrawElements()` with IBO, not `gl.DrawArrays()` (GLFunctions wrapper does not expose DrawArrays)

**Done when:**
- mkxp-z RPG Maker game runs with Clean Sharp filter active
- User can switch Off / Clean Sharp from per-game settings
- Adjusting sliders produces visible changes at runtime
- No crash if shader compilation fails (fallback to passthrough)
- Filter does not affect virtual controls overlay (separate Activity)

---

## Phase 2: CSS Filters for WebView Engines

**Goal:** Apply brightness, contrast, gamma, saturation, sharpness to any WebView-based engine via CSS.

**Engines covered:** MV, MZ, TyranoBuilder, Construct, HTML5 generic, Twine, VN Maker, Ruffle (Flash).

**Approach:**
- After WebView loads, inject CSS `filter` property on the `body` or game container element
- CSS supports `brightness()`, `contrast()`, `saturate()`, `sepia()`, `hue-rotate()`, `grayscale()`, `invert()`, `opacity()`
- CSS does NOT natively support gamma or sharpen — use `contrast()` + `brightness()` as approximation for gamma, skip sharpen for Phase 2
- Re-inject on config change (poll `runestone-filters.json` or receive from Kotlin via `evaluateJavascript()`)

**Implementation:**
- Create `WebViewFilterBridge.kt` utility that reads filter config and generates CSS string
- Wire into each WebView engine's `onPageFinished()` callback
- For MV/MZ: also inject into PixiJS stage if accessible (better quality than CSS)

**New files:**
- `app/src/main/java/com/runestone/app/filters/WebViewFilterBridge.kt` (NEW)

**Modified files:**
- Each WebView engine activity (add `onPageFinished` hook)

**Done when:**
- RPG Maker MV game runs with Clean Sharp-like CSS filter
- Filter changes take effect without restarting the game
- No visual artifacts on games with overlays or menus

---

## Phase 3: More Presets + Full UI

**Goal:** Add Pixel Perfect, Soft Smooth, Text Clarity, Dark Game Boost presets. Full dropdown UI for preset selection.

**Presets:**

| Preset | Passes | Tier | Description |
|--------|--------|------|-------------|
| Off | 0 | 0 | No filtering |
| Clean Sharp | 3 (sharp_bilinear + brightness_contrast + sharpen) | 1 | Default. Sharp pixels, mild contrast |
| Pixel Perfect | 1 (passthrough + integer scaling) | 0 | Integer-centered, nearest-neighbor |
| Soft Smooth | 1 (bilinear + mild blur) | 1 | Anti-aliased look for 3D RPG Maker games |
| Text Clarity | 2 (sharp_bilinear + heavy sharpen) | 1 | Optimized for text-heavy VNs |
| Dark Game Boost | 2 (brightness_contrast + gamma lift) | 1 | Brightens dark scenes for outdoor visibility |

**UI improvements:**
- Replace tap-to-cycle with proper dropdown (PopupWindow or Spinner)
- Per-slider reset button (restore preset default value)
- Preset description shown below picker
- Performance tier badge (color-coded: green/yellow/orange/red)
- Engine compatibility check — grey out presets incompatible with current engine

**Done when:**
- All 5 presets selectable from dropdown
- Each slider has a reset-to-default button
- Preset description and tier visible in UI
- Incompatible presets greyed out for current engine

---

## Phase 4: Retro Effects (Multi-Pass)

**Goal:** CRT, scanlines, handheld LCD effects. Multi-pass chains that combine color + texture + curvature.

**Presets:**

| Preset | Passes | Tier | Description |
|--------|--------|------|-------------|
| CRT Lite | 3 (brightness_contrast + scanlines + vignette) | 2 | Scanlines + slight vignette. Light enough for phones |
| CRT Strong | 5 (phosphor mask + scanlines + curvature + vignette + color bleed) | 3 | Full CRT simulation. May lag on low-end |
| Handheld LCD | 3 (pixel grid + color quantization + slight green tint) | 2 | Game Boy / GBA LCD look |
| Scanlines Only | 1 (scanline overlay) | 1 | Just scanlines, no other effects |

**New shaders:**
- `scanlines.frag` — horizontal scanline pattern with adjustable intensity and count
- `vignette.frag` — radial darkening at edges
- `crt_phosphor.frag` — RGB phosphor dot/shadow mask pattern
- `crt_curvature.frag` — barrel distortion (screen warp)
- `pixel_grid.frag` — visible pixel grid for handheld LCD effect
- `color_quantization.frag` — reduce color depth for retro look

**Technical requirements:**
- Ping-pong FBO chain must support 5+ passes without performance issues
- Scanline count must adapt to actual game resolution (not screen resolution)
- Barrel distortion needs `uUVScale`/`uUVOffset` to avoid stretching artifacts at edges

**Done when:**
- CRT Lite runs at 60fps on mid-range phone
- Scanlines scale correctly with game resolution
- Barrel distortion clips properly at screen edges

---

## Phase 5: EasyRPG Pipeline

**Goal:** Apply visual filters to EasyRPG (RPG Maker 2000/2003) games.

**Investigation needed:**
- EasyRPG uses its own rendering pipeline (SDL2 + custom C++ renderer)
- Check if `libeasyrpg_android.so` has a similar FBO chain to mkxp-z
- EasyRPG source: `native/easyrpg-android/` submodule
- Possible injection points: `Output::Present()`, SDL renderer backend, or `Scene::Draw()`

**Approach:**
- If FBO chain exists: reuse FilterChain from Phase 1 (link against same object files)
- If no FBO: capture SDL surface, upload to GL texture, run filter chain, blit back
- Worst case: separate `libfilterchain.so` that intercepts via LD_PRELOAD (fragile)

**Done when:**
- EasyRPG game runs with Clean Sharp filter
- Same presets available as mkxp-z

---

## Phase 6: PixiJS Injection for MV/MZ

**Goal:** Apply filters directly inside the PixiJS rendering pipeline for RPG Maker MV/MZ, bypassing CSS limitations.

**Approach:**
- MV/MZ use PixiJS v4/v5 for rendering
- PixiJS has `PIXI.filters.ColorMatrixFilter` with brightness, contrast, saturation, hue
- Custom `PIXI.Filter` subclass for sharpen (convolution kernel on the fragment shader)
- Inject via `evaluateJavascript()` after PixiJS initializes:
  ```javascript
  var cf = new PIXI.filters.ColorMatrixFilter();
  cf.brightness(1.0, false);
  cf.contrast(0.05, false);
  $gameSystem._filters = [cf];
  ```

**Challenges:**
- MV uses PixiJS v4, MZ uses PixiJS v5 — API differences
- Filter must survive scene transitions (map change, battle, menu)
- Some MV plugins override the renderer or use custom containers
- Need to detect PixiJS version and adapt injection code

**New files:**
- `app/src/main/java/com/runestone/app/filters/PixiFilterBridge.kt` (NEW)

**Done when:**
- MV game with default renderer shows Clean Sharp effect
- Effect survives map transitions and menu open/close
- MZ game works with same approach (different PixiJS version)

---

## Phase 7: Ren'Py + Godot

**Goal:** Apply filters to Ren'Py and Godot engine games.

### Ren'Py

- Ren'Py on Android runs via `librenpython.so` (embedded Python + SDL2)
- Ren'Py has its own GL renderer (`renpy.display.render`)
- Possible injection: modify `renpy/common/00gl.rpy` to add post-processing
- Alternative: capture SDL2 surface, apply FilterChain, blit back
- Ren'Py wrapper (existing `RenpyActivity`) may allow intercepting the GL context

### Godot

- Godot on Android uses its own GL/Vulkan renderer
- Optional engine (142MB .so) — only available if enabled in Addons
- Possible injection: Godot has `CanvasItem` shader override
- Alternative: same SDL2 surface capture approach

**Done when:**
- Ren'Py game shows filter effect
- Godot game shows filter effect (or graceful "unsupported" message)
- Both engines fall back to passthrough if injection fails

---

## Phase 8: Advanced Filters

**Goal:** Heavy multi-pass effects and experimental features.

**Presets:**

| Preset | Passes | Tier | Description |
|--------|--------|------|-------------|
| CRT Strong | 5-6 | 3 | Full CRT emulation with phosphor, curvature, bloom |
| NTSC Composite | 3 | 3 | NTSC artifact simulation (rainbow fringing) |
| xBRZ Upscale | 1 (heavy) | 4 | Pixel art upscaling shader (GPU-based) |
| Color Blind Assist | 1-2 | 1 | Daltonization filters for protanopia/deuteranopia/tritanopia |
| High Contrast | 1 | 1 | Accessibility: boosted contrast + edge detection |

**New shaders:**
- `ntsc_composite.frag` — YIQ color space separation + chroma blur
- `xbrz.frag` — GPU-based xBRZ edge detection (simplified)
- `daltonize.frag` — color blind correction matrices
- `edge_detect.frag` — Sobel edge detection for high contrast mode
- `bloom.frag` — threshold + blur + additive blend

**Done when:**
- CRT Strong at 30+ fps on high-end phone
- Color blind filters produce correct color mapping
- Performance tier warnings shown for Tier 3+ presets

---

## Preset Catalog (All Phases)

| Preset | Phase | Passes | Tier | Engines |
|--------|-------|--------|------|---------|
| Off | 1 | 0 | 0 | All |
| Clean Sharp | 1 | 3 | 1 | All |
| Pixel Perfect | 3 | 1 | 0 | Native only |
| Soft Smooth | 3 | 1 | 1 | All |
| Text Clarity | 3 | 2 | 1 | All |
| Dark Game Boost | 3 | 2 | 1 | All |
| Scanlines Only | 4 | 1 | 1 | Native |
| CRT Lite | 4 | 3 | 2 | Native |
| CRT Strong | 4/8 | 5 | 3 | Native |
| Handheld LCD | 4 | 3 | 2 | Native |
| NTSC Composite | 8 | 3 | 3 | Native |
| xBRZ Upscale | 8 | 1 | 4 | Native |
| Color Blind Assist | 8 | 2 | 1 | All |
| High Contrast | 8 | 1 | 1 | All |

---

## Shader Inventory

### Already written (Phase 1, on `feature/visual-filters`):
- `filterQuad.vert` — fullscreen passthrough vertex shader
- `filterPassthrough.frag` — identity (fallback)
- `filterSharpBilinear.frag` — integer-aware scaling with sharpness control
- `filterBrightnessContrast.frag` — brightness, contrast, gamma, saturation
- `filterSharpen.frag` — 3x3 unsharp mask

### To write (Phase 4+):
- `scanlines.frag`
- `vignette.frag`
- `crt_phosphor.frag`
- `crt_curvature.frag`
- `pixel_grid.frag`
- `color_quantization.frag`
- `ntsc_composite.frag`
- `xbrz.frag`
- `daltonize.frag`
- `edge_detect.frag`
- `bloom.frag`
