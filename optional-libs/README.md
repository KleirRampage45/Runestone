# Optional Libraries

These native libraries are NOT included in the default APK build.
Enable them in Runestone's Settings > Addons to download and use.

## Godot Engine
- `libgodot_android.so` (142 MB) — Godot 4.6.3 engine runtime
- `libc++_shared_godot.so` (1.4 MB) — Godot's C++ runtime

To re-enable: copy both files to `app/src/main/jniLibs/arm64-v8a/` and rebuild.
To use via download (future): Runestone downloads them on-demand when user enables Godot in Addons.
