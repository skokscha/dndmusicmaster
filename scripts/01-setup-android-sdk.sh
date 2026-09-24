#!/usr/bin/env bash
# Download and set up the Android SDK (command-line tools, platform-tools,
# platform + build-tools) without Android Studio. Idempotent.
set -euo pipefail

SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
# Version verified against https://developer.android.com/studio (September 2026).
CMDLINE_TOOLS_VERSION="15859902"
ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

# AGP 9.4 / sibling-verified combo. Note the new SDK package naming:
# platforms now carry an extension suffix ("android-37.0").
PLATFORM="android-37.0"
BUILD_TOOLS="37.0.0"

mkdir -p "$SDK_DIR"

if [[ -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
    echo "cmdline-tools already present at $SDK_DIR/cmdline-tools/latest"
else
    echo "Downloading command-line tools to a temp dir..."
    TMP_ZIP="$(mktemp --suffix=.zip)"
    trap 'rm -f "$TMP_ZIP"' EXIT
    wget -q --show-progress -O "$TMP_ZIP" "$ZIP_URL"

    echo "Unpacking into $SDK_DIR/cmdline-tools ..."
    mkdir -p "$SDK_DIR/cmdline-tools"
    rm -rf "$SDK_DIR/cmdline-tools/.tmp"
    unzip -q "$TMP_ZIP" -d "$SDK_DIR/cmdline-tools/.tmp"
    rm -rf "$SDK_DIR/cmdline-tools/latest"
    mv "$SDK_DIR/cmdline-tools/.tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rmdir "$SDK_DIR/cmdline-tools/.tmp"
fi

export ANDROID_HOME="$SDK_DIR"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

echo "Accepting licenses..."
yes | "$SDKMANAGER" --licenses >/dev/null

echo "Installing platform-tools, $PLATFORM, build-tools $BUILD_TOOLS ..."
yes | "$SDKMANAGER" "platform-tools" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS" >/dev/null

echo "Writing local.properties..."
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
printf 'sdk.dir=%s\n' "$SDK_DIR" > "$PROJECT_ROOT/local.properties"

echo
echo "SDK ready at: $SDK_DIR"
echo
echo "Add this to your shell profile (e.g. ~/.zshrc):"
echo "  export ANDROID_HOME=\"$SDK_DIR\""
echo "  export PATH=\"\$PATH:$SDK_DIR/platform-tools:$SDK_DIR/cmdline-tools/latest/bin\""
