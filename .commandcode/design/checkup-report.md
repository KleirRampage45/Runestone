# Runestone — Checkup Report

**Date:** 2026-06-04
**Score:** 35/60
**Verdict:** Watch — accessible only to sighted touch users

---

## Heuristic Scores

| # | Vital Sign | Score | Key Finding |
|---|---|---|---|
| 1 | Intentionality | 10/10 | Strong authored language — glassmorphism, palette system, custom views. No defaults. |
| 2 | Readability | 5/10 | MUTED_DIM (#645F55) fails AA contrast (3.96:1) on dark bg. No line-height control. |
| 3 | Usability | 10/10 | Clear task flows. Search, filter, sort, dock nav all present. Resume bar works. |
| 4 | Responsiveness | 5/10 | SP text scaling and dynamic widths present, but fixed carousel cards and no tablet/notch handling. |
| 5 | Speed | 5/10 | RecyclerView + background threads solid, but 5+ carousel overlays risk jank on low-end devices. |
| 6 | Accessibility | 0/10 | **Critical.** No screen reader support. 40dp touch targets. No reduced motion. |

---

## Priority Issues

### [P0] Screen reader users are locked out
- Zero `contentDescription` attributes on any ImageView, including all 5 dock navigation icons (home, store, add, files, settings).
- `GlassSlider` has no accessibility delegate — a blind user cannot adjust text scale, button opacity, or any slider.
- `LayoutPreviewView` is a decorative custom View without any accessibility metadata.
- `AmbientGlowView`, `VignetteOverlay`, `GrainOverlay`, `BloomOverlay` are decorative but not marked as `importantForAccessibility = NO`.

**Fix:** Add `contentDescription` to every actionable ImageView. Set `importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO` on decorative views. Extend `GlassSlider` with `AccessibilityDelegate`. Use `/design interaction` for comprehensive state work.

### [P1] Touch targets below Android minimum
- Dock icon wrappers are `dp(40)` — below the 48dp minimum. Affects all 5 navigation items.
- Clear button in search bar: padding `dp(8)` on each side of `textSize = 14f` "X" — effective width ~30dp.
- Engine filter chips: `setPadding(dp(10), dp(7), dp(10), dp(7))` with 11f text — ~44dp width, borderline.

**Fix:** Increase dock item wrappers to `dp(48)`. Expand search clear button hit area with `TouchDelegate` or padding. Use `/design interaction`.

### [P1] MUTED_DIM contrast fails AA
- `Color.rgb(100, 95, 85)` = `#645F55` on `#0F0E10` bg = 3.96:1 ratio.
- Used for: search hints, file counts in detail panel, secondary labels, settings descriptions.
- Below 4.5:1 AA threshold for normal text.

**Fix:** Lighten MUTED_DIM to `Color.rgb(120, 112, 104)` = `#787068` for 4.5:1. Use `/design recolor`.

### [P2] No reduced-motion support
- `OvershootInterpolator(1.5f)` used on every `animTap` and `makeLiquid`.
- Gear spin animation runs regardless of user preference.
- No `Settings.Global.ANIMATOR_DURATION_SCALE` check or Android accessibility animation setting.

**Fix:** Add `prefers-reduced-motion` check via `Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)`. Add a "Reduce Motion" toggle in Settings. Use `/design motion`.

### [P2] Multiple carousel overlay layers risk frame drops
- 5+ simultaneous overlay Views: `AmbientGlowView`, `VignetteOverlay`, `GrainOverlay`, `BloomOverlay`, `DepthOfFieldController`.
- No `setLayerType(LAYER_TYPE_HARDWARE, null)` calls on static layers.

**Fix:** Set `LAYER_TYPE_HARDWARE` on `VignetteOverlay`, `GrainOverlay`, and `BloomOverlay`. Profile on low-end device. Use `/design motion` or `/design finish`.

---

## What's Working

- **Coherent visual language.** Glassmorphism panels, amber-default palette, serif titles, engine-colored gradients — every element looks like Runestone, not a template.
- **Filter and sort is well-executed.** Glass overlay with engine chips and sort options, debounced search, clear state management. One of the better mobile filter UX patterns.
- **Skeleton loading is present.** Pulses on available games screen, staggered delays. Makes loading feel fast.
- **Resume bar is thoughtful.** STOP with glass confirmation dialog, RESUME side by side. Saves warning is clear.
- **Carousel is genuinely ambitious.** Ambient glow, color extraction, DOF blur, vignette, grain — the cinematographic layering is unusual and intentional for a game launcher.

---

## Next Modes

1. `/design interaction` — accessibility fixes, touch targets, state completion (P0, P1)
2. `/design recolor` — fix MUTED_DIM contrast, audit palette against colorblind filters (P1)
3. `/design motion` — reduced-motion support, hardware layer optimization (P2)
4. `/design responsive` — tablet layouts, carousel card scaling, notch handling
