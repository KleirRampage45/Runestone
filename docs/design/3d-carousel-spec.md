# 3D Game Card Carousel — Design Specification

> **Status:** Draft v1
> **Applies to:** Runestone multi-engine game launcher
> **Target UI Mode:** `CAROUSEL_3D`

---

## 1. Overview

Replace the flat grid game list with an interactive 3D carousel ("The Shelf"). Cards sit on a perspective arc, edge cards are scaled/rotated/blurred, and the focused card's artwork drives the ambient color of the surrounding glass UI.

The current grid remains available via a new **UI Mode** setting. The carousel is one of four modes: `GRID`, `CAROUSEL_3D`, `LIST`, `TILES`.

---

## 2. UI Mode System

### 2.1 Enum

```kotlin
enum class UIMode(val label: String, val description: String) {
    GRID("Grid", "Default card grid"),
    CAROUSEL_3D("3D Shelf", "Perspective carousel"),
    LIST("Compact List", "Vertical text list"),
    TILES("Tiles", "Smaller cards in rows"),
}
```

### 2.2 Settings Storage

Add to `RunnerSettings`:

```kotlin
data class RunnerSettings(
    // ... existing fields
    val uiMode: UIMode = UIMode.GRID,
)
```

Default is `GRID` (current behavior, backward compatible).

### 2.3 How It Affects HomeScreen

`HomeScreen.create()` currently renders a vertical scroll of game cards. Instead:

```kotlin
when (settings.uiMode) {
    UIMode.GRID -> renderGridLayout()
    UIMode.CAROUSEL_3D -> renderCarousel3D()
    UIMode.LIST -> renderListLayout()
    UIMode.TILES -> renderTileLayout()
}
```

Each render method produces a full-width view that fills the same content area. The RESUME banner, dock, filters/search, and other chrome remain the same regardless of mode.

### 2.4 Settings Screen

Add a "UI Mode" selector in SettingsScreen, similar to the Layout Mode selector:

```
┌──────────────────────────────┐
│  UI Mode                     │
│  Choose how games appear.    │
│                              │
│  ┌────────┐ ┌────────┐      │
│  │ GRID   │ │3D SHELF│      │
│  │ [selected] │ [      ]    │
│  └────────┘ └────────┘      │
│  ┌────────┐ ┌────────┐      │
│  │ LIST   │ │ TILES  │      │
│  │ [      ] │ [      ]      │
│  └────────┘ └────────┘      │
└──────────────────────────────┘
```

---

## 3. The 3D Carousel — Visual Design

### 3.1 Layout

```
┌──────────────────────────────────────────────────────┐
│  ☰ LIBRARY    🔍 [_find games..._]    ⚙️              │
│                                                        │
│       ◀═══     CAROUSEL ARC    ═══▶                    │
│                                                        │
│     ┌──┐      ┌────┐      ┌──┐    ┌────┐              │
│  ┌──┤  ├──┐  │    │  ┌──┤  ├──┐ │    │  ┌──┐         │
│  │  │  │  │  │ 🎯 │  │  │  │  │ │ 🎯 │  │  │         │
│  └──┤  ├──┘  │    │  └──┤  ├──┘ │    │  └──┘         │
│     └──┘      └────┘      └──┘    └────┘              │
│                                                        │
│  pos: -2       -1        0      +1       +2           │
│  (past)     (prev)   (FOCUS)  (next)   (future)       │
│                                                        │
│  ┌────────────────────────────────────────┐            │
│  │  Dark Souls - Prepare to Die Edition    │            │
│  │  ⭐⭐⭐⭐☆  |  🕐 127.4h  |  💾 15 saves │            │
│  │  Last played: 2 hours ago               │            │
│  │                                          │            │
│  │  [ ▶ PLAY ]  [ ⚙️ Settings ]  [ 📷 Gallery ]│         │
│  └────────────────────────────────────────┘            │
│                                                        │
│  DOCK: [LIBRARY] [STORE] [SETTINGS]                    │
└──────────────────────────────────────────────────────┘
```

### 3.2 Card Appearance

| Property | Center (pos 0) | Adjacent (pos ±1) | Far (pos ±2+) |
|----------|---------------|-------------------|---------------|
| **Scale** | 1.0 (100%) | 0.82 (82%) | 0.65 (65%) |
| **Y-Rotation** | 0° | ±22° | ±45° |
| **Alpha** | 1.0 | 0.75 | 0.45 |
| **Blur** (API 31+) | 0px | 2px Gaussian | 4px Gaussian |
| **Elevation** | 14dp | 6dp | 2dp |
| **Z-Translation** | 0dp | -40dp | -80dp |

### 3.3 Card Dimensions

- **Width:** 260dp (center)
- **Height:** 360dp (center)  
- **Corner radius:** 22dp
- **Aspect ratio:** ~1:1.38 (portrait game art)
- **Spacing between cards:** 8dp (center gap at rest)

