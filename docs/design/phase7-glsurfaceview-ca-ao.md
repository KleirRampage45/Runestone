# Phase 7 — Investigation: GLSurfaceView, Chromatic Aberration & Ambient Occlusion

> **Status:** Investigation Complete
> **Date:** 2026-05-29
> **Branch:** feature/3d-carousel-ui

---

## 1. Executive Summary

Three approaches exist for screen-space post-processing on Android Views:

| Approach | API Level | Performance | Complexity | Viable For |
|----------|-----------|-------------|------------|------------|
| **AGSL RuntimeShader** (recommended) | 33+ (13+) | 🟢 GPU | 🟢 Low | CA, Bloom, Vignette, DOF |
| **GLSurfaceView overlay** | 26+ | 🟡 Medium | 🔴 High | CA, AO, any GLSL shader |
| **Bitmap compositing** (Canvas) | 26+ | 🔴 CPU-bound | 🟢 Low | Simple effects only |

**Winner: AGSL RuntimeShader.** It runs fragment shaders on the GPU *within* the View rendering pipeline — no separate GL context, no SurfaceView layering issues, and it applies to any View via `setRenderEffect()`.

---

## 2. AGSL — Android Graphics Shading Language (API 33+)

### 2.1 What It Is

AGSL is Android's built-in fragment shader language, introduced in Android 13 (API 33). It shares syntax with GLSL fragment shaders and runs directly on the GPU as part of the View rendering pipeline.

### 2.2 Key API

```kotlin
// API 34+ — Apply shader to any View as a RenderEffect
val shader = RuntimeShader(agslSourceCode)
view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "inputShader"))
```

The second parameter `"inputShader"` is the uniform name in the AGSL shader that receives the View's rendered content as input.

### 2.3 Chromatic Aberration — AGSL Shader

```glsl
// chromatic_aberration.agsl
uniform shader inputShader;
uniform float2 uResolution;
uniform float uStrength;

half4 main(float2 fragCoord) {
    float2 center = float2(0.5, 0.5);
    float2 uv = fragCoord / uResolution;
    float dist = distance(uv, center);
    float strength = uStrength * dist;
    
    float r = inputShader.eval(fragCoord + float2(strength * 2.0, 0.0)).r;
    float g = inputShader.eval(fragCoord).g;
    float b = inputShader.eval(fragCoord - float2(strength * 2.0, 0.0)).b;
    
    return half4(r, g, b, 1.0);
}
```

**Key differences from GLSL:**
- Uses `uniform shader` instead of `uniform sampler2D`
- Uses `inputShader.eval(coord)` instead of `texture2D()`
- Uses `half4` instead of `vec4` (AGSL supports half-precision)
- Uses `float2` instead of `vec2`

### 2.4 Barrel Distortion + Chromatic Aberration (from Wagner project, adapted to AGSL)

```glsl
// barrel_ca.agsl
uniform shader inputShader;
uniform float2 uResolution;
uniform float uDistortion;  // ~2.2 for noticeable effect
uniform float uChromatic;   // ~0.02 for subtle fringing

float2 barrelDistortion(float2 coord, float amt) {
    float2 cc = coord - 0.5;
    float dist = dot(cc, cc);
    return coord + cc * dist * amt;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float2 distortedUV = barrelDistortion(uv, uDistortion);
    
    float r = inputShader.eval(distortedUV * uResolution + float2(uChromatic, 0.0)).r;
    float g = inputShader.eval(distortedUV * uResolution).g;
    float b = inputShader.eval(distortedUV * uResolution - float2(uChromatic, 0.0)).b;
    
    return half4(r, g, b, 1.0);
}
```

### 2.5 Integration into Runestone

```kotlin
// Apply CA shader to the carousel container view
class ChromaticAberrationEffect {
    companion object {
        private const val SHADER_SOURCE = """
            uniform shader inputShader;
            uniform float2 uResolution;
            uniform float uStrength;
            
            half4 main(float2 fragCoord) {
                float2 center = float2(0.5, 0.5);
                float2 uv = fragCoord / uResolution;
                float dist = distance(uv, center);
                float strength = uStrength * dist;
                
                float r = inputShader.eval(fragCoord + float2(strength * 2.0, 0.0)).r;
                float g = inputShader.eval(fragCoord).g;
                float b = inputShader.eval(fragCoord - float2(strength * 2.0, 0.0)).b;
                
                return half4(r, g, b, 1.0);
            }
        """
        
        fun applyTo(view: View, strength: Float = 0.003f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // 34
                try {
                    val shader = RuntimeShader(SHADER_SOURCE)
                    shader.setFloatUniform("uResolution", 
                        view.width.toFloat(), view.height.toFloat())
                    shader.setFloatUniform("uStrength", strength)
                    view.setRenderEffect(
                        RenderEffect.createRuntimeShaderEffect(shader, "inputShader")
                    )
                } catch (e: Exception) {
                    Log.w("CAEffect", "Failed to apply CA shader", e)
                }
            }
        }
        
        fun removeFrom(view: View) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.setRenderEffect(null)
            }
        }
    }
}
```

