# Light / Dark Theme Support — Design Specification

> **Status:** Draft  
> **Date:** 2026-05-29  
> **Author:** Asuka (GODMODE)  
> **Repo:** Runestone — Multi-engine RPG Maker game launcher for Android  

---

## 1. Current State

**Everything is hardcoded to dark colors.** There is no concept of a theme — every color value is written inline as `Color.rgb()` or `Color.argb()` directly at the point of use.

### Specific problems

| Pattern | Example | Location |
|---------|---------|----------|
| Background | `Color.rgb(3, 3, 4)` | HomeScreen.kt:80, SettingsScreen.kt:143, AvailableGamesScreen.kt:151, ImportProgressScreen.kt:39 |
| Surface | `Color.argb(220, 12, 11, 16)` | AvailableGamesScreen.kt:440, ManageFilesScreen.kt:153, ImportProgressScreen.kt:48 |
| Card background | `Color.argb(190, 12, 11, 16)` | ManageFilesScreen.kt:185, SettingsScreen.kt:204 |
| Panel | `Color.argb(200, 15, 15, 18)` | HomeScreen.kt:124 |
| Text (primary) | `Color.rgb(232, 229, 220)` | Every `companion object` |
| Text (secondary) | `Color.rgb(140, 130, 112)` | Every `companion object` |
| Accent | `Color.rgb(207, 174, 126)` | Every `companion object` |
| Glass helper | `Color.argb(alpha, 22, 20, 26)` with `Color.argb(45, 100, 90, 80)` stroke | glassBg() in every screen |
| Overlay dim | `Color.argb(180, 0, 0, 0)` | HomeScreen.kt:140, AvailableGamesScreen.kt:430 |
| Progress bar | `Color.argb(160, 207, 174, 126)` | AvailableGamesScreen.kt:323 |
| Danger panel | `Color.argb(40, 60, 20, 20)` with `Color.argb(40, 200, 80, 80)` stroke | ProviderSettingsScreen.kt:237 |
| Card engine tints | `Color.rgb(30, 35, 28)`, `Color.rgb(35, 28, 32)`, etc. | HomeScreen.kt:778–784 |
| Source status dots | `Color.rgb(100, 200, 100)` / `Color.rgb(200, 100, 100)` | SourcesScreen.kt:137–139 |

### Duplicated helper code

Every screen class defines its own private copies of:

- `glassBg(radius, alpha, accent)` — the "glass morphism" background drawable
- `makeLiquid(view)` — touch interactivity (scale + parallax)
- `animTap(view)` — tap animation
- `spacer()` / `dp()` — basic layout helpers
- Companion object with `TEXT`, `MUTED`, `MUTED_DIM`, `ACCENT` constants

The values in `UiKit.kt` are partially overlapping but **not used** by most screens — each screen defines its own.

---

## 2. Proposed Architecture

### 2.1 ThemeProvider (singleton)

Create a new class in `com.runestone.app.ui.theme` package:

```kotlin
package com.runestone.app.ui.theme

enum class ThemeMode { DARK, LIGHT }

class ThemeProvider private constructor(context: Context) {

    var currentTheme: ThemeMode
        private set

    // Exposed color properties
    val background: Int get() = colors[themeKey]?.background ?: dark.background
    val surface: Int get() = colors[themeKey]?.surface ?: dark.surface
    val cardBackground: Int get() = colors[themeKey]?.cardBackground ?: dark.cardBackground
    val text: Int get() = colors[themeKey]?.text ?: dark.text
    val textSecondary: Int get() = colors[themeKey]?.textSecondary ?: dark.textSecondary
    val accent: Int get() = colors[themeKey]?.accent ?: dark.accent
    val accentMuted: Int get() = colors[themeKey]?.accentMuted ?: dark.accentMuted
    val muted: Int get() = colors[themeKey]?.muted ?: dark.muted
    val mutedDim: Int get() = colors[themeKey]?.mutedDim ?: dark.mutedDim

    fun setTheme(mode: ThemeMode) { ... }  // persists to SharedPreferences
    fun toggle(): ThemeMode { ... }
    fun followSystem(): Boolean { ... }

    companion object {
        @Volatile private var instance: ThemeProvider? = null
        fun getInstance(context: Context): ThemeProvider { ... }
    }
}
```