### 3.4 Animation Values

| Motion | Duration | Curve |
|--------|----------|-------|
| Snap to center after fling | 350ms | `OvershootInterpolator(1.2)` |
| Card transform on scroll | Per frame | Linear (interpolated by scroll offset) |
| Color transition (ambient) | 450ms | `SmartSpring` (bouncy decay) |
| Inspect mode (long-press) | 250ms | `AnticipateOvershoot(1.5)` |
| Detail panel slide-up | 300ms | `DecelerateInterpolator` |

---

## 4. Technical Architecture

### 4.1 Component Tree

```
MainActivity
└── rootContainer (FrameLayout)
    ├── HomeScreen
    │   ├── TopBar
    │   ├── RESUME banner (conditional)
    │   ├── [Mode-Specific Content]
    │   │   ├── UIMode.GRID → renderGridLayout() (existing LinearLayout scroll)
    │   │   ├── UIMode.CAROUSEL_3D → renderCarousel3D()
    │   │   │   ├── RecyclerView
    │   │   │   │   ├── Carousel3DLayoutManager (custom)
    │   │   │   │   └── GameCarouselAdapter
    │   │   │   │       └── CarouselGameCard (item view)
    │   │   │   ├── DetailPanel (below carousel)
    │   │   │   └── AmbientGlowView (behind carousel)
    │   │   ├── UIMode.LIST → renderListLayout()
    │   │   └── UIMode.TILES → renderTileLayout()
    │   ├── Dock
    │   └── Filter/Sort bar
    └── [Overlay panels]
```

### 4.2 Dependencies to Add

In `app/build.gradle.kts`:

```kotlin
dependencies {
    // Existing
    implementation("com.intuit.sdp:sdp-android:1.1.0")
    
    // New — RecyclerView (for carousel)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    
    // New — Palette color extraction
    implementation("androidx.palette:palette:1.0.0")
    
    // Optional — Material components (for MaskableFrameLayout if needed)
    // implementation("com.google.android.material:material:1.13.0")
}
```

### 4.3 Carousel3DLayoutManager

A custom `RecyclerView.LayoutManager` that:

1. **Measures children** at fixed width=260dp, height=360dp
2. **Positions children** in a horizontal scrollable arc
3. **Calculates per-child transforms** based on scroll offset:

```kotlin
class Carousel3DLayoutManager : RecyclerView.LayoutManager() {
    
    private var scrollOffset = 0f
    private val cardWidth = dp(260)
    private val cardHeight = dp(360)
    private val cardSpacing = dp(8)
    private val centerPercent = 0.35f // card center at 35% from left
    
    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        // Detach + recycle all
        // Fill visible area with cards
        val centerX = width * centerPercent
        var currentX = centerX - scrollOffset
        
        for (i in 0 until itemCount) {
            val pos = positionToIndex(currentX, centerX)
            if (pos < -2 || pos > 2) {
                // Skip — beyond visible arc
            }
            
            val view = recycler.getViewForPosition(i)
            addView(view)
            measureChildWithMargins(view, cardWidth, cardHeight)
            
            val transform = calculateTransform(pos)
            applyTransform(view, transform)
            
            layoutView(view, currentX - cardWidth/2, centerY - cardHeight/2,
                       currentX + cardWidth/2, centerY + cardHeight/2)
            
            currentX += cardWidth + cardSpacing
        }
    }
    
    private fun calculateTransform(position: Int): CardTransform {
        val absPos = abs(position)
        return CardTransform(
            scale = lerp(1.0f, 0.65f, absPos / 2f),
            rotationY = sign(position) * lerp(0f, 45f, absPos / 2f),
            alpha = lerp(1.0f, 0.45f, absPos / 2f),
            zTranslation = -lerp(0f, 80f, absPos / 2f),
        )
    }
}
```

**Key design decisions:**
- Cards are laid out horizontally with fixed spacing
- Only positions -2 to +2 are rendered at any time (plus partial -3/+3)
- Transforms are interpolated continuously (not snapped to discrete positions)
- `scrollHorizontallyBy()` updates `scrollOffset` and calls `offsetChildrenHorizontal()`

**Snap behavior:**
```kotlin
override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
    // Snap target position to center
    val targetX = position * (cardWidth + cardSpacing)
    recyclerView.smoothScrollBy(targetX - scrollOffset.toInt(), 0)
}
```

Use `LinearSmoothScroller` with centered snapping:
```kotlin
override fun calculateDtToFit(viewStart, viewEnd, boxStart, boxEnd, viewVelocity): Int {
    // Snap to center — calculate distance to center the view
    val viewCenter = (viewStart + viewEnd) / 2
    val containerCenter = (boxStart + boxEnd) / 2
    return (containerCenter - viewCenter) / 2 // half speed for smooth feel
}
```

### 4.4 GameCarouselAdapter