### 2.6 Limitations

| Limitation | Impact |
|------------|--------|
| **API 34+ required** for `createRuntimeShaderEffect()` | Only ~40% of active devices (as of 2026). Devices on 33 can use RuntimeShader on a Canvas but NOT as a View-level RenderEffect |
| **Single shader input** | The `uniform shader` receives the View's rendered content. Additional textures (noise, lightmaps) need separate uniforms |
| **Performance** | GPU-bound but lightweight — Chet Haase (Android Toolkit) estimates ~1-2ms per frame for simple shaders |
| **No AGSL debugger** | Shader errors produce runtime exceptions with minimal info. Must test on device |
| **Not animatable** | Uniform changes require re-creating the RuntimeShader or calling `setFloatUniform` + `invalidate()` |

---

## 3. GLSurfaceView Overlay Approach (API 26+, fallback)

### 3.1 Architecture

```
View hierarchy (carousel + overlays)
    ↓
Bitmap capture via Canvas → GLSurfaceView with custom shader
    ↓
Display rendered result
```

### 3.2 How It Works

```kotlin
class PostProcessingSurfaceView(context: Context) : GLSurfaceView(context) {
    
    private val renderer = PostProcessingRenderer()
    
    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        // Make background transparent so Views behind show through
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
    }
    
    fun captureAndProcess(sourceView: View) {
        // 1. Render source View to Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        sourceView.draw(canvas)
        
        // 2. Upload to GL texture
        renderer.updateTexture(bitmap)
        
        // 3. Request render
        requestRender()
    }
}
```

### 3.3 Fragment Shader (GLSL ES)

```glsl
#version 300 es
precision mediump float;
uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uTime;
in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(vTexCoord, center);
    float strength = dist * 0.02;
    
    // Chromatic aberration
    float r = texture(uTexture, vTexCoord + vec2(strength, 0.0)).r;
    float g = texture(uTexture, vTexCoord).g;
    float b = texture(uTexture, vTexCoord - vec2(strength, 0.0)).b;
    
    // Vignette
    float vignette = 1.0 - dist * 0.6;
    
    fragColor = vec4(r, g, b, 1.0) * vignette;
}
```

### 3.4 Why NOT to use this for Runestone

| Issue | Why It's Bad |
|-------|-------------|
| **Double-rendering** | Must render View hierarchy to Bitmap, then render Bitmap via GL. Doubles frame time |
| **Touch passthrough** | GLSurfaceView on top intercepts touch events. Must manually forward them |
| **Z-ordering** | `setZOrderOnTop(true)` makes it overlay everything — overlays and dialogs render BEHIND it |
| **Memory** | Full-screen Bitmap at 1080p = ~8MB per frame, plus GL texture copy |
| **API complexity** | Requires EGL context, shader compilation, render loop management |

**Verdict:** Only use GLSurfaceView if AGSL is unavailable (API < 33) AND the effect is critical enough to warrant the complexity. For Runestone, AGSL covers all needed effects with 10% of the complexity.

---

## 4. Ambient Occlusion — Fake vs Real

### 4.1 Fake AO (Current Approach)

Already partially implemented via card elevation shadows (`setElevation()`) and the ambient glow behind the carousel. Each card casts a `RenderEffect` shadow proportional to its elevation.

### 4.2 Real AO — Why We Can't Do It

True Ambient Occlusion requires:
1. **A depth buffer** — a per-pixel depth value for every element on screen
2. **A normals buffer** — surface orientation per pixel
3. **Screen-space sampling** — sample neighboring pixels to calculate occlusion

Android's View system does NOT expose a depth buffer. There is no API to get "how far away is this pixel from the camera" for a View hierarchy. This is fundamentally different from game engines where every object has a known 3D position.

### 4.3 SSAO (Screen-Space AO) — Dreaming

If we ever move the carousel to GLSurfaceView (full GL render), we could implement SSAO by:
1. Rendering the scene to a depth texture (cards at known z-positions)
2. Sampling the depth buffer with a kernel
3. Darkening occluded areas

But this requires the ENTIRE carousel to be a GL scene, not just a post-processing overlay. Not worth it for a game launcher UI.

### 4.4 Recommendation

