# Ren'Py on Android — How a Generic Launcher Would Work

> **Date:** 2026-06-01
> **For:** Runestone project — making Ren'Py games from Windows work on Android without per-game APK ports

---

## 1. How Ren'Py Android Games Work Today

### The Standard Pipeline

When a developer uses Ren'Py's "Build Android" feature, RAPT (Ren'Py Android Packaging Tool) creates a **custom APK** that embeds the game. Here's what happens internally:

```
Ren'Py SDK
  └─ "Build Android" command
      └─ RAPT (Ren'Py Android Packaging Tool)
          ├─ Downloads python-for-android (p4a)
          ├─ Compiles librenpython.so (Python + SDL2 + Ren'Py core)
          ├─ Creates PythonActivity.java (the bootstrap)
          ├─ Packs game files into the APK
          │   ├─ assets/  (game scripts, images, audio)
          │   └─ lib/arm64-v8a/librenpython.so
          └─ Signs and zipaligns the APK
```

### The Resulting APK Structure

```
game.apk
├── AndroidManifest.xml
├── classes.dex                  ← PythonActivity.java compiled
├── assets/
│   ├── renpy/                   ← Ren'Py engine files
│   │   ├── common/              ← Ren'Py's own assets
│   │   └── android-presplash.jpg
│   ├── game/                    ← THE ACTUAL GAME FILES
│   │   ├── script.rpyc
│   │   ├── images/
│   │   ├── audio/
│   │   └── ...
│   └── lib/                     ← Python stdlib
│       └── python3.11/
└── lib/
    └── arm64-v8a/
        ├── librenpython.so      ← Python + SDL2 + Pygame + Ren'Py (55MB)
        ├── libSDL2.so
        ├── libc++_shared.so
        └── libpython3.11.so
```

### What PythonActivity.java Does

The bootstrap Activity (from python-for-android) does **four critical things**:

**Step 1 — Set up Python environment:**
```java
System.loadLibrary("python3.11");       // Load Python interpreter
System.loadLibrary("sdl2");             // Load SDL2
System.loadLibrary("renpython");        // Load Ren'Py + Pygame

// Set PYTHONHOME to APK's asset directory
String apkPath = getApplicationInfo().sourceDir;
System.setProperty("python.home", apkPath + "/assets/lib/python3.11");
System.setProperty("python.path", apkPath + "/assets/lib/python3.11/site-packages");
```

**Step 2 — Extract game assets:**
```java
// Copy assets from APK to internal storage (uncompressable assets)
AssetExtract.extractAssets(getAssets(), dataDir + "/assets");
```

**Step 3 — Set up Ren'Py environment variables:**
```java
// RENPY_RUNTIME_DIR — where Ren'Py engine files are
// RENPY_GAME_DIR — where the game's assets are
// ANDROID_PUBLIC — writeable save directory
// ANDROID_PRIVATE — app's private data dir
System.setProperty("RENPY_RUNTIME_DIR", dataDir + "/assets/renpy");
System.setProperty("RENPY_GAME_DIR", dataDir + "/assets/game");
System.setProperty("ANDROID_PUBLIC", sdcardDir + "/renpy");
System.setProperty("ANDROID_PRIVATE", dataDir);
```

**Step 4 — Start Ren'Py via Python:**
```java
// Tell Python to run: renpy.main.main()
PythonActivity.startThread("renpy.main", "main");
```

### At Runtime, Ren'Py Checks These Variables

```python
# Inside renpy/init.py (simplified):
if renpy.android:
    gamedir = os.environ.get("RENPY_GAME_DIR", "game")
    renpy.config.gamedir = gamedir
    renpy.config.savedir = os.environ.get("ANDROID_PUBLIC") + "/saves"
```

---

## 2. The Core Challenge: Why a Generic Launcher is Hard

### Problem 1: Ren'Py Engine Files Are Bundled Per-Game

In a normal Ren'Py APK, the **Ren'Py engine files** (`renpy/` directory with `common/`, `common/00access.rpyb`, etc.) are packed into the APK's `assets/` directory alongside the game files. These are NOT part of `librenpython.so` — they're Ren'Py's own `.rpyb` script files.

When you run a Ren'Py game from Windows, these files exist in the Ren'Py SDK directory. On Android, they must be extracted from assets at runtime.

**This means:** We need the Ren'Py engine files (about 30-50MB of `.rpyb` and assets) available on the device for ANY game to run. We can either:
- Bundle them in Runestone's APK (bloats it)
- Download them on first use
- Extract them from the user's Ren'Py installation

### Problem 2: Python Standard Library

The Python standard library (3.11) is compiled into `libpython3.11.so` as part of `librenpython.so`. But `librenpython.so` actually contains ALL of:
- Python 3.11 interpreter
- SDL2 library
- Pygame/SDL2 wrapper
- Ren'Py Python modules
- Game-specific compiled scripts

If the game uses any Python modules not included in this monolith, they won't be available.

### Problem 3: File Paths Are Hardcoded

Ren'Py games often use hardcoded paths for:
- `renpy.loadsave.location` — save file directory
- `config.save_directory` — persistent data
- `renpy.config.gamedir` — where game files are
- Image/audio loading from `game/` subdirectory

Running a game from an arbitrary import path (SAF content URI) instead of a fixed extraction directory means we'd need to copy or symlink files.

### Problem 4: APK Assets Are Read-Only

The assets inside a Ren'Py APK are read-only. That's why `PythonActivity` extracts them to internal storage first. For a generic launcher:
- We can't extract from an APK (there's no APK — we imported a folder)
- We'd need to copy the game folder from the import location to internal storage
- That's 500MB-2GB for a typical Ren'Py game

