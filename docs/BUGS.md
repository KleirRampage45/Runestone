# Runestone — Bug & Feature Inventory

## Fixed

### 1. Resume banner never appears ✅ FIXED
`isPaused` reads `paused_game` from SharedPreferences in both `OverlayNavigationController.toCardInfo()` and `GameListViewModel.toCardInfo()`.
**Files:** `OverlayNavigationController.kt`, `GameListViewModel.kt`

### 2. Playtime accumulator never stops ✅ FIXED
`GameSessionManager.finalize()` caps elapsed seconds to 14400 (4 hours max per session).
**Files:** `GameSessionManager.kt`

### 3. STOP button creates separate GameSessionManager instance ✅ FIXED
`showHome()` STOP callback now uses the shared `sessionManager` from the navController instead of creating a new instance.
**Files:** `OverlayNavigationController.kt`

### 1. Resume banner never appears
`isPaused` is hardcoded to `false` in both `OverlayNavigationController.toCardInfo()` (line 980) and `GameListViewModel.toCardInfo()` (line 111). The resume bar at the top of the home screen only renders when `pausedGame != null`, which never happens.

**Files:** `OverlayNavigationController.kt:980`, `GameListViewModel.kt:111`  
**Fix:** Read `paused_game` from SharedPreferences and set `isPaused = true` when the game matches.

### 2. Resume works only once; second resume kills the game
`playGame()` at `MainActivity.kt:393` calls `finish()` to dismiss MainActivity when resuming a minimized game. This destroys the activity. On the *second* minimize → resume cycle, a new `MainActivity` is created via `onCreate()`, and the game list loads asynchronously via `rootContainer.post { refreshGames() }`. If the user taps RESUME before the coroutine populates `games`, `playGame()` returns silently because the game is not found.

**Files:** `MainActivity.kt:382-404`, `OverlayNavigationController.kt:273-305` (resume callback chain)

### 3. Playtime accumulator never stops
`GameSessionManager.finalize("fresh_on_create")` is called in `onCreate` (line 142). It calculates `elapsed = now - startedAt`. If the phone was off for 48h between game launch and app restart, it adds all 48h as play time. No sanity cap exists. The in-memory cache at `GameSessionManager.kt:114` never resets (`playTimeCache` is never cleared), so hours accumulate across sessions within the same process lifetime.

**Files:** `GameSessionManager.kt:55-78` (cap elapsed at `last_seen_at` or 24h), `MainActivity.kt:142` (clearResumeState → finalize call chain)

### 4. STOP button creates separate GameSessionManager instance
`OverlayNavigationController.kt:293` calls `GameSessionManager(activity).recordStop(storageName)` — a *new* instance that shares no cache with the main `sessionManager`. The `warmCache()` call on the main instance populated `playTimeCache`, but this new instance has an empty cache. `recordStop` reads from its own empty cache and adds elapsed to a separate, unread copy. The main instance's cache is never updated, so play time from the STOP button is lost.

**Files:** `OverlayNavigationController.kt:289-304` (onStop lambda), `MainActivity.kt:107-111` (navCallbacks should expose sessionManager)

## UX Bugs

### 5. Filter/sort not remembered across restarts
`activeEngineFilter`, `currentSort`, `searchQuery` live in memory in `OverlayNavigationController` (lines 91-94) and `HomeUiState.data class` (`GameListViewModel.kt:28-30`). On process death or `recreate()`, all filter state resets to default.

**Files:** `OverlayNavigationController.kt:91-94`, `GameListViewModel.kt:25-33`, `MainActivity.kt` (load/save)

### 6. Grid display off-center in 3-column mode
The game grid renders with more empty space on the right side. Likely a `GridLayout` column weight calculation in `HomeScreen.kt`.

**Files:** `HomeScreen.kt` — grid layout measure phase

### 7. UI content slightly shifted right
`MainActivity.kt:167-184` applies `ViewCompat.setOnApplyWindowInsetsListener` with `SAFE_AREA` mode. On devices with a left-side camera cutout, the `displayCutout.left` padding shifts the root FrameLayout right, creating a visible gap on the right side. The dock bar inherits this shift.

**Files:** `MainActivity.kt` (insets listener), possibly `HomeScreen.kt` grid centering

### 8. Add Game file browser lacks file operations
The SAF folder/file list shows folders and files but has no: Move, Delete, "More" icon (Copy, Cut, Extract, Rename), Details dialog (full name, date modified, size, full path).

**Files:** `GameFolderBrowserScreen.kt`

### 9. Import progress bar shows no percentage
The import overlay shows "Importing game…" text with no progress percentage or progress bar.

**Files:** `ImportManager.kt`, import UI in `OverlayNavigationController.kt`

## Feature Gaps

### 10. Import wording
When no file copy occurs (e.g., SAF reference), use "Adding" or "Installing" instead of "Importing".

**Files:** `ImportManager.kt`, string resources (`values/strings.xml`)

### 11. Guided import flow
The SAF browser should offer: explicit path input, or prompt to select the game's launcher file (Game.exe, Game.ini, RGSS*.dll, index.html, 0.txt) to auto-detect engine + strip non-game files. Currently only shows a folder tree with no intelligence.

**Files:** `GameFolderBrowserScreen.kt`, `ImportManager.kt`, `EngineRegistry.kt`

### 12. UI sounds / haptic feedback
No `HapticFeedbackConstants` or button-click sounds anywhere. Every `setOnClickListener` should trigger haptic feedback.

**Files:** Every screen file + a global utility class

### 13. Filter UI — type-ahead label
The filter/sort row shows "ALL | NEW" but the label should reflect the current active sort mode ("RECENT", "NAME A-Z", etc.) so the user knows what they're looking at.

**Files:** `HomeScreen.kt` filter header

## Observations (defer)

- Onboarding engine toggles (`OnboardingScreen.kt`) don't actually control engine availability — only `godot` and `renpy` have meaningfully togglable registration. The other three toggles (`mkxp-z`, `easyrpg`, `onscripter`) are always bundled and registered.
- The `EngineRegistry.setOptionalEnabled` only handles `godot` — `renpy` is always registered in `registerEngines()`.
- After `recreate()` from onboarding completion, the second `onCreate` runs the normal flow. `persistentDock` is created and assigned to `navController.persistentDock`, but the dock's `onSettings` callback captures the *initial* `settings` reference (the lambda at `MainActivity.kt:229` captures `settings` by value at lambda creation, not by property reference). Any runtime settings changes are invisible until the next `showHome()` rebuild.

**Files:** `OnboardingScreen.kt`, `EngineRegistry.kt`, `MainActivity.kt:229`
