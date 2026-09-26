#!/usr/bin/env bash
# Fetch Creative Commons music tracks from Jamendo into local-music/ zones
# listed in scripts/jamendo_manifest.tsv (zone dir | tag | result index).
#
#   scripts/fetch-jamendo.sh          download everything in the manifest
#   scripts/jamendo_manifest.tsv      zone dir | tag | result index
#
# Requires scripts/fetch-keys.local (gitignored) with JAMENDO_CLIENT_ID.
# Jamendo licenses are Creative Commons variants recorded verbatim in
# local-music/ATTRIBUTION-JAMENDO.md (the catalog is mostly BY / BY-NC /
# BY-NC-ND — verbatim redistribution with credit, non-commercial use).
#
# Two caches keep re-runs stable:
#   local-music/.jamendo-downloaded.tsv   track id -> file (idempotent, credits)
#   local-music/.jamendo-rows.tsv         manifest row -> track id
# The Jamendo search shuffles and intermittently answers with empty lists, so
# a resolved row is pinned by track id and re-fetched via ?id= on later runs.
# Rows that keep failing are reported and skipped; attribution is rebuilt
# from the downloaded history either way.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT_DIR/local-music"
MANIFEST="$ROOT_DIR/scripts/jamendo_manifest.tsv"
ATTRIBUTION="$OUT/ATTRIBUTION-JAMENDO.md"
CACHE="$OUT/.jamendo-downloaded.tsv"
ROW_CACHE="$OUT/.jamendo-rows.tsv"
MAX_DURATION_S=480
MIN_DURATION_S=60

[ -n "${JAMENDO_CLIENT_ID:-}" ] || . "$ROOT_DIR/scripts/fetch-keys.local"
[ -n "${JAMENDO_CLIENT_ID:-}" ] || { echo "JAMENDO_CLIENT_ID missing — create scripts/fetch-keys.local" >&2; exit 1; }
mkdir -p "$OUT"
touch "$CACHE" "$ROW_CACHE"

# relpath_dir \t download_url \t title \t artist \t license_name \t track_id
# plus ROWCACHE lines consumed by the shell loop below.
resolve_rows() {
python3 - "$MANIFEST" "$JAMENDO_CLIENT_ID" "$ROW_CACHE" << 'PYEOF'
import json, subprocess, sys, time, urllib.parse

manifest, client_id, row_cache_path = sys.argv[1], sys.argv[2], sys.argv[3]
rows = []
for line in open(manifest, encoding="utf-8"):
    line = line.rstrip("\n")
    if not line or line.startswith("#"):
        continue
    relpath, tag, pick = line.split("\t")
    rows.append((relpath, tag, int(pick)))

pinned = {}
for line in open(row_cache_path, encoding="utf-8"):
    parts = line.rstrip("\n").split("\t")
    if len(parts) == 4:
        pinned[(parts[0], parts[1], int(parts[2]))] = parts[3]

def api(params):
    url = (f"https://api.jamendo.com/v3.0/tracks/?client_id={client_id}&format=json"
           f"&include=licenses&audioformat=mp32&{params}")
    for _ in range(3):
        r = subprocess.run(["curl", "-s", "--max-time", "30", url], capture_output=True, text=True)
        try:
            body = json.loads(r.stdout)
        except Exception:
            body = {}
        results = body.get("results") or []
        if results:
            return results
        time.sleep(5)  # the search intermittently answers empty — retry
    return []

fails = 0
row_cache = open(row_cache_path, "a", encoding="utf-8")
for relpath, tag, pick in rows:
    key = (relpath, tag, pick)
    if key in pinned:
        results = api(f"id={pinned[key]}")
        tracks = results
    else:
        results = api(f"limit=30&tags={urllib.parse.quote(tag)}")
        fits = [t for t in results
                if 60 <= int(t.get("duration") or 0) <= 480 and t.get("audiodownload")]
        if len(fits) < pick:
            print(f"RESOLVEFAIL\t{relpath}\tno fit #{pick} ({len(fits)} of {len(results)}) for tag {tag}")
            fails += 1; continue
        tracks = [fits[pick - 1]]
    if not tracks:
        print(f"RESOLVEFAIL\t{relpath}\tpinned track {pinned[key]} vanished")
        fails += 1; continue
    t = tracks[0]
    if key not in pinned:
        row_cache.write(f"{relpath}\t{tag}\t{pick}\t{t['id']}\n")
        row_cache.flush()
    lic = (t.get("license_ccurl") or "").rstrip("/")
    code = lic.split("/licenses/")[-1].upper().replace("-", " ") if "/licenses/" in lic else "SEE LINK"
    print(f"{relpath}\t{t['audiodownload']}\t{t['name']}\t{t['artist_name']}\t{code}\t{t['id']}")
row_cache.close()
if fails:
    print(f"INCOMPLETE: {fails} manifest rows could not be resolved (flaky Jamendo search) — "
          f"re-run later, the rest is already pinned.", file=sys.stderr)
PYEOF
}