```kotlin
class GameCarouselAdapter(
    private val games: List<GameCardInfo>,
    private val onCardClicked: (GameCardInfo) -> Unit,
    private val onCardLongPressed: (GameCardInfo) -> Unit,
) : RecyclerView.Adapter<GameCarouselAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = CarouselGameCard(parent.context)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(games[position])
    }
    
    override fun getItemCount() = games.size
    
    class ViewHolder(val card: CarouselGameCard) : RecyclerView.ViewHolder(card)
}
```

### 4.5 CarouselGameCard (Item View)

A custom `FrameLayout` that renders a game card with:

```
┌─────────────────────────────┐
│  ┌───────────────────────┐  │
│  │                       │  │
│  │    Cover Art /        │  │
│  │    Game Title          │  │
│  │                       │  │
│  │                       │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │  Engine Badge   ⭐⭐│  │
│  └───────────────────────┘  │
└─────────────────────────────┘
```

Background: Glass-style (semi-transparent with stroke, matching current card style)
Cover art: `ImageView` with Glide/Coil for image loading (future — for now, title text)
Title: Game name overlay
Badge: Engine type indicator (e.g., "RGSS", "MV", "MZ")

Makes itself clickable. Long-press triggers inspect mode.

### 4.6 DetailPanel

Below the carousel, shows information about the focused (center) game:

- **Title** — large serif text
- **Rating** / Hours / Save count
- **Last played** timestamp
- **Action buttons** — PLAY (primary), SETTINGS, GALLERY

Implementation as a `LinearLayout` that receives the focused game info and updates when the carousel scrolls.

### 4.7 AmbientGlowView

A full-width `View` behind the carousel that renders a gradient based on the focused game's dominant color.

**Color extraction flow:**
1. When a game becomes focused, check if we have a cached color
2. If not, load the cover art bitmap on a background thread
3. Run `Palette.from(bitmap).generate()` 
4. Extract `VibrantSwatch` or `MutedSwatch` color
5. Cache the color in memory (Map<String, Int>) 
6. Apply as a radial gradient behind the carousel

```kotlin
class AmbientGlowView(context: Context) : View(context) {
    private var currentColor: Int = Color.argb(30, 207, 174, 126) // default accent
    private var targetColor: Int = currentColor
    private var animProgress: Float = 1f
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    fun transitionToColor(newColor: Int) {
        targetColor = newColor
        animProgress = 0f
        // Start value animator from 0→1 over 450ms
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { 
                animProgress = it.animatedFraction
                invalidate()
            }
            start()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val blended = blendColors(currentColor, targetColor, animProgress)
        // Draw radial gradient from center-top to edges
        val gradient = RadialGradient(
            width / 2f, height * 0.3f, maxOf(width, height) * 0.7f,
            blended, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
```

---

## 5. Blur Effect (API 31+)

Edge cards get subtle blur using `RenderEffect`:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val blurRadius = when (absPos) {
        0 -> 0f
        1 -> 2f
        else -> 4f
    }
    if (blurRadius > 0f) {
        view.setRenderEffect(RenderEffect.createBlurEffect(
            blurRadius, blurRadius, Shader.TileMode.CLAMP
        ))
    } else {
        view.setRenderEffect(null)
    }
}
```

For API < 31 (Android 11 and below, ~13% of devices):
- Skip blur entirely
- Edge cards still get scale/rotation/alpha transforms
- The visual effect is still compelling without blur

---

## 6. Color Extraction via Palette API

### 6.1 Integration

```kotlin
class GameColorExtractor(private val context: Context) {
    
    private val colorCache = mutableMapOf<String, Int>()
    private val defaultAccent = Color.rgb(207, 174, 126)
    