---

## 3. The Current 55MB librenpython.so

The `librenpython.so` (55MB) in Runestone's jniLibs came from a Ren'Py 8.3.4 Android build. Let me break down what's inside:

| Component | Approx Size | Description |
|-----------|-------------|-------------|
| Python 3.11 interpreter | ~8 MB | libpython with bytecode compiler, stdlib C modules |
| SDL2 | ~7 MB | Rendering, input, audio |
| SDL2_image | ~1 MB | Image loading (PNG, JPG) |
| SDL2_mixer | ~1 MB | Audio mixing (OGG, MP3) |
| Freetype | ~1 MB | Font rendering |
| Ren'Py core Python modules | ~15 MB | Compressed .rpyc files |
| Pygame/SDL2 wrapper | ~2 MB | Python SDL2 bindings |
| Other libs (libjpeg, etc.) | ~2 MB | |
| C++ stdlib | ~2 MB | libc++_shared |
| **Total** | **~39 MB** | (rest is debug symbols) |

**What's missing:** The Ren'Py script files (`.rpyb` engine files in `renpy/common/`). These are another ~40-50MB that must be available as **regular files** in the filesystem, not in a .so.

---

## 4. How a Generic Ren'Py Launcher Would Work

Here's the architecture for a launcher that imports a Windows Ren'Py game folder:

```
┌───────────────────────────────────────────────┐
│  Runestone App                                 │
│  ┌─────────────────────────────────────────┐   │
│  │  1. Import: Copy game folder to         │   │
│  │     internal storage (renpy-games/xxx)  │   │
│  ├─────────────────────────────────────────┤   │
│  │  2. Prepare: Download/extract Ren'Py    │   │
│  │     engine files (renpy/common/) to     │   │
│  │     renpy-engine/ directory             │   │
│  ├─────────────────────────────────────────┤   │
│  │  3. Launch: Start PythonActivity with:  │   │
│  │     - RENPY_RUNTIME_DIR → renpy-engine/ │   │
│  │     - RENPY_GAME_DIR → renpy-games/xxx  │   │
│  │     - ANDROID_PUBLIC → saves/xxx        │   │
│  ├─────────────────────────────────────────┤   │
│  │  PythonActivity.java (from p4a)         │   │
│  │  ├─ Loads librenpython.so               │   │
│  │  ├─ Sets Python environment             │   │
│  │  └─ Calls renpy.main.main()             │   │
│  ├─────────────────────────────────────────┤   │
│  │  librenpython.so (55MB, bundled)        │   │
│  └─────────────────────────────────────────┘   │
└───────────────────────────────────────────────┘
```

### Step-by-step:

**Phase 1: Bundle the Ren'Py engine files**

We need the `renpy/common/` directory from a Ren'Py 8.3.4 installation. These are about 40MB of `.rpyb` files. Options:
- Bundle in the APK (adds 40MB to the already 225MB APK = 265MB)
- Download on first Ren'Py game launch (users must have internet)
- Bundle as a separate APK expansion file (OBB)

**Phase 2: Create a Ren'Py-adapted PythonActivity**

Copy python-for-android's `PythonActivity.java` and modify it to accept game path via Intent extras instead of reading from APK assets:

```java
public class RunestonePythonActivity extends SDLActivity {
    protected void onCreate(Bundle savedInstanceState) {
        String gamePath = getIntent().getStringExtra("game_path");
        String enginePath = getIntent().getStringExtra("engine_path");
        
        // Set up environment pointing to our game folder
        System.setProperty("RENPY_RUNTIME_DIR", enginePath + "/renpy");
        System.setProperty("RENPY_GAME_DIR", gamePath);
        System.setProperty("ANDROID_PUBLIC", gamePath + "/saves");
        
        // Load libraries
        System.loadLibrary("renpython");
        
        // Start Ren'Py
        PythonActivity.startThread("renpy.main", "main");
    }
}
```

**Phase 3: Handle the asset extraction**

python-for-android's `PythonActivity` expects to extract game files from APK assets. For a generic launcher, the game files are already on disk, so we skip extraction and point directly to the imported folder.