slugify() {
    python3 -c "import re,sys; s=sys.argv[1]; print(re.sub(r'__+','_',re.sub(r'[^a-z0-9]+','_',s.lower())).strip('_'))" "$1"
}

fail=0
declare -A CACHED_IDS=()
while IFS=$'\t' read -r id _; do
    [ -n "$id" ] && CACHED_IDS["$id"]=1
done < "$CACHE"

while IFS=$'\t' read -r relpath url title artist license tid; do
    [ -n "$relpath" ] || continue
    if [ "$relpath" = "RESOLVEFAIL" ]; then
        echo "resolve failed: $title"
        fail=$((fail + 1))
        continue
    fi
    if [ -n "${CACHED_IDS[$tid]:-}" ]; then
        echo "skip (cached id $tid)"
        continue
    fi
    file="$OUT/$relpath/$(slugify "${artist}_-_${title}").mp3"
    if [ -s "$file" ]; then
        # Same slug, different track: keep both, suffix the newcomer.
        n=2
        while [ -s "$file" ]; do
            file="$OUT/$relpath/$(slugify "${artist}_-_${title}")_$(printf %02d "$n").mp3"
            n=$((n + 1))
        done
    fi
    mkdir -p "$OUT/$relpath"
    echo "get $relpath/$(basename "$file")"
    curl -L --fail --retry 3 --max-time 600 -A "Mozilla/5.0 DnDSoundFetcher/1.0" -o "$file.part" "$url"
    mv "$file.part" "$file"
    duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$file" 2>/dev/null)
    awk -v d="${duration:-0}" -v m="$MIN_DURATION_S" 'BEGIN { exit !(d >= m) }' || {
        echo "CORRUPT $file (duration=${duration:-?})"; rm -f "$file"; fail=$((fail + 1)); continue;
    }
    printf '%s\t%s\t%s\t%s\t%s\n' "$tid" "${file#"$OUT"/}" "$title" "$artist" "$license" >> "$CACHE"
    CACHED_IDS["$tid"]=1
done < <(resolve_rows)

# Attribution from the full download history (stable across re-runs), even
# when some manifest rows failed to resolve this time.
{
    echo "# Jamendo attribution"
    echo
    echo "Downloaded by \`scripts/fetch-jamendo.sh\` from \`scripts/jamendo_manifest.tsv\`"
    echo "(full-quality audiodownload MP3s via the Jamendo v3.0 API)."
    echo
    echo "| File | Track | Artist | License | Source |"
    echo "| --- | --- | --- | --- | --- |"
    sort -t $'\t' -k2,2 "$CACHE" | while IFS=$'\t' read -r id f t a l; do
        echo "| \`$f\` | $t | $a | $l | https://www.jamendo.com/track/$id |"
    done
} > "$ATTRIBUTION"
count=$(find "$OUT" -name '*.mp3' | wc -l)
echo "done: $count mp3 files under $OUT (music set + jamendo), credits in $ATTRIBUTION"
[ "$fail" -eq 0 ] || exit 1
