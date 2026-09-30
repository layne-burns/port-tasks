"""Regenerates the bounty hunt data from the OSRS Wiki (SPEC-routing.md §2.5).

Writes src/main/resources/com/nucleon/porttasks/bounty_tasks.json:
  task id -> level, base XP, notice board, monster, body part, quantity, drop rarity, guaranteed, bag size

"Guaranteed" is the wiki's mark for the one bounty each notice board always offers; the rest of a board's
bounties are a random draw from its pool.

Run from the repo root:  python scripts/update_bounty_data.py
It prints anything it could not parse, so odd rows can be reviewed.
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from update_courier_data import OUT, PORT_ALIASES, template_args, wikitext  # noqa: E402


def bag_sizes(text, problems):
    """Base XP -> bag size, from the page's 'size of the bag received' table (bounties have their own)."""
    sizes = {}
    for m in re.finditer(r"^\|\s*([\d,]+)\s*\|\|\s*(Tiny|Small|Medium|Large|Huge)\s*$", text, re.M):
        sizes[int(m.group(1).replace(",", ""))] = m.group(2)
    if not sizes:
        problems.append("no bag size table found")
    return sizes


def bounty_tasks(problems):
    text, rev = wikitext("Bounty tasks")
    sizes = bag_sizes(text, problems)
    tasks = {}
    for m in re.finditer(r"\{\{BountyTaskLine\|(.*?)\}\}", text, re.S):
        a = template_args("x|" + m.group(1))
        tid = a.get("taskId")
        xp = a.get("xp", "").replace(",", "")
        if not tid or not a.get("level", "").isdigit():
            problems.append(f"unparseable row: {m.group(0)[:100]}")
            continue
        board = a.get("noticeBoard")
        if board in PORT_ALIASES:
            problems.append(f"task {tid}: wiki port name {board!r} read as {PORT_ALIASES[board]!r}")
            board = PORT_ALIASES[board]
        tasks[tid] = {
            "level": int(a["level"]),
            "xp": int(xp) if xp.isdigit() else None,
            "noticeBoard": board,
            "monster": a.get("monster"),
            "item": a.get("item"),
            "qty": int(a.get("qty", "1") or 1),
            "rarity": a.get("rarity"),
            "guaranteed": a.get("guaranteed", "").lower() == "yes",
            "bag": sizes.get(int(xp)) if xp.isdigit() else None,
        }
        if tasks[tid]["bag"] is None:
            problems.append(f"task {tid}: no bag size for XP {xp!r}")
    return tasks, rev


def main():
    problems = []
    tasks, rev = bounty_tasks(problems)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "bounty_tasks.json").write_text(json.dumps(
        {"source": {"page": "Bounty tasks", "revid": rev}, "tasks": tasks}, indent=1), encoding="utf-8")

    boards = {t["noticeBoard"] for t in tasks.values()}
    guaranteed = {}
    for t in tasks.values():
        if t["guaranteed"]:
            guaranteed.setdefault(t["noticeBoard"], []).append(f"{t['monster']} / {t['item']}")
    print(f"bounty tasks: {len(tasks)} on {len(boards)} boards (wiki rev {rev})")
    print(f"monsters: {len({t['monster'] for t in tasks.values()})}, parts: {len({t['item'] for t in tasks.values()})}")
    for b in sorted(boards):
        g = guaranteed.get(b, [])
        if len(g) != 1:
            problems.append(f"{b}: {len(g)} guaranteed bounties (expected 1): {g}")
    for p in problems:
        print("problem:", p)
    return 0


if __name__ == "__main__":
    sys.exit(main())
