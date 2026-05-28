# Runestone — License Analysis & Bundling Strategy

> Generated: 2026-05-28
> Our license: GPLv2+ ("version 2 of the License, or (at your option) any later version")

---

## What "GPLv2+" Means for Bundling

All Runestone source files state: `either version 2 of the License, or (at your option) any later version`. This means we can operate under GPLv3 terms when needed, which is **crucial** because EasyRPG Player is GPLv3.

**Key compatibility rules:**
- GPLv2+ → can relicense as GPLv3 ✅
- MIT → compatible with both GPLv2 and GPLv3 ✅
- Apache 2.0 → compatible with GPLv3 only (NOT GPLv2) ✅ (we can use GPLv3)
- GPLv3 → compatible with GPLv2+ (via upgrade to GPLv3) ✅

---

## Engine License Audit

| Engine | License | GPLv2+ Compatible? | Can Bundle? | Binary Size | Status |
|--------|---------|-------------------|-------------|-------------|--------|
| **mkxp-z** | GPLv2+ | ✅ Same license | ✅ Already bundled | ~8 MB (.so) | DONE |
| **EasyRPG Player** | GPLv3 | ✅ Via GPLv3 upgrade | ✅ YES | ~5 MB (.so) | Needs build |
| **Ren'Py** | MIT | ✅ MIT→GPL compatible | ✅ YES | ~40 MB (Python+SDL2) | Needs build |
| **Godot Engine** | MIT | ✅ MIT→GPL compatible | ✅ YES | ~20 MB (.so) | Needs build |
| **nw.js** (MV/MZ) | MIT | ✅ Already using via WebView | ✅ Bundled via system WebView | 0 MB | DONE |
| **Ruffle (Flash)** | MIT/Apache2 | ✅ | ✅ YES | ~5 MB | Future |

### Legacy Engines (no open-source runtime)

| Engine | Year | Runtime | Can Bundle? |
|--------|------|---------|-------------|
| **RPG Maker 95** | 1997 | Proprietary (ASCII) | ❌ No runtime exists |
| **RPG Tsukuru Dante 98** | 1992 | Proprietary (ASCII) | ❌ No runtime exists |

---

## Recommended Bundling Roadmap

### Phase 1 — EasyRPG (GPLv3) — NOW
1. Add EasyRPG/Player as git submodule
2. Build .so for arm64-v8a (NDK cross-compile)
3. Copy .so → `app/src/main/jniLibs/arm64-v8a/`
4. Register `org.easyrpg.player.GameActivity` in AndroidManifest.xml
5. Result: RM2000/2003 games launch natively, no download needed

### Phase 2 — Ren'Py (MIT) — Next
1. Extract Ren'Py Android runtime from renpy/renpy-build
2. Bundle Python .so + SDL2 .so + Ren'Py libs
3. Create `RenpyActivity` wrapper
4. Result: Ren'Py VNs launch natively

### Phase 3 — Godot (MIT) — After
1. Build Godot Android template .so for arm64-v8a
2. Bundle godot-lib .so + create `GodotActivity` wrapper
3. Result: Godot games launch natively

### Phase 4 — Ruffle (MIT/Apache2) — Future
1. Bundle Ruffle WebView wrapper
2. Result: Flash .swf games playable

---

## Current Bundled Status

| Engine | Native .so Bundled? | Launch Path |
|--------|-------------------|-------------|
| XP / VX / VX Ace | ✅ Yes (8 .so files) | `com.hatkid.mkxpz.MainActivity` |
| MV / MZ | ✅ System WebView | `WebViewEngine` (bundled) |
| 2000 / 2003 | ❌ Not yet | Falls back to download dialog |
| Ren'Py | ❌ Not yet | Falls back to download dialog |
| Godot | ❌ Not yet | Falls back to download dialog |
| RM95 / Dante98 | ❌ No runtime exists | Informational dialog only |

---

## JoiPlay Comparison — Why We Win on Licensing

JoiPlay is **proprietary / closed source**. They CANNOT bundle:
- EasyRPG (GPLv3 — copyleft requires source release)
- mkxp-z (GPLv2+ — same)
- Ren'Py (MIT — but they'd need to bundle, which bloats their closed APK)

JoiPlay's "plugin APK" architecture is a **workaround** for their closed-source limitation. They can't legally bundle GPL code into their proprietary app, so they make users install separate APKs.

**Runestone can legally bundle ALL of them** because we're GPLv2+. This is our single biggest competitive advantage: **download, import, play** — no separate plugin APKs needed.

---

## Action Items

- [ ] Add EasyRPG Player as git submodule → `native/easyrpg-android/`
- [ ] Build EasyRPG .so for arm64-v8a + copy to jniLibs
- [ ] Register EasyRPG activity in AndroidManifest
- [ ] Add Ren'Py runtime build script
- [ ] Add Godot template build script
- [ ] Update GameActivity launch paths to prefer bundled native
