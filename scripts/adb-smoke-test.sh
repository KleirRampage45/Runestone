#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-192.168.100.28:5555}"
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"
COMMAND="${1:-home}"
PACKAGE="com.runestone.app"
ACTIVITY="com.runestone.app.MainActivity"

case "$COMMAND" in
  home|manage|settings|store|first_game) ;;
  *)
    echo "Usage: $0 [home|manage|settings|store|first_game]" >&2
    exit 2
    ;;
esac

./gradlew :app:assembleDebug

adb -s "$DEVICE" wait-for-device
adb -s "$DEVICE" install -r "$APK"
adb -s "$DEVICE" logcat -c
adb -s "$DEVICE" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
adb -s "$DEVICE" shell am start \
  -n "$PACKAGE/$ACTIVITY" \
  --es runestone_adb_command "$COMMAND" >/dev/null

sleep 4

echo "Focused activity:"
adb -s "$DEVICE" shell dumpsys window \
  | awk '/mCurrentFocus|mFocusedApp|mTopIsFullscreen/ { print }'

echo
echo "Recent Runestone log lines:"
adb -s "$DEVICE" shell logcat -d -t 80 \
  | grep -E "Runestone|mkxp-z|EasyRPG|AndroidRuntime" || true

echo
echo "Crash buffer:"
adb -s "$DEVICE" shell logcat -b crash -d -t 80
