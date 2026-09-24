#!/usr/bin/env bash
# Generate tiny synthetic audio samples in dev-samples/ following the library
# folder convention (docs/CONTENT.md, 25 wheel zones). Everything is generated
# with ffmpeg, so the samples are license-clean by construction.
# Zones left EMPTY on purpose (to exercise the fallback chip):
#   creepy/eerie, magical/slight-magic, transitions/festive.
# happy/vivid-town gets 3+ tracks (no-repeat check). Total size < 1 MB.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT_DIR/dev-samples"

command -v ffmpeg >/dev/null 2>&1 || {
    echo "ffmpeg is required — run scripts/00-install-deps.sh." >&2
    exit 1
}

rm -rf "$OUT"

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

# Music: exploration zones (empty: creepy/eerie, magical/slight-magic,
# transitions/festive; happy/vivid-town has three tracks).
tone "music/neutral/hum_01.ogg" 261 2
tone "music/happy/calm/stroll_01.ogg" 294 2
tone "music/happy/vivid-town/market_01.ogg" 330 2
tone "music/happy/vivid-town/market_02.ogg" 349 2
tone "music/happy/vivid-town/market_03.ogg" 392 2
tone "music/epic/soaring/dawn_01.ogg" 220 2
tone "music/epic/epic/warhorn_01.ogg" 196 2
tone "music/epic/epic/warhorn_02.ogg" 208 2
tone "music/sad/defeat/ashes_01.ogg" 165 2
tone "music/sad/tragic/lament_01.ogg" 147 2
tone "music/tense/thrilling/pursuit_01.ogg" 185 2
tone "music/tense/tense/standoff_01.ogg" 175 2
tone "music/creepy/creepy/dungeon_01.ogg" 98 2
tone "music/mystic/spheric/nebula_01.ogg" 247 2
tone "music/mystic/mystic/oracle_01.ogg" 233 2
tone "music/magical/magical/enchant_01.ogg" 440 2
tone "music/funny/noble/court_01.ogg" 415 2
tone "music/funny/funny/jig_01.ogg" 494 2
tone "music/transitions/victory/fanfare_01.ogg" 523 2
tone "music/transitions/tragic-fight/duel_01.ogg" 139 2
tone "music/transitions/moody/twilight_01.ogg" 156 2
tone "music/transitions/dread/approach_01.ogg" 87 2
tone "music/transitions/haunted/seance_01.ogg" 111 2
tone "music/transitions/enchanted/glade_01.ogg" 466 2
tone "music/transitions/whimsical/spirals_01.ogg" 554 2

# Battle subtree: a few zones, same convention inside battle/.
tone "music/battle/neutral/clash_01.ogg" 131 2
tone "music/battle/epic/epic/charge_01.ogg" 196 2
tone "music/battle/creepy/creepy/ambush_01.ogg" 93 2

# Ambience: one environment with a base loop, random spots and loop layers.
noise "ambience/forest/base.ogg" 4
noise "ambience/forest/base_night.ogg" 4
tone "ambience/forest/spots/birds_01.ogg" 880 1
tone "ambience/forest/spots/birds_02.ogg" 988 1
noise "ambience/forest/layers/stream.ogg" 3
noise "ambience/forest/layers/campfire.ogg" 3

# Weather layers.
noise "weather/rain/drizzle.ogg" 3
noise "weather/storm/thunder_01.ogg" 3
noise "weather/wind/gust.ogg" 3

# One-shots.
tone "sounds/creature/goblin_01.ogg" 147 1
tone "sounds/creature/goblin_02.ogg" 165 1
tone "sounds/attack/sword_hit_01.ogg" 523 1
tone "sounds/attack/sword_hit_02.ogg" 587 1

echo "Samples written to $OUT ($(du -sh "$OUT" | cut -f1))"