### 2.2 Theme colors data class

```kotlin
data class ThemeColors(
    val background: Int,
    val surface: Int,
    val cardBackground: Int,
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentMuted: Int,   // ARGB with ~60 alpha
    val muted: Int,
    val mutedDim: Int,
)
```

### 2.3 Architecture diagram

```
┌─────────────────────┐
│   ThemeProvider     │  Singleton, holds current ThemeMode
│   (singleton)       │  Reads colors from ThemeColors.DARK / LIGHT
└─────────┬───────────┘          ▲
          │                          │
          ▼                          │ persisted in
┌─────────────────────┐    SharedPreferences
│   Screen classes    │  ("theme_mode" key)
│   (HomeScreen, etc) │
│                     │  All Color.rgb()/Color.argb() calls replaced
│                     │  with ThemeProvider.getInstance(context).<color>
└─────────────────────┘
```

### 2.4 How screens consume colors

**Before (hardcoded):**

```kotlin
root.setBackgroundColor(Color.rgb(3, 3, 4))
textView.setTextColor(Color.rgb(232, 229, 220))
background = glassBg(dp(14))
```

**After (theme-aware):**

```kotlin
val theme = ThemeProvider.getInstance(context)
root.setBackgroundColor(theme.background)
textView.setTextColor(theme.text)
background = theme.glassBg(dp(14))  // see §7.1
```

---

## 3. Dark Theme Colors (Current Baseline)

These are the values already in use across all screens.

| Token | Value | Notes |
|-------|-------|-------|
| `background` | `Color(3, 3, 4)` | Near-black, used as root background |
| `surface` | `Color(12, 11, 16)` | Used for glass panels, card surfaces |
| `cardBackground` | `Color(22, 20, 26)` | Slightly lighter than surface |
| `text` | `Color(220,210,200)` aka `Color(232, 229, 220)` | Primary text – two variants in use, standardize to `Color(220, 210, 200)` |
| `textSecondary` | `Color(180, 160, 140)` | Secondary text |
| `accent` | `Color(207, 174, 126)` | Gold/brass accent |
| `accentMuted` | `argb(60, 207, 174, 126)` | Translucent accent for button backgrounds |
| `muted` | `Color(140, 130, 112)` | Muted text (currently varies: `140,130,112` or `151,143,132`) |
| `mutedDim` | `Color(100, 95, 85)` aka `Color(100, 95, 85)` | Dimmer muted text |

### Color discrepancy to resolve

The current codebase has inconsistent `text` and `muted` values across screens:

- **HomeScreen:** `TEXT = Color(232, 229, 220)`, `MUTED = Color(140, 130, 112)`
- **ManageFilesScreen:** `TEXT = Color(232, 229, 220)`, `MUTED = Color(140, 130, 112)`
- **SettingsScreen:** Uses `TEXT` from parent scope (same as HomeScreen)
- **ImportProgressScreen:** Uses inline `Color(232, 229, 220)` and `Color(140, 130, 112)`
- **ProviderSettingsScreen:** `TEXT = Color(232, 229, 220)`, `MUTED = Color(140, 130, 112)`
- **SourcesScreen:** `TEXT = Color(232, 229, 220)`, `MUTED = Color(140, 130, 112)`
- **AvailableGamesScreen:** Uses `TEXT` from companion (same)

**Standardize to the values listed in §3 above** during refactoring.

---

## 4. Light Theme Colors (Proposed)

| Token | Value | Notes |
|-------|-------|-------|
| `background` | `Color(245, 243, 240)` | Warm off-white |
| `surface` | `Color(255, 255, 255)` | Pure white for glass panels |
| `cardBackground` | `Color(240, 238, 235)` | Slightly warm light gray |
| `text` | `Color(30, 28, 26)` | Near-black with warm tint |
| `textSecondary` | `Color(100, 95, 90)` | Warm dark gray |
| `accent` | `Color(170, 130, 80)` | Burnished gold — less yellow than dark accent |
| `accentMuted` | `argb(60, 170, 130, 80)` | Translucent light accent |
| `muted` | `Color(140, 135, 130)` | Medium gray |
| `mutedDim` | `Color(180, 175, 170)` | Light gray for hints |

### Light theme glassBg equivalent

For the `glassBg()` helper, the light theme variants:

