#!/usr/bin/env bash
# Fetch real D&D-style music from free sources (OpenGameArt, incompetech) into
# local-music/ following the 25-zone folder convention (docs/CONTENT.md).
#
#   scripts/fetch-music.sh          download everything listed in the manifest
#   scripts/fetch-music.sh --check  only verify that every URL answers with 200
#
# The downloaded audio stays OUT of the repository (local-music/ is ignored);
# the repo carries the manifest, this script and the generated ATTRIBUTION.md.
# Only public-domain (CC0) and permissively attributed (CC-BY 3.0/4.0) tracks
# are listed — licenses are recorded per track in the manifest.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT_DIR/local-music"
MANIFEST="$ROOT_DIR/scripts/music_manifest.tsv"
ATTRIBUTION="$OUT/ATTRIBUTION.md"
UA="Mozilla/5.0 (X11; Linux x86_64) DnDSoundFetcher/1.0"
MODE="fetch"
[ "${1:-}" = "--check" ] && MODE="check"

rows() {
    grep -v '^#' "$MANIFEST" | awk -F'\t' 'NF>=6 {print}'
}

fail=0
total=0
while IFS=$'\t' read -r path title author license page url; do
    total=$((total + 1))
    file="$OUT/$path"
    if [ "$MODE" = "check" ]; then
        code=$(curl -s -o /dev/null -w "%{http_code}" -I --max-time 25 -A "$UA" "$url")
        if [ "$code" != "200" ] && [ "$code" != "302" ]; then
            echo "FAIL $code $path"
            fail=$((fail + 1))
        fi
        continue
    fi
    if [ -s "$file" ]; then
        echo "skip (exists) $path"
        continue
    fi
    mkdir -p "$(dirname "$file")"
    echo "get $path"
    curl -L --fail --retry 3 --max-time 600 -A "$UA" -o "$file.part" "$url"
    mv "$file.part" "$file"
    duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$file" 2>/dev/null | cut -d. -f1)
    [ -n "${duration:-}" ] && [ "$duration" -gt 3 ] || {
        echo "CORRUPT (duration=${duration:-0}s) $path"
        rm -f "$file"
        fail=$((fail + 1))
    }
done < <(rows)

if [ "$MODE" = "check" ]; then
    echo "checked $total urls, $fail failures"
    [ "$fail" -eq 0 ]
    exit 0
fi

# ATTRIBUTION.md: full per-track credits for everything under local-music/.
{
    echo "# Music attribution"
    echo
    echo "Tracks downloaded by \`scripts/fetch-music.sh\` from \`scripts/music_manifest.tsv\`."
    echo "CC0 tracks are public domain; CC-BY tracks require the credit printed below."
    echo
    echo "| File | Track | Author | License | Source |"
    echo "| --- | --- | --- | --- | --- |"
    rows | sort | while IFS=$'\t' read -r path title author license page url; do
        echo "| \`$path\` | $title | $author | $license | $page |"
    done
    echo
    echo "> All Kevin MacLeod tracks: Music by Kevin MacLeod (incompetech.com),"
    echo "> Licensed under Creative Commons: By Attribution 4.0 License,"
    echo "> http://creativecommons.org/licenses/by/4.0/"
} > "$ATTRIBUTION"

count=$(find "$OUT" -type f \( -name '*.mp3' -o -name '*.ogg' \) | wc -l)
echo "done: $count audio files in $OUT ($(du -sh "$OUT" | cut -f1)), credits in $ATTRIBUTION"
[ "$fail" -eq 0 ]