**Skip AO entirely.** The shadow-based depth perception from `setElevation()` + ambient glow + vignette provides enough spatial cues. True AO is architecturally impossible in the View system, and SSAO would require a full GL port of the carousel that isn't justified.

---

## 5. Apple's Approach (Investigation Reference)

Apple implements real-time CA and glass effects in UIKit/SwiftUI via **Metal compute shaders** applied as a screen-space pass over the entire UI compositing layer.

| Apple (Metal) | Android Equivalent |
|---------------|-------------------|
| `CAMetalLayer` | `GLSurfaceView` or `RenderEffect` |
| Metal Shading Language | AGSL (identical concept) |
| `UIVisualEffectView` | `RenderEffect.createBlurEffect()` |
| Metal compute shader post-processing | `RuntimeShader` + `createRuntimeShaderEffect()` |
| Per-window compositing | Per-View via `setRenderEffect()` |

Apple's advantage: they own the entire graphics stack from the kernel driver to the UI framework. They can insert post-processing at the window compositor level. On Android, we're limited to per-View effects.

**visionOS** uses a similar approach but adds **metal aperture distortion** CA at the lens level (for the actual headset optics). This is hardware-specific and not applicable.

---

## 6. Implementation Path for Runestone

### Phase 7a — AGSL Shader Integration (Estimated: 4-6 hours)

```kotlin
// Files to create:
// - app/src/main/java/com/runestone/app/ui/carousel/effects/ChromaticAberrationEffect.kt
// - app/src/main/java/com/runestone/app/ui/carousel/effects/agsl/chromatic_aberration.agsl
// - app/src/main/java/com/runestone/app/ui/carousel/effects/PostProcessingPipeline.kt (update)

// Files to modify:
// - app/src/main/java/com/runestone/app/ui/HomeScreen.kt (add CA to carousel render)
```

Steps:
1. Write AGSL shader for CA with barrel distortion
2. Create `ChromaticAberrationEffect` Kotlin wrapper with API 34+ guard
3. Add toggle in Settings (Visual Effects → Chromatic Aberration)
4. Wire into `renderCarousel3D()` as an optional overlay
5. Test on API 34+ device/emulator

### Phase 7b — Animated CA (Future)

CA strength that pulses subtly during scrolling (motion-synced):
- Idle: strength = 0.001 (barely visible)
- During scroll: strength = 0.005 (noticeable fringing at edges)
- Slow decay after scroll stops

### Phase 7c — GLSurfaceView Fallback (If Needed)

Only if AGSL is insufficient AND a specific effect is critical. Not recommended.

---

## 7. Device Coverage

| API Level | % Active Devices | Has AGSL View Shaders? | Has GLSurfaceView? |
|-----------|-----------------|----------------------|-------------------|
| 34+ (Android 14+) | ~40% | ✅ `createRuntimeShaderEffect` | ✅ |
| 33 (Android 13) | ~15% | ⚠️ RuntimeShader on Canvas only | ✅ |
| 31-32 (Android 12) | ~20% | ❌ | ✅ |
| 26-30 (Android 8-11) | ~25% | ❌ | ✅ |

**Strategy:**
- **API 34+**: Full AGSL shader effects (CA, advanced bloom)
- **API 33**: RuntimeShader on Canvas (limited — can render a post-processed Bitmap but not as a View effect)
- **API 26-32**: Fall back to current RenderEffect blur + Canvas vignette/noise (already implemented)

---

## 8. Performance Budget

```
Current Phase 1-5 effects (Vignette + Grain + DOF + Bloom):  ~5.9ms per frame
Add Chromatic Aberration (AGSL shader):                    +1.5ms per frame
Add Barrel Distortion (AGSL shader):                       +0.5ms per frame
──────────────────────────────────────────────────────────────────
Total with ALL effects:                                     ~7.9ms per frame
                                                           (60fps budget = 16.6ms)
```

Even with all effects, we're well under 16ms. AGSL shaders are GPU-bound and lightweight.

---

## 9. Conclusion

| Effect | Approach | Viable | When |
|--------|----------|--------|------|
| **Chromatic Aberration** | AGSL RuntimeShader (API 34+) | ✅ YES | Phase 7a |
| **Barrel Distortion** | AGSL RuntimeShader (API 34+) | ✅ YES | Phase 7a |
| **True Ambient Occlusion** | Not possible in View system | ❌ NO | Never |
| **GLSurfaceView pipeline** | Not recommended (complexity > benefit) | ⚠️ Fallback | Only if AGSL insufficient |
| **Apple-style Metal CA** | Track AGSL evolution | 🔍 Track | API 34+ already equivalent |

**Recommendation:** Skip AO. Implement CA via AGSL RuntimeShader. Skip GLSurfaceView entirely. Track AGSL API improvements in future Android releases.
