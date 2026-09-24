#!/usr/bin/env bash
# Build and install the debug APK on a connected device.
#
# WSL2 does not usually forward USB, so the primary path is wireless
# debugging: enable it on the device, then
#   adb pair <ip:port>          (code from the developer options screen)
#   adb connect <ip:port>
# After that this script works normally.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found — run scripts/00-install-deps.sh (android-tools package)." >&2
    exit 1
fi

DEVICES="$(adb devices | grep -v List | grep -cw device || true)"
if [[ "$DEVICES" -eq 0 ]]; then
    echo "No adb device connected." >&2
    echo "Wireless debugging (WSL2): enable it in developer options, then:" >&2
    echo "  adb pair <ip:port>     # pairing code from the device screen" >&2
    echo "  adb connect <ip:port>" >&2
    echo "Fallback: copy app/build/outputs/apk/debug/app-debug.apk to /mnt/c/Temp" >&2
    echo "and install it on the device by hand." >&2
    exit 1
fi

./gradlew --no-daemon :app:installDebug
