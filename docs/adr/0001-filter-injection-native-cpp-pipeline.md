# 0001 — Visual filter injection via native C++ post-processing pass

mkxp-z runs as a separate Activity with its own SDL SurfaceView and OpenGL ES context. Runestone's GameActivity has no access to its rendering surface. We decided to inject visual filters directly into mkxp-z's native C++ rendering pipeline (`graphics.cpp`), adding a post-processing pass after the final scene composite but before the screen blit.

## Considered Options

- **A — Native C++ pass in graphics.cpp.** Add a shader pass between the front buffer FBO and `blitBeginScreen`. The existing ping-pong FBO chain makes this natural. Settings are bridged via a JSON config file that native code reads at runtime.
- **B — Intercept at SDLSurface level.** Replace SurfaceView with TextureView so Kotlin-side code can post-process the texture. Rejected because SDL2's Android backend is tightly coupled to SurfaceView, adds latency, and TextureView has worse GPU performance.
- **C — Android SurfaceControl/shell-level capture.** Engine-agnostic but requires high API levels, adds latency, and has poor device compatibility.

## Why A

The C++ FBO chain is the natural injection point. mkxp-z already does ping-pong rendering for its own effects (gray tone, flash, transitions). Adding one more pass before the final blit is architecturally consistent. The native submodule is under our control. No cross-Activity surface hacks. No added compositing latency.

## Consequences

- Filter shaders are GLSL loaded from Android assets at runtime by native code.
- Settings bridge requires writing filter config to a JSON file (similar to existing `mkxp.json`) that native code reads.
- Each additional native engine (EasyRPG, Ren'Py, ONScripter) will need its own injection point investigation — the approach may or may not transfer.
- WebView engines (MV, MZ, Tyrano, etc.) will use a completely different path (CSS filters / PixiJS injection).
