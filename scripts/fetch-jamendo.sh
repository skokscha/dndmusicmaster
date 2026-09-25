#!/usr/bin/env bash
# Fetch extra Creative Commons music tracks from Jamendo into local-music/
# zones listed in scripts/jamendo_manifest.tsv (relpath_dir, tag, index).
#
#   scripts/fetch-jamendo.sh          download everything in the manifest
#   scripts/jamendo_manifest.tsv      zone dir | tag | result index
#
# Requires scripts/fetch-keys.local (gitignored) with JAMENDO_CLIENT_ID.
# Jamendo licenses are Creative Commons variants recorded verbatim in
# local-music/ATTRIBUTION-JAMENDO.md (the catalog is mostly BY / BY-NC /
# BY-NC-ND — verbatim redistribution with credit, non-commercial use).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT_DIR/local-music"
MANIFEST="$ROOT_DIR/scripts/jamendo_manifest.tsv"
ATTRIBUTION="$OUT/ATTRIBUTION-JAMENDO.md"
MAX_DURATION_S=480

[ -n "${JAMENDO_CLIENT_ID:-}" ] || . "$ROOT_DIR/scripts/fetch-keys.local"
[ -n "${JAMENDO_CLIENT_ID:-}" ] || { echo "JAMENDO_CLIENT_ID missing — create scripts/fetch-keys.local" >&2; exit 1; }

# relpath_dir \t download_url \t title \t artist \t license_name \t track_id
resolve_rows() {
python3 - "$MANIFEST" "$JAMENDO_CLIENT_ID" "$MAX_DURATION_S" << 'PYEOF'
import json, subprocess, sys, time, urllib.parse

manifest, client_id, max_dur = sys.argv[1], sys.argv[2], int(sys.argv[3])
rows = []
for line in open(manifest, encoding="utf-8"):
    line = line.rstrip("\n")
    if not line or line.startswith("#"):
        continue
    relpath, tag, pick = line.split("\t")
    rows.append((relpath, tag, int(pick)))

fails = 0
for relpath, tag, pick in rows:
    url = (f"https://api.jamendo.com/v3.0/tracks/?client_id={client_id}&format=json"
           f"&limit=30&tags={urllib.parse.quote(tag)}&include=licenses&audioformat=mp32")
    results = []
    for _ in range(3):
        r = subprocess.run(["curl", "-s", "--max-time", "30", url], capture_output=True, text=True)
        try:
            results = json.loads(r.stdout).get("results", [])
        except Exception:
            results = []
        if results:
            break
        time.sleep(5)  # the API occasionally answers with an empty list — retry
    fits = [t for t in results
            if 0 < int(t.get("duration") or 0) <= max_dur and t.get("audiodownload")]
    if len(fits) < pick:
        print(f"RESOLVEFAIL\t{relpath}\tno fit ({len(fits)} of {len(results)}) for tag {tag}")
        fails += 1; continue
    t = fits[pick - 1]
    lic = (t.get("license_ccurl") or "").rstrip("/")
    code = lic.split("/licenses/")[-1].upper().replace("-", " ") if "/licenses/" in lic else "SEE LINK"
    print(f"{relpath}\t{t['audiodownload']}\t{t['name']}\t{t['artist_name']}\t{code}\t{t['id']}")
print(f"RESOLVEFAIL_COUNT\t{fails}", file=sys.stderr)
sys.exit(0)
PYEOF
}

slugify() {
    python3 -c "import re,sys; s=sys.argv[1]; print(re.sub(r'__+','_',re.sub(r'[^a-z0-9]+','_',s.lower())).strip('_'))" "$1"
}

fail=0
total=0
declare -a CREDITS=()
while IFS=$'\t' read -r relpath url title artist license tid; do
    [ -n "$relpath" ] || continue
    if [ "$relpath" = "RESOLVEFAIL" ]; then
        echo "resolve failed: $title"
        fail=$((fail + 1))
        continue
    fi
    total=$((total + 1))
    file="$OUT/$relpath/$(slugify "${artist}_-_${title}").mp3"
    if [ -s "$file" ]; then
        echo "skip (exists) $relpath/$(basename "$file")"
    else
        mkdir -p "$OUT/$relpath"
        echo "get $relpath/$(basename "$file")"
        curl -L --fail --retry 3 --max-time 600 -A "Mozilla/5.0 DnDSoundFetcher/1.0" -o "$file.part" "$url"
        mv "$file.part" "$file"
        duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$file" 2>/dev/null)
        awk -v d="${duration:-0}" 'BEGIN { exit !(d >= 60) }' || {
            echo "CORRUPT $file (duration=${duration:-?})"; rm -f "$file"; fail=$((fail + 1)); continue;
        }
    fi
    CREDITS+=("$relpath/$(basename "$file")|$title|$artist|$license|https://www.jamendo.com/track/$tid")
done < <(resolve_rows)
[ "$fail" -eq 0 ] || exit 1

{
    echo "# Jamendo attribution"
    echo
    echo "Downloaded by \`scripts/fetch-jamendo.sh\` from \`scripts/jamendo_manifest.tsv\`"
    echo "(full-quality audiodownload MP3s via the Jamendo v3.0 API)."
    echo
    echo "| File | Track | Artist | License | Source |"
    echo "| --- | --- | --- | --- | --- |"
    for row in "${CREDITS[@]}"; do
        IFS='|' read -r f t a l s <<< "$row"
        echo "| \`$f\` | $t | $a | $l | $s |"
    done
} > "$ATTRIBUTION"
count=$(find "$OUT" -name '*.mp3' | wc -l)
echo "done: $count mp3 files under $OUT (music set + jamendo), credits in $ATTRIBUTION"
