#!/usr/bin/env bash
# Build a debug APK. Idempotent wrapper over ./gradlew.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f local.properties ]]; then
    echo "local.properties is missing — run scripts/01-setup-android-sdk.sh first." >&2
    exit 1
fi

./gradlew --no-daemon assembleDebug
echo
echo "APK: $ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
