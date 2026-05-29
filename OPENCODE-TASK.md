You are implementing a Game Provider System (Phase 1) for Runestone — an Android RPG Maker game launcher.

PROJECT LOCATION: /home/asukate/Development/Runestone
CURRENT BRANCH (already created): feature/phase12-game-provider

## CONTEXT

Runestone is a Kotlin Android app with NO XML layouts (all views are programmatic). It uses:
- compileSdk 35, minSdk 26, Kotlin 2.2.10
- No Room database (uses SharedPreferences for settings)
- Glassmorphism UI style (dark amber palette #030304 bg, #CFAE7E accent)
- All UI files in app/src/main/java/com/runestone/app/ui/
- Builds with `./gradlew :app:assembleDebug` and installs with `adb install`
- The app only installs to my phone via wireless adb (adb connect)
- After building, install with: adb install -r app/build/outputs/apk/debug/app-debug.apk

## EXISTING UI PATTERNS (MUST FOLLOW)

The app uses ALL PROGRAMMATIC views (NO XML). Every screen is a class with a create() method that returns a FrameLayout.

Look at these files for style reference:
- /home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/HomeScreen.kt — main screen with hero cards, HeaderRow, filter/sort overlay dialog
- /home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/SettingsScreen.kt — settings screen
- /home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/UiKit.kt — shared UI constants

### COLOR PALETTE (from HomeScreen.kt companion object)
- TEXT = Color.rgb(232, 229, 220)   // off-white text
- MUTED = Color.rgb(140, 130, 112)  // muted text
- MUTED_DIM = Color.rgb(100, 95, 85) // dimmer
- ACCENT = Color.rgb(207, 174, 126)  // amber accent
- Background: Color.rgb(3, 3, 4)
- Glass panel background: Color.argb(220, 12, 11, 16) with cornerRadius dp(18), stroke Color.argb(70, 160, 140, 110)

### GLASS BG HELPER (from HomeScreen.kt line ~781)
```kotlin
private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
    GradientDrawable().apply {
        setColor(Color.argb(alpha,
            if (accent) 50 else 22, if (accent) 40 else 20, if (accent) 30 else 26))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(if (accent) 80 else 45,
            if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
    }
```

### TAP ANIMATION PATTERN
```kotlin
fun animTap(v: View) {
    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60)
        .withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(OvershootInterpolator(1.5f)).start()
        }.start()
}
```

### LIQUID GLASS TOUCH EFFECT (makeLiquid)
Every tappable element on HomeScreen uses makeLiquid() for magnify-on-press effect.

### SORT & FILTER OVERLAY STYLE (from HomeScreen.kt lines ~375-635)
The filter/sort dialog uses:
- Full-screen dark overlay (Color.argb(180, 0, 0, 0))
- Glass panel (88% screen width, centered)
- Engine filter chips (horizontal, selectable, with active/inactive styling)
- Sort options (vertical list with icons and checkmarks)
- DONE + CLEAR buttons at bottom
- Slide-up animation with OvershootInterpolator

### DOCK BAR (from HomeScreen.kt lines ~277-311)
Fixed bottom bar with: ADD | FILES | SETTINGS icons
Glassmorphism background, 44dp height.

## WHAT TO BUILD — Phase 1: Game Provider System

### 1. Provider data model + storage

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/provider/ProviderSource.kt`:
```kotlin
data class ProviderSource(
    val id: String,
    val name: String,
    val url: String,
    val status: SourceStatus,
    val addedAt: Long = System.currentTimeMillis()
)

enum class SourceStatus { PENDING, ACTIVE, FAILED }

data class AvailableGame(
    val id: String,
    val title: String,
    val engine: String?,  // Engine type hint
    val fileSize: Long?,
    val downloadUrl: String?,
    val sourceName: String,
    val coverUrl: String?
)
```

### 2. SourcesManager.kt

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/provider/SourcesManager.kt`:
- Stores/loads ProviderSource list in SharedPreferences as JSON
- Methods: getSources(), addSource(url), removeSource(id), clearSources()
- Method: fetchGamesFromSources() — makes HTTP requests to a configurable API URL
- For now, the API URL should be configurable (default to a placeholder)
- When API returns no data (not configured yet), show a "Configure API Server" placeholder
- Use java.net.HttpURLConnection (no external dependencies)
- Parse JSON with org.json.JSONObject/JSONArray (built into Android)

### 3. AvailableGamesScreen.kt

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/AvailableGamesScreen.kt`:

This is the screen that shows games available from providers. It MUST follow the same visual patterns as HomeScreen:

- Returns a FrameLayout (like all Runestone screens)
- Has a header with "Available Games" title and a back/close button
- Has filter chips for engine type (same glass style as HomeScreen filter)
- Has sort options (same pattern: A-Z, Z-A, Size, Date)
- Shows game cards in a vertical scroll list (similar to hero cards but smaller — like 60% width, compact)
- Each game card shows: title, engine badge, file size, source name
- Tap a game card → shows a glass dialog overlay with download option
- Shows "No sources configured" empty state with a button to add sources
- Has a "Configure" button in the header that goes to source management

THE CARDS MUST MATCH the existing hero card style — same corner radius, same glass borders, same color palette. They should just be a different layout (smaller, more compact, showing game info instead of engine label).

### 4. SourcesScreen.kt

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/SourcesScreen.kt`:

UI for managing provider sources:
- Return FrameLayout
- Header: "Game Sources" + back button
- List of existing sources with status badge (PENDING/ACTIVE/FAILED) and remove button
- "Add Source" button at bottom (same style as DONE button in filter dialog)
- Add Source dialog — a glass overlay with URL input field + "Add" button
- Empty state: "No sources added yet" with explanation text
- Each source row shows: name, URL (truncated), status chip, remove "X" button

### 5. ProviderSettingsScreen.kt

Create `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/ProviderSettingsScreen.kt`:

Configuration screen for the provider system:
- "Provider API" section — URL input for the backend API server
- "Default Sources" section — list of pre-configured source URLs
- "Clear All Sources" button (with confirmation dialog)
- Import/export sources as JSON text
- Follow the same style as SettingsScreen.kt

### 6. Integrate into MainActivity.kt

In `/home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/MainActivity.kt`:

- Add a new dock item: Add "STORE" / compass icon in the dock bar between ADD and FILES
- The dock in HomeScreen is created by `makeDockBar()`. Since it takes callbacks, add a new callback parameter for "onStore" or "onBrowse"
- When store tab is tapped: show AvailableGamesScreen as an overlay (same pattern as showSettings() and showManageFiles())
- In the AvailableGamesScreen: add button to open SourcesScreen
- Add a new callback to the HomeScreen.create() parameters: `onBrowse: (() -> Unit)? = null`

### HOW TO MODIFY HOMESCREEN DOCK

In HomeScreen.kt, the dock is at line ~277:
```kotlin
private fun makeDockBar(onAdd: () -> Unit, onManage: () -> Unit, onSettings: () -> Unit): LinearLayout
```

Change this to:
```kotlin
private fun makeDockBar(onAdd: () -> Unit, onBrowse: () -> Unit, onManage: () -> Unit, onSettings: () -> Unit): LinearLayout
```

Add a "STORE" icon (you can use a Unicode character like "☰" or a simple text label as "▦" or create a simple text button) between ADD and FILES. Use the same dockItem() pattern.

In the HomeScreen.create() call, add the parameter and pass it through.

### KEY UI CONSTRAINTS (DO NOT VIOLATE)

1. **All views programmatic** — No XML layouts, no Compose. Use LinearLayout, FrameLayout, TextView, ImageView directly.
2. **Use the EXISTING color palette** — TEXT, MUTED, MUTED_DIM, ACCENT from HomeScreen's companion. Use dp() for all sizes.
3. **Glassmorphism** — Every panel/dialog must use GradientDrawable with:
   - setColor with alpha (background see-through)
   - cornerRadius (8-18dp depending on size)
   - setStroke with subtle border
4. **Animation** — Use animTap() and makeLiquid() from HomeScreen on every clickable element. Use slide-up + overshoot for dialogs.
5. **No dependencies** — Only add external dependencies if absolutely required. Use java.net.HttpURLConnection and org.json (built into Android).
6. **Screen size** — Measure with displayMetrics, use 88-92% width for dialogs, hero cards at 88% width.

### OTHER NOTES
- Add an INTERNET permission request to AndroidManifest.xml if not present
- Handle JSON encoding/decoding manually with org.json (no Moshi/Gson needed for this)
- All network calls should be on background threads using Thread or kotlinx.coroutines if already available
- Show loading state while fetching games
- Handle errors gracefully (show toast or inline error)

## VERIFICATION
After making changes, verify:
1. The app compiles: run `cd /home/asukate/Development/Runestone && ./gradlew :app:assembleDebug 2>&1 | tail -30`
2. If there are compile errors, fix them
3. git add and git commit the changes with a descriptive message
4. Report exactly what was created and any known issues
