# Phase 1 — Game Provider System (✅ Complete)

> Branch: `feature/phase12-game-provider`
> Date: 2026-05-28
> Commit: `3333066`

## What was built

### New files (6)

| File | Purpose |
|------|---------|
| `app/src/main/java/com/runestone/app/provider/ProviderSource.kt` | Data models: `ProviderSource`, `AvailableGame`, `SourceStatus` enum with JSON serialization |
| `app/src/main/java/com/runestone/app/provider/SourcesManager.kt` | SharedPreferences storage + HttpURLConnection API client. Stores sources as JSON, fetches games from configurable API URL on background thread |
| `app/src/main/java/com/runestone/app/ui/AvailableGamesScreen.kt` | Browse games from providers — glassmorphism cards, search bar, loading/error/empty states. 382 lines |
| `app/src/main/java/com/runestone/app/ui/SourcesScreen.kt` | Manage provider sources — list with status badges (PENDING/ACTIVE/FAILED), add URL dialog, remove. 322 lines |
| `app/src/main/java/com/runestone/app/ui/ProviderSettingsScreen.kt` | API URL configuration, clear all sources with confirmation. 355 lines |
| `app/src/main/res/drawable/ic_store.xml` | STORE icon for the dock bar |

### Modified files (3)

| File | Change |
|------|--------|
| `HomeScreen.kt` | Added `onBrowse` callback param to `create()`, STORE dock icon between ADD and FILES |
| `MainActivity.kt` | Wired up `SourcesManager` instance, `showAvailableGames()`, `showSources()`, `showProviderSettings()` as overlays |
| `AndroidManifest.xml` | Added `INTERNET` permission |

### Architecture

```
User taps STORE dock icon
  │
  ├─→ showAvailableGames() → AvailableGamesScreen
  │    ├─→ Checks SourcesManager.getSources()
  │    ├─→ If no API URL → "Configure API Server" with buttons
  │    ├─→ If no sources → "No sources configured" with MANAGE SOURCES
  │    ├─→ fetchGamesFromSources() → HTTP GET {apiUrl}/games?source={url}
  │    └─→ Shows game cards with engine badge, size, source name
  │
  ├─→ showSources() → SourcesScreen
  │    ├─→ Lists all sources with status badges
  │    ├─→ "Add Source" → glass dialog with URL input
  │    └─→ Remove (X) per source
  │
  └─→ showProviderSettings() → ProviderSettingsScreen
       ├─→ API URL input field
       ├─→ Clear all sources (with confirmation)
       └─→ Back button
```

### Limitations (Phase 1)
- No backend API server exists yet → "Configure API Server" on first use
- Game cards have a "GET" button but no actual download/extraction
- No search/filter/sort on the browse screen (just a search text field)
- No download progress, no notifications

### Next → Phase 2: Download & Install
See `PLAN-PHASE2.md`
