# Sparse Patch Workspace

Runestone uses a space-efficient in-place install model.

## Installed game files

Imported games live under:

```text
files/games/{storageName}/original/
```

The directory name `original/` is kept for compatibility with existing installs,
but it is the installed playable game directory. Engines launch games from this
folder directly.

Runestone does not create a full duplicate active copy for normal gameplay.

## Patch model

Patches, mods, and translations are applied in place to the installed game
directory. Reversibility comes from sparse backups:

```text
files/games/{storageName}/patches/
  backups/{patchId}/      # original versions of overwritten files only
  manifests/{patchId}.json
  zips/{patchId}.zip
```

When a patch overwrites a file, Runestone backs up that file before replacing it.
When a patch adds a new file, Runestone records the added path so revert can
delete it later.

This avoids wasting storage on full game copies while still letting users revert
patches in a predictable order.

## Legacy active workspace

Older code and installs may still contain:

```text
files/games/{storageName}/active/
```

This folder is legacy. It is not used for launch and should not be treated as
the authoritative playable workspace.
