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

## 15. Open Questions

1. **Cover art source** — currently `GameCardInfo` has no `coverUrl`. Should this come from the game directory (scan for `title.png`, `boxart.png`) or from the provider catalogue?
2. **Game hours tracking** — do we track play time? Not yet implemented. The detail panel shows "—" for now.
3. **Save file count** — `SaveManager.listSaves()` exists and works.
4. **Drag-to-reorder** — requires `ItemTouchHelper` and persisting the order to SharedPreferences.
5. **Ambient particle effects** — lower priority, can add sparkle/snow/dust particles using `Canvas` drawing on the `AmbientGlowView`.
6. **Cover art download** — could be done when browsing the provider store, cached locally.
