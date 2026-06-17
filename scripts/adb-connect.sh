#!/usr/bin/env bash
# adb-connect.sh — bring up a persistent wireless adb session for a USB device.
#
# Usage:
#   ./scripts/adb-connect.sh                 # use the cached IP
#   ./scripts/adb-connect.sh 192.168.1.42    # explicit IP
#   ./scripts/adb-connect.sh --usb           # tear down TCP, go back to USB-only
#   ./scripts/adb-connect.sh --status        # report current state
#
# The script is idempotent. Running it when TCP is already enabled just
# reconnects. It also caches the last successful IP at
# .runestone/.last-adb-ip so subsequent runs don't need to re-derive it.
set -euo pipefail

STATE_DIR=".runestone"
STATE_FILE="$STATE_DIR/.last-adb-ip"
PORT="${ADB_PORT:-5555}"

mkdir -p "$STATE_DIR"

usage() {
    cat <<EOF
Usage: $0 [ip | --usb | --status]

With no args: connect to the last known IP, or auto-discover if no cache.
With an IP:   connect to that IP on port $PORT.
--usb:        tell the device to drop TCP mode (go back to USB-only).
--status:     print the current adb state without changing anything.
EOF
}

list_devices() {
    adb devices | sed -n '/^List of devices/p;$ p' | sed '1d'
}

cached_ip() {
    [[ -f "$STATE_FILE" ]] && cat "$STATE_FILE" || true
}

save_ip() {
    echo "$1" > "$STATE_FILE"
}

# --usb: tell the device to drop TCP listening.
if [[ "${1:-}" == "--usb" ]]; then
    if ! adb devices | grep -qE "device$"; then
        echo "No device connected over USB or TCP." >&2
        exit 1
    fi
    adb usb
    rm -f "$STATE_FILE"
    echo "Switched to USB-only. Reconnect the cable if it isn't already."
    exit 0
fi

# --status: report state.
if [[ "${1:-}" == "--status" ]]; then
    echo "=== adb devices ==="
    adb devices
    echo
    echo "=== TCP listen status (per device) ==="
    adb devices | awk '/device$/{print $1}' | while read -r d; do
        props=$(adb -s "$d" shell getprop service.adb.tcp.port 2>/dev/null | tr -d '\r')
        echo "  $d: service.adb.tcp.port = ${props:-<not set>}"
    done
    if [[ -f "$STATE_FILE" ]]; then
        echo
        echo "=== Cached IP ==="
        echo "  $(cat "$STATE_FILE"):$PORT"
    fi
    exit 0
fi

# Help
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

# Main: ensure a USB device is connected, then enable TCP.
if ! adb devices | awk '$2=="device"{exit 0} {exit 1}'; then
    echo "No adb device is connected. Plug in the USB cable and try again." >&2
    exit 1
fi

# Enable TCP on the device. The USB device shows up as a transport, not an
# IP:port, so we use plain `adb` (no -s) to target the only USB device.
echo "Enabling TCP listening on port $PORT on the USB device..."
adb tcpip "$PORT"

# Find the device's wlan0 IP. This requires the device to be on Wi-Fi.
# We try `wlan0` first, then `wlan1`, then the route's source IP.
device_ip() {
    adb shell ip -4 addr show wlan0 2>/dev/null \
        | awk '/inet /{print $2}' | head -1 | cut -d/ -f1
}

EXPLICIT_IP="${1:-}"
if [[ -n "$EXPLICIT_IP" ]]; then
    TARGET_IP="$EXPLICIT_IP"
else
    TARGET_IP="$(cached_ip)"
    if [[ -z "$TARGET_IP" ]]; then
        echo
        echo "No cached IP. Discovering the device's Wi-Fi address..."
        TARGET_IP="$(device_ip)"
    fi
fi

if [[ -z "$TARGET_IP" ]]; then
    cat <<EOF >&2

Couldn't find the device's Wi-Fi IP automatically. Make sure:
  - The device is connected to Wi-Fi (not just cellular)
  - The device is awake (some devices sleep the network stack)

Then either:
  - Re-run with the IP explicitly: $0 192.168.1.42
  - Or run \`adb shell ip addr show wlan0\` and look for the inet line.
EOF
    exit 2
fi

# Try to connect. If it fails, fall back to a quick re-scan.
echo
echo "Connecting to $TARGET_IP:$PORT ..."
if ! adb connect "$TARGET_IP:$PORT"; then
    echo "Connect failed. Discovered IP may be stale. Re-scanning..."
    TARGET_IP="$(device_ip)"
    if [[ -n "$TARGET_IP" ]]; then
        echo "Retrying with $TARGET_IP:$PORT ..."
        adb connect "$TARGET_IP:$PORT"
    else
        echo "Could not re-discover IP. Plug the cable back in and try again." >&2
        exit 3
    fi
fi

if adb -s "$TARGET_IP:$PORT" shell echo ok >/dev/null 2>&1; then
    save_ip "$TARGET_IP"
    echo
    echo "Connected: $TARGET_IP:$PORT"
    echo "Run: adb -s $TARGET_IP:$PORT logcat -c && adb -s $TARGET_IP:$PORT logcat"
    echo "Or:  DEVICE=$TARGET_IP:$PORT ./scripts/adb-smoke-test.sh home"
else
    echo "Connect reported success but the device is not responding." >&2
    echo "Try: adb kill-server && adb start-server && $0" >&2
    exit 4
fi
