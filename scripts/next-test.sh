#!/usr/bin/env bash
# next-test.sh — install the latest debug APK and stream logcat.
#
# Assumes ./scripts/adb-connect.sh has been run at least once and a
# wireless adb session is up. The script auto-runs adb-connect.sh if
# it isn't, so the first call after a fresh boot also works.
set -euo pipefail

DEVICE="${DEVICE:-}"
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"

# If DEVICE wasn't passed, try the cached IP, then run the connector.
if [[ -z "$DEVICE" && -f .runestone/.last-adb-ip ]]; then
    DEVICE="$(cat .runestone/.last-adb-ip):5555"
fi

if [[ -z "$DEVICE" ]]; then
    echo "No device IP cached. Running adb-connect.sh..."
    ./scripts/adb-connect.sh
    DEVICE="$(cat .runestone/.last-adb-ip):5555"
fi

echo "Using device: $DEVICE"
echo

# Sanity check
if ! adb -s "$DEVICE" shell echo ok >/dev/null 2>&1; then
    echo "Device $DEVICE is not reachable. Trying to reconnect..."
    ./scripts/adb-connect.sh
    DEVICE="$(cat .runestone/.last-adb-ip):5555"
fi

# Build + install
./gradlew :app:assembleDebug
adb -s "$DEVICE" install -r "$APK"

# Clear and stream
adb -s "$DEVICE" logcat -c
echo
echo "=== Streaming Runestone + chromium logs. Press Ctrl+C to stop. ==="
echo
adb -s "$DEVICE" logcat Runestone:V chromium:V '*:S'
