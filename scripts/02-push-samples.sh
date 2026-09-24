#!/usr/bin/env bash
# Pushes the generated dev-samples library to the device so the onboarding
# flow (stage 3) can be tested: pick /sdcard/Download/dndsound-lib in the app.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found — run scripts/00-install-deps.sh first." >&2
    exit 1
fi
if ! adb get-state >/dev/null 2>&1; then
    echo "No device connected. For wireless ADB (WSL2) see scripts/install-adb.sh." >&2
    exit 1
fi

SAMPLES=dev-samples
if [ ! -d "$SAMPLES/music" ]; then
    echo "dev-samples missing — generating with scripts/gen-samples.sh (needs ffmpeg)..." >&2
    ./scripts/gen-samples.sh
fi

echo "Pushing $SAMPLES -> /sdcard/Download/dndsound-lib ..."
adb shell mkdir -p /sdcard/Download/dndsound-lib
adb push "$SAMPLES/." /sdcard/Download/dndsound-lib/
echo "Done. In the app: pick /sdcard/Download/dndsound-lib as the library folder."