```kotlin
// Default glass (light theme)
setColor(Color.argb(alpha, 245, 243, 240))    // background color with alpha
setStroke(dp(1), Color.argb(alpha/2, 200, 195, 190))

// Accent glass (light theme)
setColor(Color.argb(alpha, 170, 130, 80))      // accent color with alpha
setStroke(dp(1), Color.argb(alpha/2, 170, 130, 80))
```

---

## 5. Implementation Steps

### Phase 1: Foundation + one screen refactor

1. **Create the theme package**
   - `/app/src/main/java/com/runestone/app/ui/theme/ThemeProvider.kt`
   - `/app/src/main/java/com/runestone/app/ui/theme/ThemeMode.kt`
   - `/app/src/main/java/com/runestone/app/ui/theme/ThemeColors.kt`

2. **Implement ThemeProvider**
   - Double-checked locking singleton pattern
   - `getInstance(context)` — lazy init with Application context
   - Load initial mode from `SharedPreferences("runestone-settings-v1")` key `"theme_mode"`
   - Default to `DARK` if not set

3. **Refactor one screen: HomeScreen**
   - Replace `companion object` color constants with `ThemeProvider.getInstance(context).<color>`
   - Update `glassBg()` to accept and use theme colors
   - Verify every `Color.rgb()` / `Color.argb()` call is replaced
   - Manually test dark theme rendering is unchanged
   - Toggle to light theme and visually verify

**Estimated effort:** 2–3 hours for ThemeProvider, 1–2 hours for HomeScreen refactor.

### Phase 2: Refactor all remaining screens

| Screen | Files | Hardcoded color sites (approx) | Priority |
|--------|-------|-------------------------------|----------|
| HomeScreen | ✓ done in Phase 1 | ~40 | — |
| AvailableGamesScreen | 1 file | ~45 | High |
| SettingsScreen | 1 file | ~35 | High |
| ManageFilesScreen | 1 file | ~25 | High |
| SourcesScreen | 1 file | ~20 | Medium |
| ProviderSettingsScreen | 1 file | ~20 | Medium |
| ImportProgressScreen | 1 file | ~10 | Medium |
| UiKit.kt | 1 file | ~8 | Low (deprecated?) |

**Strategy per screen:**

1. Remove the `companion object` color constants
2. Add `private val theme = ThemeProvider.getInstance(context)` at class level
3. Replace every `Color.rgb()` / `Color.argb()` with `theme.<token>`
4. Update `glassBg()` to delegate to a shared theme-aware version
5. Run the app and verify no visual regression in dark mode
6. Toggle to light mode and verify readability

**Estimated effort:** 4–6 hours total.

### Phase 3: Settings toggle

Add a `Theme` section to `SettingsScreen.kt`:

```
┌────────────────────────────────┐
│ Theme                          │
│ Choose light or dark appearance│
│                                │
│  ○ Light    ○ Dark    ○ System │
└────────────────────────────────┘
```

- Radio-button style selection (three options)
- Calls `ThemeProvider.getInstance(context).setTheme(mode)`
- Theme changes apply immediately (no restart needed)
- Persisted via SharedPreferences

**Implementation notes:**

- The current `SettingsScreen` is a glass overlay on top of the home screen. Theme toggle should **immediately** update all visible views. This requires either:
  - (a) Having `MainActivity` listen for theme changes and rebuild the overlay, or
  - (b) Making each screen's views read `theme.<color>` at draw time (e.g., overriding `onDraw` or using a custom `ViewGroup` that re-applies colors on theme change).

**Recommendation:** Option (a) — `MainActivity` observes the ThemeProvider and calls `refreshOverlay()` which recreates the active overlay view. This is simpler and avoids coupling view trees to theme state.

**Estimated effort:** 2–3 hours.

### Phase 4: Auto-theme (follow system)

```kotlin
fun isDarkModeEnabled(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            val uiMode = context.resources.configuration.uiMode
            uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        else -> false // fall back to manual
    }
}
```

- Wire into `MainActivity.onCreate()` and register a `ConfigurationChanged` listener
- When system theme changes AND user has selected "System" mode, auto-switch
- Persist `ThemeMode.SYSTEM` in SharedPreferences to distinguish from manual DARK/LIGHT

**Estimated effort:** 1–2 hours.

---

