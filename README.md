# Runestone

**Multi-engine RPG Maker game launcher for Android.**

Import, organize, and play RPG Maker games on your Android device. Supports:

- **XP / VX / VX Ace** — via mkxp-z (native arm64-v8a runtime)
- **MV / MZ** — via Android WebView (Chromium-based, no extra runtime needed)

Inherits architecture from [Grimmobile](https://github.com/KleirRampage45/blacksouls-android), a specialized Black Souls launcher, generalized to be engine-agnostic.

## Goals

- Lightweight APK (~40 MB with mkxp-z, ~5 MB without)
- No external dependencies — all runtimes embedded or using system APIs
- Import games via Android SAF (Storage Access Framework)
- Per-game settings, workspace isolation, save management
- Virtual gamepad and touch overlay
- Clean, modern UI

## Architecture

```
Runestone Launcher
├── EngineDetector       → Detects RPG Maker engine from game files
├── mkxp-z (native)      → XP / VX / VX Ace runtime (.so)
├── WebView (system)     → MV / MZ runtime (Chromium via Android WebView)
├── SAF Importer         → Import games from any file manager
├── Workspace Manager    → Isolated storage per game
└── UI                   → Home, Settings, Import, Game Activity screens
```

## License

GNU General Public License v2 or later — see [LICENSE](LICENSE).

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

### Third-Party Components

| Component | License | Description |
|---|---|---|
| mkxp-z | GPLv2+ | RGSS runtime for XP/VX/VX Ace games |
| Android WebView | Apache 2.0 / BSD | System component for MV/MZ HTML5 rendering |

See [THIRD_PARTY.md](THIRD_PARTY.md) for details.
