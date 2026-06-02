# Ren'Py Engine Files

This directory should contain the Ren'Py engine runtime files from the Ren'Py SDK.

## What Goes Here

The `renpy/common/` directory contains Ren'Py's core script files (`.rpyb` files) that provide:
- UI rendering logic
- Save/load system
- Text display
- Image handling
- Audio playback
- And all other Ren'Py engine features

## How to Get These Files

1. Download Ren'Py SDK from https://www.renpy.org/latest.html
2. Extract the SDK
3. Copy the `renpy/common/` directory to this location
4. The directory should be approximately 40-50MB

## Directory Structure

```
renpy-engine/
└── renpy/
    └── common/
        ├── 00access.rpyb
        ├── 00action.rpyb
        ├── 00gallery.rpyb
        ├── 00gamepad.rpyb
        ├── 00gui.rpyb
        ├── 00keymap.rpyb
        ├── 00library.rpyb
        ├── 00layout.rpyb
        ├── 00menu.rpyb
        ├── 00nvlmode.rpyb
        ├── 00preferences.rpyb
        ├── 00screen.rpyb
        ├── 00start.rpyb
        ├── 00style.rpyb
        ├── 00stylepreferences.rpyb
        ├── 00system.rpyb
        ├── 00transition.rpyb
        ├── 00updater.rpyb
        ├── 00voice.rpyb
        └── ... (many more files)
```

## Note

Without these files, Ren'Py games will fail to start with errors like:
- "Could not find renpy/common/"
- Missing UI elements
- No text display
- No save/load functionality

These files are required for Ren'Py to function and are not part of librenpython.so.
Runestone's active Ren'Py runtime is packaged as assets/renpy-runtime.zip by
scripts/package-renpy-runtime.sh from the official Ren'Py 8.3.4 SDK.

This directory is retained as the unpacked engine source used by the previous
prototype. It can be removed after the packaged runtime has been validated
against the supported game set.
