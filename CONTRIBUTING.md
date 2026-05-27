# Contributing to Runestone

Thank you for your interest in contributing to Runestone! This document provides guidelines and information for contributors.

## Code of Conduct

- Be respectful and constructive
- Focus on technical merit, not personal attacks
- Help newcomers — everyone was new once
- Keep discussions in English (code, issues, PRs)

## Getting Started

### Prerequisites

- **Android Studio** (latest stable) or command-line tools
- **JDK 17+** (required by Gradle 9.x)
- **Android SDK** with API level 35
- **Android NDK** (for native engine builds — mkxp-z, EasyRPG)
- **Git** with LFS support

### Development Setup

```bash
# Clone with submodules
git clone --recursive https://github.com/KleirRampage45/Runestone.git
cd Runestone

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or build and install in one step
./gradlew installDebug
```

### Project Structure

```
Runestone/
├── app/                          # Main Android app module
│   └── src/main/
│       ├── java/com/runestone/app/
│       │   ├── MainActivity.kt       # Home screen + game library
│       │   ├── GameActivity.kt       # Routes to correct engine
│       │   ├── engine/               # Engine implementations
│       │   ├── importer/             # Game import via SAF
│       │   ├── workspace/            # Game file management
│       │   └── data/                 # Data models
│       ├── assets/                   # JS injection files
│       └── res/                      # Android resources
├── native/                       # Native engine submodules
│   └── mkxp-z-android/           # mkxp-z (XP/VX/VX Ace)
├── tests/                        # Test games (gitignored)
├── DESIGN.md                     # Architecture & design decisions
├── AGENTS.md                     # AI agent instructions
└── CONTRIBUTING.md               # This file
```

## Development Workflow

### Branches

- **`master`** — Stable releases. Only merge from development after testing.
- **`development`** — Active development. Default branch for PRs.
- **`feature/*`** — Feature branches for larger work.

### Commits

- Use conventional commit format:
  - `feat: add mkxp-z engine support`
  - `fix: crash when importing game without icon`
  - `docs: update engine detection algorithm`
  - `refactor: extract save management to separate class`
  - `test: add unit tests for EngineDetector`
- Keep commits atomic — one logical change per commit
- Write meaningful commit messages (what and why, not just what)

### Pull Requests

1. **Fork** the repository
2. **Branch** from `development`
3. **Code** your changes
4. **Test** on a real device (emulator is not sufficient for native engines)
5. **Document** new features in DESIGN.md if architectural
6. **Submit** PR against `development`

### PR Checklist

- [ ] Code compiles without warnings
- [ ] Tested on a real Android device
- [ ] No copyrighted game files included
- [ ] New features documented
- [ ] Follows existing code style (Kotlin conventions)
- [ ] GPL header in new source files

## Coding Standards

### Kotlin

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `val` over `var` when possible
- Prefer immutable data classes
- Use coroutines for async operations
- No wildcard imports (`import foo.*`)

### Architecture

- **Engines are plugins** — Each engine implements the `GameEngine` interface
- **Keep core lightweight** — Base app is launcher + UI only
- **Engine isolation** — Engines should not depend on each other
- **Use existing patterns** — Follow the patterns in existing engine implementations

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (needs device)
./gradlew connectedAndroidTest
```

- Write unit tests for engine detection logic
- Write instrumented tests for engine launching
- Test with real games from each engine

## Adding a New Engine

See `DESIGN.md` → "Engine Plugin System" for the full architecture.

### Quick Start

1. **Create engine class** implementing `GameEngine` interface
2. **Add detection** to `EngineDetector.kt`
3. **Register** in `EngineRegistry`
4. **Add UI** (if needed) for engine-specific settings
5. **Test** with at least 3 games from that engine
6. **Document** detection signatures in DESIGN.md

### Example: Adding TyranoBuilder Support

```kotlin
class TyranoEngine : GameEngine {
    override val id = "tyrano"
    override val name = "TyranoBuilder"
    override val version = "1.0.0"
    
    override fun canRun(gameFolder: File): Boolean {
        // TyranoBuilder games have data/ and tyrano/ folders
        val hasData = File(gameFolder, "data").isDirectory
        val hasTyrano = File(gameFolder, "tyrano").isDirectory
        return hasData && hasTyrano
    }
    
    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        // Launch via WebView (it's HTML/JS)
        val intent = Intent(context, WebViewGameActivity::class.java).apply {
            putExtra("game_path", gameFolder.absolutePath)
            putExtra("entry_point", "index.html")
        }
        context.startActivity(intent)
    }
}
```

## Native Engine Development

### Building Native Engines

Native engines (mkxp-z, EasyRPG) require the Android NDK:

```bash
# Set NDK path
export ANDROID_NDK_HOME=/path/to/ndk

# Build native libraries
./gradlew externalNativeBuildDebug
```

### Submodule Management

```bash
# Initialize submodules after clone
git submodule update --init --recursive

# Update submodules to latest
git submodule update --remote

# Add a new submodule
git submodule add <url> native/<name>
```

⚠️ **Never run `git submodule update --force`** if you have uncommitted changes in a submodule — it will silently discard them.

## Reporting Issues

### Bug Reports

Include:
- **Device** model and Android version
- **Game** name and engine type
- **Steps to reproduce**
- **Expected vs actual behavior**
- **Logcat output** (`adb logcat | grep runestone`)

### Feature Requests

- Explain the **use case** (why, not just what)
- Reference existing engines/games that would benefit
- Consider if it fits the project scope (see DESIGN.md)

## License

By contributing, you agree that your contributions will be licensed under the project's GPLv2+ license.

All new source files must include this header:

```kotlin
/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 <Your Name>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
```

## Questions?

- Open an issue for technical questions
- Check DESIGN.md for architecture decisions
- Read AGENTS.md for AI-assisted development guidelines
