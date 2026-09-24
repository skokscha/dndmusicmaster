#!/usr/bin/env bash
# Run Android lint over the app module.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew --no-daemon :app:lintDebug
