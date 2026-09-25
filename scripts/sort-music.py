#!/usr/bin/env python3
"""Sort a flat pile of music files into the 25-zone wheel tree.

Guesses each track's zone from its file name (English or Russian keywords),
prints the proposed mapping, and with --apply copies (or moves) the files
into the library layout from docs/CONTENT.md:

    music/neutral/            music/<sector>/<tier>/        music/transitions/<name>/
    music/battle/...          (with --battle)

Zone folders come from core/src/main/resources/wheel_zones.json (the same
source of truth as the app), so the sorter never drifts from the wheel.

Usage:
  scripts/sort-music.py ~/Downloads/music                  # dry run (default)
  scripts/sort-music.py ~/Downloads/music --apply          # copy into local-music/music
  scripts/sort-music.py ~/Downloads/music --apply --move   # move instead of copy
  scripts/sort-music.py ~/Downloads/music --apply --battle # sort into battle/ tree
  scripts/sort-music.py ... --map "last stand=transition.tragic_fight"  # overrides
  scripts/sort-music.py --list-zones                       # show zone ids and folders

Files whose names match nothing are listed as UNSORTED — rename them or add a
--map override. Existing files in the target are never overwritten: a "_02"
suffix is appended instead.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PALETTE = ROOT / "core/src/main/resources/wheel_zones.json"

AUDIO_EXTENSIONS = {".mp3", ".ogg", ".opus", ".flac", ".wav", ".m4a"}

# zone id (wheel_zones.json) -> keyword stems. A stem matches when it appears
# in the normalized file name; longer stems score higher (more specific).
KEYWORDS: dict[str, tuple[str, ...]] = {
    "neutral": ("нейтрал", "neutral", "путешеств", "wander", "дорог", "дорож", "путь", "path",
                "тракт", "camping", "лагерь", "привал"),
    "happy.calm": ("спокойн", "calm", "мирн", "peaceful", "деревн", "village", "gentle",
                   "мягк", "утренн", "morning", "лёгк", "легк"),
    "happy.vivid_town": ("город", "town", "рынок", "market", "ярмарк", "fair", "порт", "port",
                         "торгов", "trade", "caravan", "караван", "улиц", "street"),
    "epic.soaring": ("soaring", "heroic", "геро", "рассвет", "dawn", "восход", "небо", "sky",
                     "полёт", "полет", "flight", "возвыш", "крыл", "wing"),
    "epic.epic": ("эпик", "epic", "битв", "battle", "войн", "war", "сражен", "charge",
                  "атак", "legion", "легион", "армия", "army", "boss"),
    "sad.defeat": ("defeat", "поражен", "утрат", "ashes", "пепел", "упадок", "разрух"),
    "sad.tragic": ("трагед", "tragic", "плач", "lament", "скорб", "mourning", "funeral",
                   "похорон", "реквием", "requiem", "груст", "sad", "печал", "слез", "tears"),
    "tense.thrilling": ("погоня", "chase", "pursuit", "stealth", "стелс", "sneak",
                        "проникновен", "thrill", "infiltrat", "тайн"),
    "tense.tense": ("tense", "suspense", "напряж", "угроз", "threat", "standoff",
                    "противост", "конфликт", "выжида"),
    "creepy.eerie": ("eerie", "странн", "strange", "жуткова", "шепот", "whisper",
                     "зловещ", "шорох"),
    "creepy.creepy": ("creepy", "horror", "хоррор", "ужас", "страх", "жутк", "dungeon",
                      "подземель", "склеп", "catacomb", "катакомб", "могил", "grave",
                      "некроман", "тюрьм", "prison", "crypt"),
    "mystic.spheric": ("spheric", "space", "космос", "drone", "дрон", "сфера",
                       "медитат", "meditation", "глубин", "эфиры"),
    "mystic.mystic": ("mystic", "мистик", "тайна", "mystery", "оракул", "oracle",
                      "пророч", "prophecy", "древн", "ancient", "рун", "rune", "артефакт"),
    "magical.slight_magic": ("лёгкая магия", "легкая магия", "slight", "искорк", "sparkle",
                             "glimmer", "весенняя"),
    "magical.magical": ("магия", "magic", "magical", "волшеб", "чароде", "заклинан",
                        "spell", "enchant", "фе", "fairy", "манг", "arcane"),
    "funny.noble": ("noble", "благород", "двор", "court", "корол", "king", "царск",
                    "royal", "барокк", "baroque", "менуэт", "minuet", "галант", "ball",
                    "бал "),
    "funny.funny": ("funny", "весел", "jig", "gigue", "шут", "jester", "смешн",
                    "комед", "comedy", "пляс", "таверн", "tavern", "inn", "кабак",
                    "кабацк", "кувырк"),
    "transition.victory": ("victory", "побед", "fanfare", "фанфар", "триумф", "triumph",
                           "гимн", "anthem"),
    "transition.tragic_fight": ("дуэль", "duel", "last stand", "последн бой",
                                "tragic fight", "отчаян бой", "на ножах"),
    "transition.moody": ("moody", "сумрач", "меланхол", "melanchol", "тоска",
                         "закат", "sunset", "twilight", "сумерк", "passing"),
    "transition.dread": ("dread", "надвиг", "approach", "doom", "рок", "апокалипт",
                         "бездн", "abyss", "возмезд"),
    "transition.haunted": ("haunted", "привиден", "призрак", "ghost", "spirit",
                           "спирит", "сеанс", "проклят", "cursed", "полтерг"),
    "transition.enchanted": ("enchanted", "зачарован", "glade", "поляна", "эльф",
                             "elf", "эльфи", "грез"),
    "transition.whimsical": ("whimsical", "причудл", "спираль", "spiral", "круж",
                             "vacillat", "странн танец"),
    "transition.festive": ("праздн", "festive", "фест", "карнавал", "carnival",
                           "пир ", "feast", "пирушк", "гулян"),
}


def load_zone_folders() -> dict[str, str]:
    """zone id -> folder path relative to music/ (exploration tree)."""
    data = json.loads(PALETTE.read_text(encoding="utf-8"))
    folders = {"neutral": data["neutral"]["folder"]}
    for sector in data["sectors"]:
        mood = sector["mood"].lower()
        folders[f"{mood}.{sector['inner']['id'].split('.')[-1]}"] = f"{mood}/{sector['inner']['folder']}"
        folders[f"{mood}.{sector['outer']['id'].split('.')[-1]}"] = f"{mood}/{sector['outer']['folder']}"
    for t in data["transitions"]:
        folders[t["id"]] = f"transitions/{t['folder']}"
    return folders


def normalize(name: str) -> str:
    name = name.lower().replace("ё", "е")
    return re.sub(r"[-_]+", " ", name)


def guess_zone(file_name: str) -> tuple[str | None, str]:
    """Return (zone id, matched keyword) for a file name."""
    text = " " + normalize(Path(file_name).stem) + " "
    best_id, best_stem, best_score = None, "", 0
    for zone_id, stems in KEYWORDS.items():
        for stem in stems:
            if stem in text and len(stem) > best_score:
                best_id, best_stem, best_score = zone_id, stem, len(stem)
    return best_id, best_stem


def unique_destination(target: Path) -> Path:
    if not target.exists():
        return target
    for i in range(2, 100):
        candidate = target.with_name(f"{target.stem}_{i:02d}{target.suffix}")
        if not candidate.exists():
            return candidate
    raise SystemExit(f"too many duplicates for {target}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", nargs="?", help="folder (or single file) with music files")
    parser.add_argument("--apply", action="store_true", help="actually copy/move files")
    parser.add_argument("--move", action="store_true", help="move instead of copy (with --apply)")
    parser.add_argument("--dest", default=str(ROOT / "local-music/music"),
                        help="library root holding music/ (default: local-music/music)")
    parser.add_argument("--battle", action="store_true",
                        help="sort into the battle/ subtree (music/battle/<zone>)")
    parser.add_argument("--map", action="append", default=[], metavar="NAME=ZONE",
                        help="force a file (name substring) into a zone id, e.g. \"Плач=sad.tragic\"")
    parser.add_argument("--list-zones", action="store_true", help="print zone ids and folders")
    args = parser.parse_args()

    folders = load_zone_folders()

    if args.list_zones:
        for zone_id in KEYWORDS:
            print(f"{zone_id:26} music/{folders[zone_id]}")
        return 0
    if not args.source:
        parser.print_help()
        return 1

    overrides: list[tuple[str, str]] = []
    for pair in args.map:
        name, _, zone = pair.partition("=")
        zone = zone.strip()
        if zone not in folders:
            raise SystemExit(f"unknown zone id '{zone}' (see --list-zones)")
        overrides.append((name.strip().lower(), zone))

    source = Path(args.source).expanduser()
    files = sorted([source] if source.is_file() else
                   [p for p in source.iterdir() if p.suffix.lower() in AUDIO_EXTENSIONS])
    if not files:
        raise SystemExit(f"no audio files in {source}")

    plan: list[tuple[Path, Path]] = []
    unsorted: list[Path] = []
    for path in files:
        zone_id = None
        forced = next((z for needle, z in overrides if needle in path.name.lower()), None)
        if forced:
            zone_id = forced
        else:
            zone_id, matched = guess_zone(path.name)
        if zone_id is None:
            unsorted.append(path)
            continue
        prefix = "battle/" if args.battle else ""
        target_dir = Path(args.dest) / prefix / folders[zone_id]
        plan.append((path, target_dir / path.name))

    width = max((len(p.name) for p, _ in plan), default=0)
    for src, dst in plan:
        print(f"{src.name:{width}} -> {dst.relative_to(args.dest)}")
    for path in unsorted:
        print(f"UNSORTED {path.name}  (rename or use --map \"<substring>=<zone>\")")

    if not args.apply:
        print(f"\ndry run: {len(plan)} sorted, {len(unsorted)} unsorted. "
              f"Re-run with --apply to {'move' if args.move else 'copy'}.")
        return 0

    for src, dst in plan:
        dst.parent.mkdir(parents=True, exist_ok=True)
        final = unique_destination(dst)
        if args.move:
            shutil.move(str(src), final)
        else:
            shutil.copy2(src, final)
        print(f"{'moved' if args.move else 'copied'} -> {final.relative_to(args.dest)}")
    print(f"\ndone: {len(plan)} files placed into {args.dest}; rescan the library in the app.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
