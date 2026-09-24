#!/usr/bin/env bash
# Build a release APK. Signing is NOT configured locally: the artifact is
# unsigned unless a keystore is provided via environment variables
# (used by CI: ANDROID_KEYSTORE_FILE / ANDROID_KEYSTORE_PASSWORD /
# ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f local.properties ]]; then
    echo "local.properties is missing — run scripts/01-setup-android-sdk.sh first." >&2
    exit 1
fi

./gradlew --no-daemon assembleRelease
echo
echo "APK: $ROOT_DIR/app/build/outputs/apk/release/"