    suspend fun getDominantColor(gameTitle: String, coverUrl: String?): Int {
        // Check cache first
        colorCache[gameTitle]?.let { return it }
        
        // No cover art? Return default
        if (coverUrl == null) return defaultAccent
        
        // Load bitmap and extract
        return withContext(Dispatchers.IO) {
            try {
                // For now, skip actual bitmap loading (no cover art yet)
                // Future: load from URL or local cache
                defaultAccent
            } catch (e: Exception) {
                defaultAccent
            }
        }.also { colorCache[gameTitle] = it }
    }
}
```

**Note:** Cover art URLs exist in `AvailableGame.coverUrl` but not yet in `GameCardInfo`. The carousel spec for Phase 1 uses default accent colors. Phase 2 adds cover art loading + color extraction.

### 6.2 Fallback Colors

If cover art isn't available or extraction fails, use engine-based defaults:

| Engine | Default Tint |
|--------|-------------|
| RGSS (XP/VX/VA) | Warm amber `Color(207, 174, 126)` |
| MV / MZ | Cool blue `Color(130, 170, 210)` |
| EasyRPG (2k/3) | Vintage green `Color(140, 180, 140)` |
| Ren'Py | Soft pink `Color(200, 150, 170)` |
| Godot | Teal `Color(100, 190, 190)` |
| Ruffle (Flash) | Purple `Color(180, 140, 200)` |
| Unknown | Neutral gray `Color(150, 150, 160)` |

---

## 7. Inspect Mode

When a card is long-pressed:

1. Background dims to 60% black overlay
2. Focused card scales up to 1.05x, elevates to 20dp shadow
3. A flyout panel appears below/over the card with quick actions:
   - **PLAY** (primary)
   - **Settings** (per-game engine, controls)
   - **Screenshot Gallery** (future)
   - **Delete Game Data**
4. Tap outside → returns to normal carousel

**Implementation:**
```kotlin
fun enterInspectMode(cardView: View, gameInfo: GameCardInfo) {
    // 1. Create dim overlay
    val dim = View(context).apply {
        setBackgroundColor(Color.argb(150, 0, 0, 0))
        alpha = 0f
        animate().alpha(1f).duration = 250
    }
    rootContainer.addView(dim, MATCH_PARENT, MATCH_PARENT)
    
    // 2. Scale up card
    cardView.animate()
        .scaleX(1.05f).scaleY(1.05f)
        .translationZ(dp(20))
        .setDuration(250)
        .setInterpolator(AnticipateOvershootInterpolator(1.5f))
        .start()
    
    // 3. Show action panel
    showInspectPanel(gameInfo)
}
```

---

## 8. Implementation Phases

### Phase 1: Foundation (Estimated: 6-8 hours)
- [ ] Add `recyclerview` and `palette` dependencies to build.gradle.kts
- [ ] Create `UIMode` enum in `RunnerSettings`
- [ ] Add `uiMode` field to `RunnerSettings` with default `GRID`
- [ ] Create `Carousel3DLayoutManager` (basic horizontal layout, no 3D transforms yet)
- [ ] Create `GameCarouselAdapter`
- [ ] Create `CarouselGameCard` view
- [ ] Wire `renderCarousel3D()` in HomeScreen
- [ ] Add UI Mode selector to SettingsScreen
- [ ] **Verify:** Build passes, carousel renders flat cards horizontally

### Phase 2: 3D Transforms (Estimated: 4-6 hours)
- [ ] Implement `calculateTransform()` with scale/rotation/alpha/z
- [ ] Add `setCameraDistance()` to RecyclerView
- [ ] Add dynamic recalculations on scroll via `scrollHorizontallyBy()`
- [ ] Implement snap-to-center behavior
- [ ] Add blur effect for edge cards (API 31+)
- [ ] Add elevation/shadow to cards
- [ ] **Verify:** Cards arc in 3D, scrolling snaps, edges are blurred

### Phase 3: Detail Panel + Glow (Estimated: 3-4 hours)
- [ ] Create `DetailPanel` view
- [ ] Wire carousel scroll events → DetailPanel updates
- [ ] Create `AmbientGlowView`
- [ ] Implement `GameColorExtractor` with Palette API
- [ ] Wire color extraction → AmbientGlowView transitions
- [ ] **Verify:** Detail panel shows focused game info, background tints change

### Phase 4: Inspect Mode + Polish (Estimated: 3-4 hours)
- [ ] Implement long-press inspect mode
- [ ] Add flyout action panel
- [ ] Add RESUME banner integration (show over carousel if game is paused)
- [ ] Add page indicator dots
- [ ] Add "no games" empty state for carousel mode
- [ ] **Verify:** Full UX flow works end-to-end

### Phase 5: LIST + TILES Modes (Estimated: 2-3 hours)
- [ ] Implement `renderListLayout()` — RecyclerView with vertical compact rows
- [ ] Implement `renderTileLayout()` — GridLayoutManager with small cards
- [ ] Add mode preview icons to Settings
- [ ] **Verify:** All four modes work, settings save/restore correctly

### Phase 6: Cover Art + Polish (Estimated: 4-6 hours)
- [ ] Add `coverUrl` field to `GameCardInfo`
- [ ] Load cover art into `CarouselGameCard` ImageView (Glide or Coil)
- [ ] Wire cover art → Palette color extraction → AmbientGlowView
- [ ] Add drag-to-reorder (long-press + drag on carousel cards)
- [ ] Add fling momentum tweaking
- [ ] Performance optimization (view recycling, bitmap caching)
- [ ] **Verify:** Cover art loads, colors match, perf is smooth

---

## 9. Performance Considerations

| Concern | Solution |
|---------|----------|
| **View recycling** | RecyclerView handles this — only 5-7 cards inflated at once |
| **Color extraction** | Run on background thread, cache results |
| **Blur effect** | `RenderEffect` is GPU-accelerated — negligible cost on API 31+ |
| **Animation jank** | All transforms use hardware layers — `view.setLayerType(HARDWARE)` |
| **Bitmap memory** | Use downscaled thumbnails for palette extraction (max 150px) |

**Hardware acceleration note:**
All card views should use `view.setLayerType(View.LAYER_TYPE_HARDWARE, null)` during scroll animation to prevent repeated redraws. Revert to `LAYER_TYPE_NONE` when stationary to save GPU memory.

---

## 10. Edge Cases

| Edge Case | Handling |
|-----------|----------|
| **Only 1 game** | Single card centered, no scrolling possible |
| **2 games** | Both visible, card-0 centered by default |
| **No games** | Show "Add your first game" empty state |
| **Cover art missing** | Show title text with engine-themed gradient fill |
| **Palette fails** | Fall back to engine default color |
| **API < 31 (no blur)** | Cards still scale/rotate/fade — still looks good |
| **Rapid scrolling** | Transform interpolation handles smooth transitions |
| **Screen rotation** | `onSaveInstanceState` preserves scroll position |
| **Accessibility** | Cards remain tappable, TalkBack reads game title |

---

## 11. Backward Compatibility

- **Existing GRID mode is unchanged** — default remains GRID for existing users
- `RunnerSettings` gets a new field with default value — no migration needed
- All existing screens, overlays, and game launching logic are untouched
- The carousel is a **render mode swap** only in the HomeScreen content area

---

## 12. Files to Create

| File | Path | Purpose |
|------|------|---------|
| `Carousel3DLayoutManager.kt` | `ui/carousel/` | Custom RecyclerView layout manager |
| `GameCarouselAdapter.kt` | `ui/carousel/` | RecyclerView adapter |
| `CarouselGameCard.kt` | `ui/carousel/` | Individual card view |
| `DetailPanel.kt` | `ui/carousel/` | Focused game info panel |
| `AmbientGlowView.kt` | `ui/carousel/` | Background color glow |
| `GameColorExtractor.kt` | `ui/carousel/` | Palette API color extraction |
| `InspectOverlay.kt` | `ui/carousel/` | Long-press inspect mode |

## 13. Files to Modify

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add `recyclerview` and `palette` dependencies |
| `RunnerSettings.kt` | Add `uiMode: UIMode = UIMode.GRID` |
| `HomeScreen.kt` | Add mode dispatch in `create()` |
| `SettingsScreen.kt` | Add UI Mode selector panel |
| `SettingsStore.kt` | Add `uiMode` persistence (if not auto-mapped) |
| `MainActivity.kt` | Pass `settings.uiMode` to HomeScreen |

---

## 14. Visual Reference (ASCII Mockup)

```
CENTERED VIEW (5 cards visible):

          ╔══╗          ╔════╗          ╔══╗
       ╔══╣  ╠══╗    ╔═╣    ╠═╗    ╔══╣  ╠══╗
       ║  ║  ║  ║    ║ ║ 🎯 ║ ║    ║  ║  ║  ║
       ╚══╣  ╠══╝    ╚═╣    ╠═╝    ╚══╣  ╠══╝
          ╚══╝          ╚════╝          ╚══╝
       ───── ── ────    🎮    ──── ── ─────
                         ↑
                    FOCUSED CARD
                    Full size, sharp, centered
                    Casts warm amber glow on glass

