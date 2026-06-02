#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 /path/to/renpy-8.3.4-sdk" >&2
    exit 1
fi

sdk_dir="$(realpath "$1")"
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
staging_dir="$(mktemp -d)"
trap 'rm -rf "$staging_dir"' EXIT
runtime_zip="$repo_dir/app/src/main/assets/renpy-runtime.zip"

mkdir "$staging_dir/lib"
cp -a "$sdk_dir/lib/python3.9" "$staging_dir/lib/python3.9"
cp -a "$sdk_dir/renpy" "$staging_dir/renpy"
cp "$sdk_dir/renpy.py" "$staging_dir/runestone_renpy_launcher.py"
cp "$repo_dir/scripts/renpy-main.py" "$staging_dir/main.py"
rm -f "$runtime_zip"

(
    cd "$staging_dir"
    find . -exec touch -t 200001010000 {} +
    find . -type f -print0 | sort -z | xargs -0 zip -q -X "$runtime_zip"
)
