#!/usr/bin/env bash
# Install system dependencies for building this project from a clean Linux machine.
# Idempotent: re-running is safe.
set -euo pipefail

if [[ "$(id -u)" -eq 0 ]]; then
    echo "Run this script as a regular user (it calls sudo itself)." >&2
    exit 1
fi

if [[ ! -r /etc/os-release ]]; then
    echo "Cannot detect the distribution (/etc/os-release is missing)." >&2
    exit 1
fi
# shellcheck disable=SC1091
source /etc/os-release
ID="${ID:-unknown}"

echo "Detected distribution: $PRETTY_NAME"

install_arch() {
    # AGP 9.x requires JDK 17+; we prefer the LTS JDK 21.
    local pkgs=(unzip wget git android-tools ffmpeg)
    if pacman -Si jdk21-openjdk >/dev/null 2>&1; then
        pkgs=(jdk21-openjdk "${pkgs[@]}")
    else
        pkgs=(jdk17-openjdk "${pkgs[@]}")
    fi
    echo "Installing: ${pkgs[*]}"
    sudo pacman -Sy --needed --noconfirm "${pkgs[@]}"
}

install_debian() {
    # Debian/Ubuntu: android-tools is called adb in some releases; try both.
    echo "Installing JDK 21 (may fall back to 17) plus build tools"
    sudo apt-get update -qq
    sudo apt-get install -y unzip wget git ffmpeg || true
    if apt-cache show openjdk-21-jdk >/dev/null 2>&1; then
        sudo apt-get install -y openjdk-21-jdk
    else
        sudo apt-get install -y openjdk-17-jdk adb
    fi
}

case "$ID" in
    arch|manjaro|endeavouros|cachyos) install_arch ;;
    debian|ubuntu|linuxmint|pop) install_debian ;;
    *)
        echo "Unsupported distribution '$ID'. Install manually:" >&2
        echo "  JDK 17+ (21 recommended), unzip, wget, git, adb, ffmpeg" >&2
        exit 1
        ;;
esac

echo
echo "Installed versions:"
java -version 2>&1 | head -1
command -v adb >/dev/null && adb --version | head -1 || echo "adb: not found (wireless debugging still needs it later)"
ffmpeg -version 2>/dev/null | head -1 || echo "ffmpeg: not found"
echo
echo "Done. Next step: scripts/01-setup-android-sdk.sh"
