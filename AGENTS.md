# Agent Instructions — Runestone

## CodeGraph

Use CodeGraph for structural code questions:

- `codegraph_context` for feature/architecture context
- `codegraph_search` for symbol lookup
- `codegraph_callers` / `codegraph_callees` for call relationships
- `codegraph_trace` for flow questions
- `codegraph_impact` for change radius
- `codegraph_files` for indexed project structure

After adding or editing source files, run `codegraph sync` or `codegraph index` before relying on CodeGraph results.

## Project Rules

- **No copyrighted game files** — never commit RTP assets, game data, official art, screenshots, or music.
- **Keep imported game data out of git** — workspace/games directories are in `.gitignore`.
- **License compliance** — Runestone is GPLv2+. All source files must include the GPL header. Third-party components must be documented in THIRD_PARTY.md.
- **Inherited from Grimmobile** — Core launcher architecture (SAF import, workspace isolation, settings) is ported from blacksouls-android. Keep the patterns consistent unless there's a reason to diverge.
- **Engine-agnostic** — GameId is not hardcoded to specific titles. Engine detection must work for any RPG Maker game.

## Build

```bash
./gradlew assembleDebug        # Build APK
./gradlew installDebug         # Install on connected device
```

## Branches

- `master` — stable, working builds
- `development` — active work (default)