## 6. Migration Strategy

### 6.1 Replace all `Color.rgb()` / `Color.argb()` calls

Screen by screen, non-destructive replacement:

1. **Identify:** Search for `Color.rgb` and `Color.argb` in the file
2. **Categorize** each call:
   - **Clean replacement:** Maps directly to a theme property (e.g., `Color.rgb(3, 3, 4)` → `theme.background`)
   - **Needs mapping:** Color used for a specific purpose that varies by context (e.g., status dots, engine badge tints)
   - **Hardcoded semantic color:** E.g., `Color.rgb(240, 120, 120)` for STOP button red — these may stay as-is or be added to theme if we want semantic color tokens

3. **Replace** with `theme.<token>` and verify

### 6.2 Color mapping table

| Hardcoded value | Theme token | Notes |
|----------------|-------------|-------|
| `Color.rgb(3, 3, 4)` | `theme.background` | Root backgrounds |
| `Color.rgb(15, 14, 18)` / `Color.rgb(15, 14, 16)` | `theme.surface` | Top bar backgrounds |
| `Color.argb(190–220, 12, 11, 16)` | `theme.surface` with alpha | Glass panels, cards |
| `Color.argb(200, 15, 15, 18)` | `theme.surface` (alpha 200) | Bar backgrounds |
| `Color.argb(120, 10, 10, 13)` | `theme.cardBackground` with alpha | Nested panels |
| `Color.rgb(232, 229, 220)` | `theme.text` | Primary text |
| `Color(220,210,200)` | `theme.text` | Secondary primary text variant |
| `Color.rgb(140, 130, 112)` / `Color(151, 143, 132)` | `theme.muted` | Muted text |
| `Color.rgb(100, 95, 85)` | `theme.mutedDim` | Dim text |
| `Color.rgb(207, 174, 126)` | `theme.accent` | Accent text, borders |
| `Color.argb(40–80, 207, 174, 126)` | `theme.accentMuted` | Accent backgrounds, muted borders |
| `Color.argb(180, 0, 0, 0)` | `theme.overlayDim` | Add new token for overlay dim |
| `Color.argb(160, 207, 174, 126)` | `theme.accentMuted` (alpha 160) | Progress bar fill |
| `Color.argb(60, 207, 174, 126)` | `theme.accentMuted` | Selected card border |

### 6.3 Colors that should stay hardcoded (semantic)

These are action-specific colors that aren't theme-dependent:

| Color | Usage | Reason |
|-------|-------|--------|
| `Color.rgb(240, 120, 120)` | STOP / CLOSE GAME buttons | Red = danger, universal |
| `Color.rgb(140, 240, 140)` | RESUME button | Green = go, universal |
| `Color.rgb(200, 120, 100)` | Error messages | Red/brown for errors |
| `Color.argb(60, 200, 80, 80)` | Danger button backgrounds | Red tint, stays red |
| `Color.rgb(190, 224, 176)` | Saves detected message | Green feedback |
| Engine card tints | `Color.rgb(30, 35, 28)` etc. | Engine identity colors |
| Source status dots | `Color.rgb(100, 200, 100)` etc. | Semantic status |

These can optionally be added to the theme as `ThemeColors.error`, `ThemeColors.success`, etc. in a future iteration.

### 6.4 Verification checklist

After each screen refactor:

- [ ] Dark mode: no visual difference from before
- [ ] Light mode: all text legible on backgrounds
- [ ] All `Color.rgb()` / `Color.argb()` references removed (check with grep)
- [ ] glassBg() produces readable backgrounds in both themes
- [ ] makeLiquid() touch effects still work
- [ ] GradientDrawable strokes visible in both themes
- [ ] Overlay dim layers are appropriately translucent in light theme (use black with low alpha, not the surface color)

---

## 7. Challenges

### 7.1 glassBg() helper — theme-aware variants

**Problem:** Every screen defines:

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

This hardcodes RGB values (22, 20, 26) for the glass surface and (100, 90, 80) for the stroke — these are dark-theme-specific.

**Solution:** Move `glassBg()` into ThemeProvider as a method:

```kotlin
class ThemeProvider {
    fun glassBg(radiusDp: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            val baseColor = if (accent) accent else surface
            setColor(Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)))
            cornerRadius = dp(radiusDp).toFloat()
            val strokeColor = if (accent) accent else mutedDim
            setStroke(dp(1), Color.argb(alpha / 2, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)))
        }
}
```