---

## 5. The Hard Parts

### 5.1 Python sys.path and Import Resolution

When Python starts from `librenpython.so`, it needs to find its stdlib. In a normal Ren'Py APK, stdlib is extracted from `assets/lib/python3.11/`. For a generic launcher, we need to either:

- **Pre-extract stdlib** from the .so before running (very complex — it's inside the .so)
- **Ship stdlib separately** as files on disk (adds ~15MB to the engine download)
- **Build a custom .so** with stdlib embedded at known offsets (requires p4a fork)

### 5.2 The renpy/ Engine Directory

Ren'Py's engine files (`renpy/common/`) contain ALL the UI and rendering logic for Ren'Py games. Without these, the game won't even show a title screen. We MUST have these available as regular files.

**Solution:** Bundle them in Runestone's APK's asset directory and extract on first use. Or provide a download mechanism.

### 5.3 Game File Access

Ren'Py expects to read game files using regular file I/O (`open()`, `os.listdir()`, etc.). Android's scoped storage (API 30+) restricts direct file access.

**Solution:** Copy game files from the import location to Runestone's private data directory on launch. This means:
- Duplicate storage usage (game folder in import location + copy in app data)
- Long import times for large games (1-2GB can take minutes)
- Need cleanup when removing games

### 5.4 Save File Compatibility

Ren'Py saves are Python pickle files. They're forward-compatible within the same Ren'Py version. If our bundled `librenpython.so` is Ren'Py 8.3.4, games built for 6.x or 7.x may have save compatibility issues.

---

## 6. The Practical Approach (Realistic)

Given the complexity, here's the most practical path for Runestone:

### Step 1: Get the Ren'Py Engine Files

Download `renpy-8.3.4-sdk.7z` and extract the `renpy/common/` directory. This is the Ren'Py engine runtime — about 40MB of `.rpyb` files.

If we bundle these in Runestone's assets, users can play Ren'Py games immediately.

### Step 2: Build RunestonePythonActivity

Take python-for-android's `PythonActivity.java`, strip the APK extraction logic, and make it read game path from Intent extras. This is about 200 lines of Java.

Register it in AndroidManifest.xml as `com.runestone.app.engine.renpy.RenpyActivity`.

### Step 3: The Engine Files Go in Assets

Place renpy/common/ files in `app/src/main/assets/renpy-engine/renpy/common/`. On first Ren'Py game launch, extract them to internal storage.

### Step 4: Update RenpyEngine.kt

Instead of showing "Coming Soon", launch the Ren'Py activity with the game folder path.

### The Cost

| Item | Size | Notes |
|------|------|-------|
| librenpython.so (already bundled) | 55 MB | Already paid |
| Ren'Py engine files (renpy/common/) | ~40 MB | Must bundle or download |
| Python stdlib files | ~15 MB | Inside .so or bundle separately |
| **Total APK increase** | **+40 MB** (engine files) | → 265MB APK |

---

## 7. What JoiPlay Does

JoiPlay's Ren'Py Plugin works by:
1. Shipping a pre-built Ren'Py interpreter (the `librenpython.so` equivalent + engine files)
2. When importing a Ren'Py game, it copies the game folder to app data
3. It starts the Ren'Py interpreter with the game path as argument
4. The interpreter loads the game from the specified directory

JoiPlay's plugin is a separate APK (~30MB) that includes both the native library and the Ren'Py engine files. The main app detects it via PackageManager.

**Key insight:** JoiPlay's approach works because it includes the renpy/ engine files in the plugin APK's assets and extracts them at runtime. We'd need to do the same.

---

## 8. Summary & Recommendation

| Component | Status | What's Needed |
|-----------|--------|---------------|
| `librenpython.so` (55MB) | ✅ Bundled | None |
| Python stdlib | ⚠️ Inside .so | May need separate extraction |
| Ren'Py engine files (~40MB) | ❌ Missing | Download or bundle in assets |
| `PythonActivity.java` | ❌ Missing | Port from python-for-android or renpy-build |
| Game file copying | ❌ Not implemented | Add to WorkspaceManager |
| Import flow | ❌ Not implemented | Add Ren'Py detection + import |

**If I were to build this, I'd:**

1. Download `renpy-8.3.4-sdk` and extract `renpy/common/` → bundle in `app/src/main/assets/renpy-engine/`
2. Port `PythonActivity.java` from python-for-android → `RenpyActivity.kt`
3. Wire it to receive game path via Intent
4. Accept the APK going from 225MB → ~265MB
5. That's about 2-3 days of work for a first working version

The hard part is that every Ren'Py game version expects its matching Ren'Py runtime. If our librenpython.so is 8.3.4, games made with 7.x or 6.x may not work. And modified games (DDLC mods, etc.) use custom Ren'Py builds.

**Bottom line:** It's doable in a focused week. The engine files are the bottleneck, not the code.
