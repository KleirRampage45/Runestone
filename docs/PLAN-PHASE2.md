# Phase 2 — Download & Install System

> Branch: `feature/phase12-game-provider`
> Build on top of Phase 1

## What to build

### 1. DownloadManager.kt — HTTP download engine

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/provider/DownloadManager.kt`:

- HttpURLConnection-based download with progress tracking
- Support pause/resume via HTTP Range headers (save offset to SharedPreferences)
- Thread-based execution (one download at a time for simplicity)
- Callbacks: onProgress(bytesDownloaded, totalBytes, speed), onComplete(filePath), onError(message)
- Configurable download directory (default to context.getExternalFilesDir("downloads") or cache dir)
- Download state tracking (IDLE, DOWNLOADING, PAUSED, COMPLETED, FAILED)

### 2. ExtractionManager.kt — ZIP extraction

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/provider/ExtractionManager.kt`:

- Extract ZIP files using java.util.zip.ZipInputStream
- Progress callback (files extracted out of total, current file name)
- Preserve directory structure
- Handle nested folders
- Skip macOS metadata files (__MACOSX, .DS_Store)
- Output to the game library directory (WorkspaceManager's game location)
- Return the path where files were extracted + detected game root

### 3. GameDetector.kt — Engine detection after extraction

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/provider/GameDetector.kt`:

- Scan extracted directory for:
  - Game.ini + .exe → RGSS (XP/VX/VX Ace)
  - www/index.html → MV/MZ
  - Ren'Py common directory patterns
  - EasyRPG (RPG_RT.ldb)
- Return detected EngineType and suggested game name

### 4. Download notification system

In MainActivity or a new NotificationHelper:

- Create a notification channel "runestone_downloads"
- Show download progress notification (with progress bar, percentage)
- On complete: notification with "Install" action
- On error: notification with "Retry" action
- Use Android's NotificationCompat

### 5. Integrate into AvailableGamesScreen

Modify AvailableGamesScreen.kt:
- Replace the "GET" text button with an actual download trigger
- Show download progress inline on the card (progress bar or percentage text)
- After download + extract + detect → auto-add to library
- Track download state per game (IDLE → DOWNLOADING → EXTRACTING → INSTALLING → INSTALLED)

Add a "DOWNLOADS" tab/section:
- Show active downloads with progress
- Completed downloads
- Failed downloads with retry option

### 6. Integrate into MainActivity

Modify MainActivity.kt:
- Initialize DownloadManager and ExtractionManager
- Wire the download flow: AvailableGamesScreen "GET" → DownloadManager → ExtractionManager → GameDetector → WorkspaceManager.addGame()
- Add download notification handling
- Add a "Downloads" badge or indicator

## UI STYLE CONSTRAINTS (CRITICAL)

ALL new UI must follow the EXISTING glassmorphism pattern from HomeScreen.kt:
- Use the same color palette: TEXT=#E8E5DC, MUTED=#8C8270, MUTED_DIM=#645F55, ACCENT=#CFAE7E
- Background: Color.rgb(3, 3, 4) or glass overlays with alpha
- Use glassBg() pattern: GradientDrawable with color+alpha, cornerRadius, stroke
- Use makeLiquid() on every tappable element
- Use animTap() on every click handler
- Use dp() for all sizes
- All views programmatic — NO XML layouts, NO Compose
- Progress indicators: use View with dynamic width for bar (not ProgressBar widget, keep the glassmorphism aesthetic)

## FILES TO CREATE

1. `app/src/main/java/com/runestone/app/provider/DownloadManager.kt`
2. `app/src/main/java/com/runestone/app/provider/ExtractionManager.kt`
3. `app/src/main/java/com/runestone/app/provider/GameDetector.kt`

## FILES TO MODIFY

1. `app/src/main/java/com/runestone/app/ui/AvailableGamesScreen.kt` — wire download, show progress on cards, add downloads section
2. `app/src/main/java/com/runestone/app/MainActivity.kt` — initialize managers, wire download flow
3. `app/src/main/java/com/runestone/app/provider/AvailableGame.kt` — add download state tracking to data model if needed

## BUILD & VERIFY

1. After all code changes: `cd /home/asukate/Development/Runestone && ./gradlew :app:assembleDebug`
2. Fix any compilation errors
3. Install with: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Verify device is connected: `adb devices` should show the OPPO phone

## IMPORTANT NOTES

- DownloadManager and ExtractionManager must be Android-independent classes (not tied to Activity lifecycle where possible — pass Context)
- Use Thread for background work (not coroutines — the project doesn't use them)
- For the progress bar on game cards, use a simple colored View with dynamic width based on percentage
- The download should survive Activity recreation (save state to SharedPreferences)
- Write errors to logcat with TAG = "DL" or "EXTRACT"
- Always disconnect HttpURLConnection in finally blocks
- Close ZipInputStream in try-with-resources or finally blocks