This makes glassBg() fully theme-aware — in light mode it'll produce white-ish glass instead of near-black glass.

### 7.2 GradientDrawable colors constructed inline everywhere

**Problem:** Many places construct `GradientDrawable` with inline `Color.argb()` values for:

- Card backgrounds
- Panel surfaces
- Search bar backgrounds
- Progress bar backgrounds
- Divider lines
- Action button backgrounds

Each of these needs to be converted to use theme properties.

**Strategy:** For common patterns, create factory methods on ThemeProvider:

```kotlin
fun cardBackground(alpha: Int = 190): GradientDrawable
fun panelBackground(cornerDp: Int, alpha: Int = 220): GradientDrawable
fun inputBackground(): GradientDrawable
fun progressBackground(): GradientDrawable
fun divider(): View // returns a View with themed background color
```

This reduces duplication and ensures consistency.

### 7.3 Liquid touch effects use hardcoded colors

**Problem:** The `makeLiquid()` method itself doesn't use colors (it scales/translates views), so it's theme-agnostic. However, the visual feedback relies on the view's existing background — so if the background is hardcoded, it won't adapt when the theme changes dynamically.

**Mitigation:** This is already handled if views are recreated on theme change (Phase 3, Option a). If views are not recreated, the background drawable will remain the old dark-theme drawable. Ensure `MainActivity.refreshOverlay()` replaces the view tree on theme toggle.

### 7.4 Backup screen backgrounds (GameActivity, WebView)

**Problem:** `GameActivity.kt` is the full-screen game activity — its background is visible briefly before the WebView/engine loads, and as a fallback if the game fails. This background may need to be theme-aware, but the game itself is not themed.

**Options:**

1. **Ignore theming for GameActivity** — Keep the loading background dark (`Color(3, 3, 4)`) regardless of theme. The light theme would flash dark momentarily. Acceptable compromise since game content dominates.
2. **Read theme at startup** — `GameActivity` reads ThemeProvider in `onCreate()` and sets its background accordingly. Adds a slight delay for SharedPreferences read.
3. **Pass theme via Intent extra** — `MainActivity` passes the current `ThemeMode.name` as an Intent extra when launching `GameActivity`.

**Recommendation:** Option 3 — minimal overhead, no SharedPreferences read in the game activity path, and the theme is already known at launch time.

Similarly for `EasyRpgPlayerActivity.java` — its background can be set from the Intent extra.

### 7.5 Duplicated helper code consolidation

Refactoring is a good opportunity to extract the duplicated helpers into a shared utility. Create:

```kotlin
// ThemeProvider already handles glassBg()
// Add to ThemeProvider or a companion file:

fun dp(context: Context, value: Int): Int
fun spacer(context: Context, height: Int, width: Int = 0): View
fun makeLiquid(view: View)  // static, no theme dependency
fun animTap(view: View)     // static, no theme dependency
```

This reduces ~80 lines per screen file.

### 7.6 Overlay dimming in light theme

Currently overlays use `Color.argb(180, 0, 0, 0)` — a semi-transparent black. In light theme, this creates a strong dark curtain that clashes aesthetically.

**Recommendation:** Use a theme-dependent overlay dim:

```kotlin
val overlayDim: Int get() = if (currentTheme == DARK)
    Color.argb(180, 0, 0, 0)
else
    Color.argb(160, 40, 38, 35) // warm dark gray tint
```

---

## 8. Future Considerations

### 8.1 Accent color customization

Once the theme system is in place, adding user-customizable accent colors becomes straightforward — store the accent color int in SharedPreferences and load it into the `ThemeColors` data class.

### 8.2 Additional semantic tokens

Consider adding these in a follow-up:

- `success` — green (currently `Color(140, 240, 140)`)
- `error` / `danger` — red (currently `Color(240, 120, 120)`)
- `warning` — amber/yellow
- `info` — blue-ish
- `overlayDim` — the dim layer color (currently black with 180 alpha)

### 8.3 Animated theme transitions

Once the theme system is stable, add crossfade animations when switching themes. This requires keeping both the old and new view trees briefly, or using a screenshot overlay that fades out.

---

## Appendix A: File Inventory

