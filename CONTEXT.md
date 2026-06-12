# Runestone Visual Filters

Runestone is a multi-engine game launcher for Android. This context covers the visual filter and post-processing system that improves game readability and aesthetics across all supported engines.

## Language

**Preset**:
A named collection of filter passes with default parameters, presented to the user as a single choice (e.g. "Clean Sharp", "CRT Lite"). Resolved by FilterManager into a concrete pass list with computed uniform values.
_Avoid_: Filter, shader pack, effect profile

**Pass**:
A single shader execution step within a preset's filter chain. Each pass has a shader name, a set of parameters, and executes as a fullscreen quad draw. Passes are ordered and sequential.
_Avoid_: Stage, step, effect unit

**Filter Chain**:
The ordered sequence of passes that a preset resolves to. Executed by the native renderer using ping-pong framebuffers. A preset with no passes (or `"enabled": false`) produces no visual change.
_Avoid_: Pipeline, pass list, render chain

**Override**:
A per-game parameter value that replaces a preset's default for that parameter. Stored as absolute values. A slider at its preset default is not an override — only user-changed values are persisted. Each override can be individually reset.
_Avoid_: Tweak, adjustment, custom value

**Engine Capability**:
A descriptor of what filter features a given engine supports — multi-pass shaders, CSS injection, maximum pass count, GL backend type. Used by the UI to hide or grey out unsupported presets.
_Avoid_: Adapter, render profile, engine profile

**Aspect Mode**:
How the game viewport is fitted to the screen. Handled by a hybrid model: C++ sets `glViewport()` for letterbox/pillarbox/integer-center, and shaders receive `uUVScale`/`uUVOffset` for crop mode.
_Avoid_: Scale mode, display mode, fit mode

**Performance Tier**:
A cost classification for a preset or pass (Tier 0 through Tier 4). Tier 0 is free (passthrough), Tier 4 is experimental (AI upscaling). Used to warn users on low-end devices.
_Avoid_: Quality level, GPU cost

**Filter Config**:
A JSON file written by Kotlin and read by native C++ code. Contains the preset ID, aspect mode, and the resolved pass list with all parameters. The contract between the settings layer and the rendering layer.
_Avoid_: Shader config, render config
