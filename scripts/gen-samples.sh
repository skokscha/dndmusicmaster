#!/usr/bin/env bash
# Generate tiny synthetic audio samples in dev-samples/ following the library
# folder convention (docs/CONTENT.md). Everything is generated with ffmpeg,
# so the samples are license-clean by construction. Total size < 1 MB.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT_DIR/dev-samples"

command -v ffmpeg >/dev/null 2>&1 || {
    echo "ffmpeg is required — run scripts/00-install-deps.sh." >&2
    exit 1
}

tone() { # tone <relpath> <freq> <seconds>
    local file="$OUT/$1"
    mkdir -p "$(dirname "$file")"
    ffmpeg -hide_banner -loglevel error -y \
        -f lavfi -i "sine=frequency=$2:sample_rate=22050" \
        -t "$3" -c:a libvorbis -q:a -1 "$file"
}

noise() { # noise <relpath> <seconds>
    local file="$OUT/$1"
    mkdir -p "$(dirname "$file")"
    ffmpeg -hide_banner -loglevel error -y \
        -f lavfi -i "anoisesrc=color=brown:sample_rate=22050:amplitude=0.3" \
        -t "$2" -c:a libvorbis -q:a -1 "$file"
}

# Music: two short cues per mood (outer ring + calm center placeholder).
for mood in happy epic sad tense creepy mystic magical funny calm; do
    tone "music/$mood/${mood}_01.ogg" 261 2
    tone "music/$mood/${mood}_02.ogg" 330 2
done
tone "music/battle/battle_01.ogg" 196 2

# Ambience: one environment with a base loop, random spots and loop layers.
noise "ambience/forest/base.ogg" 4
noise "ambience/forest/base_night.ogg" 4
tone "ambience/forest/spots/birds_01.ogg" 880 1
tone "ambience/forest/spots/birds_02.ogg" 988 1
noise "ambience/forest/layers/stream.ogg" 3
noise "ambience/forest/layers/campfire.ogg" 3

# Weather layers.
noise "weather/rain_light.ogg" 3
tone "weather/thunder_01.ogg" 110 1

# One-shots.
tone "sounds/creature/goblin_01.ogg" 147 1
tone "sounds/creature/goblin_02.ogg" 165 1
tone "sounds/attack/sword_hit_01.ogg" 523 1
tone "sounds/attack/sword_hit_02.ogg" 587 1

echo "Samples written to $OUT ($(du -sh "$OUT" | cut -f1))"