All files that contain hardcoded `Color.rgb()` or `Color.argb()` calls:

| File | Color calls | Has glassBg? | Has makeLiquid? | Has companion colors? |
|------|-------------|-------------|-----------------|----------------------|
| `app/.../ui/HomeScreen.kt` | ~40 | ✓ | ✓ | ✓ |
| `app/.../ui/SettingsScreen.kt` | ~35 | ✓ | ✓ | ✗ (uses outer) |
| `app/.../ui/AvailableGamesScreen.kt` | ~45 | ✓ | ✓ | ✗ (uses outer) |
| `app/.../ui/ManageFilesScreen.kt` | ~25 | ✓ | ✓ | ✓ |
| `app/.../ui/SourcesScreen.kt` | ~20 | ✓ | ✓ | ✓ |
| `app/.../ui/ProviderSettingsScreen.kt` | ~20 | ✓ | ✓ | ✓ |
| `app/.../ui/ImportProgressScreen.kt` | ~10 | ✗ | ✗ | ✗ (all inline) |
| `app/.../ui/UiKit.kt` | ~8 | ✗ | ✗ | ✓ |
| `app/.../GameActivity.kt` | ~5 | ✗ | ✗ | ✗ |
| `app/.../MainActivity.kt` | ~2 | ✗ | ✗ | ✗ |
| `app/.../input/TouchOverlayView.kt` | ~10 | ✗ | ✗ | ✗ |

---

## Appendix B: ThemeProvider API Draft

```kotlin
// com.runestone.app.ui.theme

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class ThemeColors(
    val background: Int,        // Color.rgb(...)
    val surface: Int,           // Color.rgb(...)
    val cardBackground: Int,    // Color.rgb(...)
    val text: Int,              // Color.rgb(...)
    val textSecondary: Int,     // Color.rgb(...)
    val accent: Int,            // Color.rgb(...)
    val accentMuted: Int,       // Color.argb(...)
    val muted: Int,             // Color.rgb(...)
    val mutedDim: Int,          // Color.rgb(...)
) {
    companion object {
        val DARK = ThemeColors(...)
        val LIGHT = ThemeColors(...)
    }
}

class ThemeProvider private constructor(
    private val appContext: Context,
) {
    var currentMode: ThemeMode = ThemeMode.DARK
        private set

    val colors: ThemeColors
        get() = when (currentMode) {
            ThemeMode.DARK -> ThemeColors.DARK
            ThemeMode.LIGHT -> ThemeColors.LIGHT
            ThemeMode.SYSTEM -> if (isSystemDark()) ThemeColors.DARK else ThemeColors.LIGHT
        }

    // --- Color accessors ---
    val background: Int get() = colors.background
    val surface: Int get() = colors.surface
    val cardBackground: Int get() = colors.cardBackground
    val text: Int get() = colors.text
    val textSecondary: Int get() = colors.textSecondary
    val accent: Int get() = colors.accent
    val accentMuted: Int get() = colors.accentMuted
    val muted: Int get() = colors.muted
    val mutedDim: Int get() = colors.mutedDim

    // --- Drawable factories ---
    fun glassBg(radiusDp: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable
    fun cardBackground(alpha: Int = 190): GradientDrawable
    fun panelBackground(cornerDp: Int, alpha: Int = 220): GradientDrawable

    // --- Persistence ---
    fun setMode(mode: ThemeMode)     // saves to prefs, notifies listeners
    fun toggle(): ThemeMode          // DARK ⇄ LIGHT

    // --- System follow ---
    private fun isSystemDark(): Boolean

    // --- Listener for live updates ---
    fun addListener(listener: (ThemeMode) -> Unit)
    fun removeListener(listener: (ThemeMode) -> Unit)

    companion object {
        @Volatile private var instance: ThemeProvider? = null
        fun getInstance(context: Context): ThemeProvider {
            return instance ?: synchronized(this) {
                instance ?: ThemeProvider(context.applicationContext).also { instance = it }
            }
        }
    }
}
```

---

## Appendix C: ThemeProvider location

```
app/src/main/java/com/runestone/app/ui/theme/
├── ThemeColors.kt
├── ThemeMode.kt
├── ThemeProvider.kt
└── ThemeUtils.kt      (dp, spacer, makeLiquid, animTap)
```
