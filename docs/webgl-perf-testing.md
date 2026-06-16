# WebGL Performance & Testing — Manual Matrix

Branch: `perf/webgl-optimization`
Scope: WebView-based RPG Maker MV / MZ, plus generic HTML5 engines (Tyrano, Construct, Twine, Ruffle, VN Maker) that share the same `WebViewEngine` path.

## Why this doc exists

The change makes the `WebViewEngine` prefer WebGL2 → WebGL1 → canvas, and forwards a previously dead `useWebgl2` setting end-to-end. PIXI v4 (MV) and PIXI v5 (MZ) react differently. WebView version varies wildly across Android OEMs and Chrome updates. There are no instrumentation tests in the repo, so this is the only way to know "does it actually work" before merging.

## Automated coverage (already on the branch)

`./gradlew :app:testDebugUnitTest` runs `WebglConfigBuilderTest`, which covers the pure-Kotlin decision table:

- MV → always WEBGL1
- MZ + `useWebgl2` → WEBGL2 hint
- MZ + no `useWebgl2` → WEBGL1
- `forceCanvas` wins for every engine family
- `webgl=false` → empty query string
- Query string composition per version

This is *not* a substitute for on-device testing — it only proves the Kotlin side makes the right decision. The actual renderer choice is made inside PIXI / the game's own `Graphics._createRenderer`, which the JS bootstrap can influence but not control.

## What the runtime actually does

`WebViewEngine.loadGame` builds a query string via `WebglConfigBuilder.buildQuery(...)`:

| Engine | useWebgl2 | forceCanvas | webgl | Query string (appended to `index.html`) |
|---|---|---|---|---|
| MV | any | false | true | `?webgl=1&renderer=webgl` |
| MZ | true | false | true | `?webgl=1&webgl2=1&renderer=webgl2` |
| MZ | false | false | true | `?webgl=1&renderer=webgl` |
| any | any | true | true | `?webgl=0&renderer=canvas` |
| any | any | any | false | *(empty)* |

The query string is a **hint**. After the page loads, `webgl-bootstrap.js` (injected in `onPageFinished`) probes the actual context, patches PIXI to use the picked renderer, and posts back via `RunestoneBridge.bootDetailed(...)`.

## How to read the logcat line

When the game finishes booting, the Kotlin `Bootstrapper` logs one of:

- `Game booted: WebGL=true WebAudio=true renderer=webgl2 webglVersion=2` — WebGL2 won
- `Game booted: WebGL=true WebAudio=true renderer=webgl webglVersion=1` — WebGL1
- `Game booted: WebGL=true WebAudio=true renderer=canvas webglVersion=0` — fell back to canvas
- `Game booted: WebGL=true WebAudio=true` — old two-arg form (legacy game didn't use new bootstrap)

If you see `Failed to create WebGL context` in the page console, capture it; that's a real failure we need to defend against.

## Devices to test

Minimum: one device with Android 10+, one with Android 6/7/8 (older WebView), one emulator on API 26. The full matrix below is the ideal; if you have time for two devices only, pick the oldest one and the newest one.

## Test matrix (fill in per build)

| # | Game | Engine | Device model | Android ver | WebView ver | useWebgl2 | forceCanvas | integerScaling | FPS avg (gameplay) | FPS avg (battle/menu) | Boot OK | Pixel-art crisp? | Console clean? | Logcat renderer= | Notes / regressions |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Fear & Hunger | MV | … | … | … | on | off | off | | | ✓/✗ | ✓/✗ | ✓/✗ | | |
| 2 | Fear & Hunger | MV | … | … | … | on | off | **on** | | | ✓/✗ | ✓/✗ | ✓/✗ | | tile size, aliasing |
| 3 | Fear & Hunger | MV | … | … | … | n/a | **on** | off | | | ✓/✗ | n/a | ✓/✗ | | force-canvas fallback |
| 4 | (any MZ title) | MZ | … | … | … | **on** | off | off | | | ✓/✗ | n/a | ✓/✗ | | confirm `webglVersion=2` |
| 5 | (any MZ title) | MZ | … | … | … | **off** | off | off | | | ✓/✗ | n/a | ✓/✗ | | confirm `webglVersion=1` |
| 6 | (pixel-art MZ) | MZ | … | … | … | on | off | **on** | | | ✓/✗ | ✓/✗ | ✓/✗ | | no blur, no antialias |
| 7 | TyranoBuilder game | HTML | … | … | … | on | off | off | | | ✓/✗ | n/a | ✓/✗ | | |
| 8 | (broken game) | any | … | … | … | on | **on** | off | | | ✓/✗ | n/a | ✓/✗ | | emergency escape hatch |

## Procedure

For each row:

1. Build: `./gradlew :app:assembleDebug`
2. Install on the device, `adb logcat -c && adb logcat | grep -E 'Runestone|chromium|WebGL'` running.
3. Launch the game from the home screen.
4. Wait until the title screen is visible. Note FPS overlay (top-right, monospace, green).
5. Play 5 minutes in the heaviest area (battle / large map / animation).
6. Watch logcat for `Game booted: …` line and capture the `renderer=` value.
7. Open Chrome `chrome://inspect` (or `adb logcat | grep -i 'console'` for in-page errors). Note any `WebGL` errors.
8. For pixel-art rows, take a screenshot and zoom to a sprite edge — verify no blur, no sub-pixel shimmer.
9. Hit Home button, return to the game. Note whether WebGL context is lost (should be — `onResume` re-injects; verify by FPS dropping to 0 and recovering within 1 second).
10. Fill the row in the matrix above. For "✗", capture the failure mode (black screen, blank sprites, FPS < 30, etc.).

## Known regressions to watch for

- **PIXI v4 + WebGL2 forcing**: MV games must not be force-promoted to WebGL2. The decision table hard-codes MV → WEBGL1; verify row 1 in the matrix still picks `webglVersion=1`.
- **`forceCanvas` regression**: when forced, the game should boot to a working 2D canvas. Some MV games crash without WebGL; if you find one, document the game in this doc and we'll consider a per-game `forceCanvas` default override.
- **WebGL context loss on resume**: the `onResume` path calls `webViewEngine?.resumeTimers()` and `onResume()` but does **not** re-inject `webgl-bootstrap.js`. If a game loses context, it may render to a black canvas until the next scene change. Watch row 9.
- **Hi-DPI resolution clamp**: phones with DPR 3 (Samsung Galaxy S-line) should now cap at resolution 2. Watch for "everything is blurry" reports — that's the clamp too aggressive, fix is to bump to 3.
- **Audio regression**: `webgl-bootstrap.js` runs in `onPageFinished`, *after* `forceAudioExt` and `localStorage` shims. None of its code touches WebAudio, so audio should be unaffected. If audio breaks, bisect by disabling the new injection.

## Out of scope (don't test here)

- mkxp-z (RGSS) — separate native engine, has its own filter chain in `feature/visual-filters`.
- EasyRPG — different renderer entirely.
- Ren'Py / Godot — bundled native wrappers, not WebView.
- Store, downloads, RTP, save sync.
- Phase 6 PixiJS filter bridge from `docs/visual-filters-roadmap.md` — that's a future, larger change.

## When you're done

Commit the filled-in matrix to the branch as `docs/webgl-perf-testing-results.md` and reference it from the PR description. If any row fails, do **not** revert the change blindly — open a sub-task and document the failure mode in the matrix notes; the safest rollback is to flip the per-game `useWebgl2` override to off.
