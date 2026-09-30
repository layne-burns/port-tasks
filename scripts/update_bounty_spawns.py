"""Regenerates the bounty monsters' spawn areas from the OSRS Wiki (SPEC-routing.md §2.5.1).

Writes src/main/resources/com/nucleon/porttasks/bounty_spawns.json:
  spawns:    monster -> [{location, points: [[x, y], ...]}]
  safespots: monster -> [{x, y, w, h, plane, caption}]  (the page's "Safespot" maps; many monsters have none)
  safespotNotes: monster -> [sentence]  (prose mentioning a safespot, for monsters whose spot has no map)

Each monster page's Locations table has one LocLine per sea area, listing the spawn tiles in it. The monster
list comes from bounty_tasks.json (run update_bounty_data.py first).

Run from the repo root:  python scripts/update_bounty_spawns.py
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from update_courier_data import OUT, wikitext  # noqa: E402


def strip_links(s):
    """'[[Dusk's Maw]] - north-west of [[Laguna Aurorae|x]]' -> "Dusk's Maw - north-west of x"."""
    return re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", s).strip()


def spawns(monster, problems):
    text, rev = wikitext(monster)
    areas = []
    for m in re.finditer(r"\{\{LocLine(.*?)\n\}\}", text, re.S):
        body = m.group(1)
        loc = re.search(r"\|\s*location\s*=\s*(.*)", body)
        # Two spellings on the wiki: "|1116,2810" and "|x:1890,y:4145".
        points = [[int(x), int(y)] for x, y in re.findall(r"\|(?:x:)?(\d{3,4}),(?:y:)?(\d{3,4})", body)]
        if not points:
            problems.append(f"{monster}: an area without coordinates: {body[:80]!r}")
            continue
        areas.append({"location": strip_links(loc.group(1)) if loc else "?", "points": points})
    if not areas:
        problems.append(f"{monster}: no spawn areas found")
    return areas, safespots(text), safespot_notes(text), rev


def safespot_notes(text):
    """Sentences mentioning a safespot, from the page's prose (not file captions or maps): shown as hints."""
    notes = []
    for line in text.split("\n"):
        if line.lstrip().startswith(("[[File:", "{{Map", "|", "{{", "==")):
            continue
        for s in re.split(r"(?<=[.!?])\s+", strip_links(re.sub(r"<ref.*?</ref>|\{\{[^}]*\}\}", "", line))):
            if re.search(r"safe ?spot", s, re.I) and len(s) > 20:
                notes.append(s.strip())
    return notes


def safespots(text):
    """{{Map|name=Safespot ...|x=..|y=..|mtype=rectangle|rectX=..|rectY=..|caption=..}} -> [{x, y, w, h, caption}]."""
    out = []
    for m in re.finditer(r"\{\{Map\|([^{}]*)\}\}", text):
        a = {}
        for part in m.group(1).split("|"):
            if "=" in part:
                k, v = part.split("=", 1)
                a[k.strip().lower()] = v.strip()
        if "safe" not in (a.get("name", "") + a.get("caption", "")).lower():
            continue
        try:
            out.append({"x": int(a["x"]), "y": int(a["y"]), "w": int(a.get("rectx", 1)), "h": int(a.get("recty", 1)),
                        "plane": int(a.get("plane", 0)), "caption": strip_links(a.get("caption", a.get("name", "")))})
        except (KeyError, ValueError):
            continue
    return out


def main():
    problems = []
    tasks = json.loads((OUT / "bounty_tasks.json").read_text(encoding="utf-8"))["tasks"]
    monsters = sorted({t["monster"] for t in tasks.values()})
    out, safe, notes, revs = {}, {}, {}, {}
    for m in monsters:
        out[m], safe[m], notes[m], revs[m] = spawns(m, problems)
        print(f"{m}: {len(out[m])} areas, {sum(len(a['points']) for a in out[m])} spawns, "
              f"{len(safe[m])} safespots, {len(notes[m])} safespot notes")
    (OUT / "bounty_spawns.json").write_text(json.dumps(
        {"source": revs, "spawns": out, "safespots": safe, "safespotNotes": notes}, indent=1), encoding="utf-8")
    for p in problems:
        print("problem:", p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