SCROLLING (momentum):

          ╔══╗      ╔════╗          ╔══╗         ╔══╗
       ╔══╣  ╠══╗  ║    ║        ╔═╣  ╠═╗     ╔═╣  ╠═╗
       ║  ║  ║  ║  ║ 🎯 ║   ➡️   ║ ║  ║ ║     ║ ║  ║ ║
       ╚══╣  ╠══╝  ║    ║        ╚═╣  ╠═╝     ╚═╣  ╠═╝
          ╚══╝      ╚════╝          ╚══╝         ╚══╝
```

---

---

## 15. Screen-Space Post-Processing Effects

*The carousel's 3D transforms + ambient glow provide the core visual impact. These post-processing effects layer on top for cinematic polish — think of them as the "film look" applied to the final rendered frame.*

### 15.1 Architecture

Post-processing works on a **render-to-texture → apply shader → display** pipeline:

```
┌──────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────┐
│  Carousel │    │  Off-screen  │    │  Shader     │    │  Display  │
│  + UI     │───▶│  Bitmap      │───▶│  Chain      │───▶│  Surface  │
│  Render   │    │  (ARGB_8888) │    │  (sequenced)│    │  (View)   │
└──────────┘    └──────────────┘    └─────────────┘    └──────────┘
```

For Android View-based UI, two pipeline options exist:

| Pipeline | API Level | Performance | Quality |
|----------|-----------|-------------|---------|
| **RenderEffect chain** (Android 12+) | API 31+ | 🟢 GPU-accelerated, one pass | Good |
| **Bitmap → Canvas → shader** | API 26+ | 🟡 CPU-bound, 2-3 frame delay | Excellent |
| **OpenGL ES custom shader** | API 26+ | 🟢 Full GPU, one pass | Best |

**Recommendation:** Use `RenderEffect` for blur/tint/vignette (simplest path for API 31+). Use Bitmap→Canvas for film grain (needs manual pixel manipulation). Benchmark GLSurfaceView for chromatic aberration / bloom in Phase 7.

---

### 15.2 Effect Catalog

| # | Effect | Difficulty | API Req | Performance Cost | Status |
|---|--------|------------|---------|-----------------|--------|
| 1 | **Vignette** | 🟢 Trivial | 26+ | Negligible | 🟢 Phase 4 |
| 2 | **Film Grain** | 🟢 Easy | 26+ | Light | 🟢 Phase 4 |
| 3 | **Depth of Field (fake)** | 🟡 Medium | 31+ | Moderate | 🟡 Phase 5 |
| 4 | **Bloom (fake)** | 🟡 Medium | 31+ | Moderate | 🟡 Phase 6 |
| 5 | **Chromatic Aberration** | 🔴 Hard | 31+/GL | Heavy | 🔴 Investigate |
| 6 | **Ambient Occlusion (fake)** | 🔴 Hard | 26+ | Heavy | 🔴 Investigate |

---

### 15.3 Vignette — 🟢 Trivial

**What it does:** Darkens the corners and edges of the screen, drawing focus to the center card. Creates a cinematic "viewfinder" feel.

**Implementation:**

```kotlin
class VignetteOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = maxOf(width, height) * 0.7f
        val gradient = RadialGradient(
            width / 2f, height / 2f, radius,
            Color.TRANSPARENT,               // center — fully transparent
            Color.argb(140, 0, 0, 0),         // edges — dark
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
```

**Customization:**
- `radius`: controls how far the darkening reaches from the edge (bigger = tighter vignette)
- `alpha`: intensity of the darkening (140 = subtle, 200 = dramatic)
- Can tint the vignette using the ambient glow color instead of pure black for a more integrated look

**Performance:** One `RadialGradient.drawRect()` — effectively free at 60fps.

**Timeline:** Add to Phase 4 polish. Entire file = ~30 lines.

---

### 15.4 Film Grain — 🟢 Trivial

**What it does:** Overlays a subtle animated noise texture on the glass UI, making it look like a physical display with organic texture. Adds tactile "weight" to the dark glass surfaces.

**Two approaches:**

**A) Static noise bitmap (recommended for Phase 4):**
```kotlin
class GrainOverlay(context: Context) : View(context) {
    private val grainBitmap: Bitmap
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 12 // 12/255 = very subtle
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
    
    init {
        // Generate a small grayscale noise texture (256×256)
        val pixels = IntArray(256 * 256) { 
            val v = (Math.random() * 256).toInt()
            Color.rgb(v, v, v)
        }
        grainBitmap = Bitmap.createBitmap(pixels, 256, 256, Bitmap.Config.ARGB_8888)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Tile the noise across the screen
        for (x in 0..width step 256) {
            for (y in 0..height step 256) {
                canvas.drawBitmap(grainBitmap, x.toFloat(), y.toFloat(), grainPaint)
            }
        }
    }
}
```

**B) Animated grain (Phase 6+):**
```kotlin
// Generate a NEW noise bitmap every 2-3 frames
// Use a handler or Choreographer to invalidate at ~20fps
// Cycle through 3-4 pre-generated noise bitmaps for cheap animation
object GrainAnimator {
    private val frames = List(4) { generateNoiseBitmap() }
    private var currentFrame = 0
    
    fun nextFrame(): Bitmap = frames[currentFrame].also {
        currentFrame = (currentFrame + 1) % frames.size
    }
}
```

**Key tuning:**
- Alpha: 8-15 for subtle, 20-30 for visible. Default = **10**.
- Grain size: 256×256 tiled works well. Larger sizes look more like static noise.
- Animated vs static: Static is invisible unless you look for it. Animated is noticeable but can be distracting. Default = **static**.

**Performance:** Minimal with static bitmap (just tile-blits). Animated generation needs a background thread at 5-10fps.

**Timeline:** Static → Phase 4. Animated → Phase 6 polish.

---

### 15.5 Depth of Field (Fake) — 🟡 Medium

**What it does:** Blurs elements behind the focused card in the z-plane, simulating a camera lens focusing on the selected game.

**We already have this partially** — edge cards are blurred via `RenderEffect`. "Fake DOF" extends this to the background glass UI elements too.

**Implementation (Phase 5):**

```kotlin
// During scroll, blur the ambient glow background more intensely
// when cards are in motion, creating a "rack focus" effect

class DepthOfFieldController(private val glowView: AmbientGlowView) {
    
    fun onScroll(velocity: Float, focusPosition: Int) {
        val blurIntensity = when {
            // Fast scroll = heavy background blur (like a camera struggling to track)
            abs(velocity) > 2000f -> 24f
            // Slow scroll = light blur
            abs(velocity) > 500f -> 8f
            // Stationary = sharp
            else -> 0f
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            glowView.setRenderEffect(
                RenderEffect.createBlurEffect(
                    blurIntensity, blurIntensity, Shader.TileMode.CLAMP
                )
            )
        }
    }
}
```

**Also applicable to:**
- The dock (blur dock during carousel scroll)
- Top bar (blur during scroll)
- Non-focused game detail elements

**Timeline:** Phase 5 — after core carousel is stable.

---

### 15.6 Bloom (Fake) — 🟡 Medium

**What it does:** Makes bright elements "glow" — accent borders, text highlights, and the ambient glow itself get a soft luminous halo.

**Implementation (Phase 6):**

The "poor man's bloom" uses composited alpha layers:

```kotlin
// On the AmbientGlowView:
class BloomOverlay(context: Context) : View(context) {
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val blurMask = BlurMaskFilter(dp(12).toFloat(), BlurMaskFilter.Blur.NORMAL)
    
    fun applyBloom(canvas: Canvas, accentColor: Int) {
        // 1. Draw the accent color at low opacity with a blur mask
        bloomPaint.color = accentColor
        bloomPaint.maskFilter = blurMask
        
        // 2. Overlay on the accent regions (text, borders, glow)
        // Since we can't easily isolate "bright" pixels from a View hierarchy,
        // we target specific known elements:
        // - Ambient glow center
        // - Accent text elements (rendered to off-screen bitmap first)
        // - Card borders
        
        // Simplified: just bloom the ambient glow center
        val cx = width / 2f
        val cy = height * 0.35f
        canvas.drawCircle(cx, cy, dp(80).toFloat(), bloomPaint)
    }
}
```

**Real bloom (for future investigation):**
True bloom in game engines:
1. Render scene to HDR buffer
2. Extract bright pixels (luminance > threshold)
3. Apply gaussian blur
4. Composite back with additive blending

On Android Canvas, steps 2-4 require pixel-level manipulation on a Bitmap. This is CPU-bound and slow. An **OpenGL ES** path via `GLSurfaceView` would give full HDR bloom but requires rewriting the carousel as a GL scene.

**Fake bloom tradeoff:** Looks good on dark backgrounds (our glass UI), less convincing on light themes. Good enough for v1.

**Timeline:** Phase 6 — after core is stable. GL path deferred to Investigation Phase.

---

### 15.7 Chromatic Aberration — 🔴 Hard

**What it does:** Splits the RGB channels at the edges of the screen, creating colored fringing (red/cyan separation). Gives a "lens distortion" cinematic feel.

**True CA — OpenGL ES approach (for investigation):**
```glsl
// Fragment shader — offsets each color channel by a different amount
// based on distance from center
uniform sampler2D uTexture;
uniform vec2 uScreenSize;
varying vec2 vTexCoord;

void main() {
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(vTexCoord, center);
    float strength = dist * 0.02; // intensity increases toward edges
    
    float r = texture2D(uTexture, vTexCoord + vec2(strength, 0.0)).r;
    float g = texture2D(uTexture, vTexCoord).g;
    float b = texture2D(uTexture, vTexCoord - vec2(strength, 0.0)).b;
    
    gl_FragColor = vec4(r, g, b, 1.0);
}
```

**Canvas-based CA (slow, Phase 7 experiment):**
```kotlin
// Render carousel to Bitmap, then draw it 3 times with slight offsets
fun applyChromaticAberration(canvas: Canvas, source: Bitmap) {
    val strength = dp(2).toFloat() // 2px offset at edges
    
    // Red channel — shifted right
    val redPaint = Paint().apply { colorFilter = ColorFilter().redOnly() }
    canvas.drawBitmap(source, strength, 0f, redPaint)
    
    // Green channel — centered
    canvas.drawBitmap(source, 0f, 0f, greenPaint)
    
    // Blue channel — shifted left
    canvas.drawBitmap(source, -strength, 0f, bluePaint)
}
```

**Apple's approach (investigation target):**
Apple's UI (visionOS, iOS) likely uses **Metal compute shaders** applied as a screen-space pass over the entire UIKit rendering. They can do this because:
1. They control the entire rendering pipeline
2. Metal gives them direct GPU access
3. They render UIKit into a Metal texture, then apply post-processing

**Android equivalent:**
- `GLSurfaceView` with custom fragment shaders
- `RenderEffect` is Apple's closest equivalent — but no color-channel splitting exists in the built-in effects
- A custom `RenderNode` + `RenderEffect` subclass *might* work via `RenderEffect.createRuntimeShaderEffect()` (API 34+)

**Current verdict:** Chromatic aberration in native Android UI is **not worth the performance cost** for the visual return. It's a subtle effect that only ~30% of users notice, but it costs 2-3ms per frame if done via Bitmap compositing, or requires porting the entire carousel to GLSurfaceView.

**Recommendation:** Defer to **Phase 7 — Investigation**. Tag as "experimental, GLSurfaceView path." If we ever port the carousel to GLSurfaceView for other reasons (HardwareBuffer compositing, unified shader pipeline), CA comes for free.

---

### 15.8 Ambient Occlusion (Fake) — 🔴 Hard

**What it does:** Darkens the crevices where objects meet — where cards overlap, or where the carousel shelf meets the background. Gives a sense of physical lighting.

**True AO needs a depth buffer.** We don't have one in View-land.

**Fake approaches:**

**A) Per-card shadow gradients (Phase 4):**
```kotlin
// Each card already has elevation-based shadow via setElevation().
// Enhance by adding a linear gradient on the CARD itself:
// dark at bottom/leading edge, light at top/trailing
val shadowGradient = LinearGradient(
    0f, 0f, cardWidth.toFloat(), 0f,
    Color.argb(30, 0, 0, 0), Color.TRANSPARENT,
    Shader.TileMode.CLAMP
)
// This fakes "contact shadow" between adjacent cards
```

**B) Background darkening between cards (Phase 6):**
The space between carousel cards is naturally dark (our glass UI background). Adding a subtle vertical stripe between visible cards enhances the illusion of depth:

```kotlin
class ShelfShadowView(context: Context) : View(context) {
    override fun onDraw(canvas: Canvas) {
        // Draw vertical gradient stripes at card boundaries
        // Darken the gaps between cards
        for (cardX in cardPositions) {
            val left = cardX + cardWidth // right edge of this card
            val right = nextCardX // left edge of next card
            val gapWidth = right - left
            
            val gradient = LinearGradient(
                left, 0f, right, 0f,
                Color.argb(60, 0, 0, 0), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
            canvas.drawRect(left, 0f, right, height.toFloat(), paint)
        }
    }
}
```

**Verdict:** Fake AO is barely noticeable. True AO requires a depth-pass render. **Skip for v1.** Investigate if we move to GLSurfaceView later.

---

### 15.9 Effect Stacking — Combined Performance

Each effect adds a draw pass. Stacked together, here's the estimated frame budget:

```
Base carousel (5 views, transforms):    ~4ms
Vignette overlay:                       ~0.1ms
Film grain (static):                    ~0.3ms  
Depth of Field (blur glow):             ~1ms (API 31+)
Bloom (fake, single accent):            ~0.5ms
─────────────────────────────────────────
Total (with all active):                ~5.9ms  → ~169fps headroom
                                        (at 60fps budget = 16.6ms)
```

Even with ALL effects enabled, we're well under 16ms. The expensive stuff (CA, AO, real bloom) is deferred — but the practical effects cost almost nothing.

---

### 15.10 Implementation Plan Summary

| Phase | Post-Processing |
|-------|----------------|
| **4** | Vignette (trivial), Static Film Grain (trivial) |
| **5** | Depth of Field — blur ambient glow during scroll |
| **6** | Fake Bloom (accent glow), Animated Film Grain |
| **7 — Investigate** | GLSurfaceView pipeline, Chromatic Aberration shader, True AO |
| **Future** | Apple-style Metal equivalents — track `RenderEffect.createRuntimeShaderEffect()` on API 34+ |

### 15.11 Files to Create

| File | Path | Purpose |
|------|------|---------|
| `VignetteOverlay.kt` | `ui/carousel/effects/` | Cinematic corner darkening |
| `GrainOverlay.kt` | `ui/carousel/effects/` | Film grain noise overlay |
| `DepthOfFieldController.kt` | `ui/carousel/effects/` | Dynamic blur during scroll |
| `BloomOverlay.kt` | `ui/carousel/effects/` | Accent glow amplification |
| `PostProcessingPipeline.kt` | `ui/carousel/effects/` | Effect chain orchestrator |

---

## 16. Open Questions

1. **Cover art source** — currently `GameCardInfo` has no `coverUrl`. Should this come from the game directory (scan for `title.png`, `boxart.png`) or from the provider catalogue?
2. **Game hours tracking** — do we track play time? Not yet implemented. The detail panel shows "—" for now.
3. **Save file count** — `SaveManager.listSaves()` exists and works.
4. **Drag-to-reorder** — requires `ItemTouchHelper` and persisting the order to SharedPreferences.
5. **Ambient particle effects** — lower priority, can add sparkle/snow/dust particles using `Canvas` drawing on the `AmbientGlowView`.
6. **Cover art download** — could be done when browsing the provider store, cached locally.
7. **Chromatic Aberration on Apple platforms** — How does Apple implement real-time CA in UIKit/SwiftUI? Investigate Metal compute shader approach and whether `RenderEffect.createRuntimeShaderEffect()` (API 34+) can approximate it. Reference: visionOS glass UI has subtle CA at lens edges. Track Metal Shader Conformance for future Android GLSurfaceView path.
