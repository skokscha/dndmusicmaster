#!/usr/bin/env bash
# Run all unit tests (the :core module).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew --no-daemon :core:test
