#!/usr/bin/env bash
# Fetch real CC0 ambience, weather and one-shot sounds from Freesound into
# local-music/ following the library layout (docs/CONTENT.md).
#
#   scripts/fetch-freesound.sh          download everything in the manifest
#   scripts/fetch-freesound.sh --check  resolve every row (API) without downloading
#
# Requires scripts/fetch-keys.local (gitignored) with FREESOUND_TOKEN, or the
# env var of the same name. Token auth can download only the public hq-ogg
# previews (~128 kbit/s) — originals need OAuth2; previews are fine for
# ambience/spots. Every downloaded file is credited in
# local-music/ATTRIBUTION-FREESOUND.md.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT_DIR/local-music"
MANIFEST="$ROOT_DIR/scripts/freesound_manifest.tsv"
ATTRIBUTION="$OUT/ATTRIBUTION-FREESOUND.md"
MODE="fetch"
[ "${1:-}" = "--check" ] && MODE="check"

[ -n "${FREESOUND_TOKEN:-}" ] || . "$ROOT_DIR/scripts/fetch-keys.local"
[ -n "${FREESOUND_TOKEN:-}" ] || { echo "FREESOUND_TOKEN missing — create scripts/fetch-keys.local" >&2; exit 1; }

# Resolve every manifest row to a concrete sound: query the API (CC0 filter),
# keep results inside the duration window, take the requested index.
# Resolutions are cached in local-music/.freesound-resolved.tsv (delete the
# file to re-resolve after manifest edits) and the API is rate-limited
# (60 req/min), so queries are spaced out and retried on 429.
# Emits: relpath \t preview_url \t sound_name \t author \t sound_id
CACHE="$OUT/.freesound-resolved.tsv"
mkdir -p "$OUT"
touch "$CACHE"
resolve_rows() {
python3 - "$MANIFEST" "$FREESOUND_TOKEN" "$CACHE" << 'PYEOF'
import json, subprocess, sys, time, urllib.parse

manifest, token, cache_path = sys.argv[1], sys.argv[2], sys.argv[3]
rows = []
for line in open(manifest, encoding="utf-8"):
    line = line.rstrip("\n")
    if not line or line.startswith("#"):
        continue
    relpath, query, dmin, dmax, pick = line.split("\t")
    rows.append((relpath, query, float(dmin), float(dmax), int(pick)))

cache = {}
for line in open(cache_path, encoding="utf-8"):
    parts = line.rstrip("\n").split("\t")
    if len(parts) == 5:
        cache[parts[0]] = line.rstrip("\n")

def api(url):
    r = subprocess.run(["curl", "-s", "--max-time", "30", url], capture_output=True, text=True)
    try:
        return json.loads(r.stdout)
    except Exception:
        return {}

fails = 0
with open(cache_path, "a", encoding="utf-8") as cache_file:
    for relpath, query, dmin, dmax, pick in rows:
        cached = cache.get(relpath)
        if cached:
            print(cached)
            continue
        time.sleep(1.1)  # stay under the 60 req/min API limit
        q = urllib.parse.quote(query)
        f = urllib.parse.quote('license:"Creative Commons 0"')
        url = (f"https://freesound.org/apiv2/search/text/?query={q}&filter={f}"
               f"&token={token}&fields=id,name,username,license,duration,previews&page_size=30")
        results = []
        for _ in range(3):
            body = api(url)
            if "results" in body:
                results = body["results"]
                break
            time.sleep(15)  # throttled (429) — back off and retry
        fits = [s for s in results if dmin <= (s.get("duration") or 0) <= dmax and s.get("previews")]
        if len(fits) < pick:
            print(f"FAIL no fit ({len(fits)} of {len(results)}) {relpath}: {query}", file=sys.stderr)
            fails += 1; continue
        s = fits[pick - 1]
        line = f"{relpath}\t{s['previews']['preview-hq-ogg']}\t{s['name']}\t{s['username']}\t{s['id']}"
        cache_file.write(line + "\n")
        cache_file.flush()
        print(line)
sys.exit(1 if fails else 0)
PYEOF
}

fail=0
total=0
while IFS=$'\t' read -r relpath url name author sid; do
    [ -n "$relpath" ] || continue
    total=$((total + 1))
    file="$OUT/$relpath"
    if [ "$MODE" = "check" ]; then
        echo "resolved $relpath <- freesound $sid ($name by $author)"
        continue
    fi
    if [ -s "$file" ]; then
        echo "skip (exists) $relpath"
        continue
    fi
    mkdir -p "$(dirname "$file")"
    echo "get $relpath"
    curl -L --fail --retry 3 --max-time 300 -A "Mozilla/5.0 DnDSoundFetcher/1.0" -o "$file.part" "$url"
    mv "$file.part" "$file"
    duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$file" 2>/dev/null)
    awk -v d="${duration:-0}" 'BEGIN { exit !(d >= 0.3) }' || {
        echo "CORRUPT $relpath (duration=${duration:-?})"; rm -f "$file"; fail=$((fail + 1));
    }
done < <(resolve_rows)
[ "$fail" -eq 0 ] || exit 1
if [ "$MODE" = "check" ]; then
    echo "resolved $total rows OK"
    exit 0
fi

# Credits: every CC0 file with its source page (CC0 needs no attribution, but
# a full credit list is good practice and costs nothing).
{
    echo "# Freesound attribution (all CC0 / public domain)"
    echo
    echo "Resolved and downloaded by \`scripts/fetch-freesound.sh\` from \`scripts/freesound_manifest.tsv\`"
    echo "(public hq-ogg previews)."
    echo
    echo "| File | Sound | Author | License | Source |"
    echo "| --- | --- | --- | --- | --- |"
    resolve_rows | sort | while IFS=$'\t' read -r relpath url name author sid; do
        echo "| \`$relpath\` | $name | $author | CC0 1.0 | https://freesound.org/s/$sid/ |"
    done
} > "$ATTRIBUTION"
count=$(find "$OUT" -type f -name '*.ogg' | wc -l)
echo "done: $count ogg files under $OUT, credits in $ATTRIBUTION"
