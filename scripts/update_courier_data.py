"""Regenerates the courier routing data from the OSRS Wiki (SPEC-routing.md §6).

Writes, under src/main/resources/com/nucleon/porttasks/:
  courier_tasks.json  task id -> level, base XP, notice board, cargo port, destination, crates, name
  reward_bags.json    bag size -> shared drops; bag size -> destination port -> signature drops

Item ids come from the wiki's item mapping for tradeable items, and from each untradeable item's infobox
otherwise. Alch values are NOT stored: the plugin reads them from the game's item definitions at runtime.

Run from the repo root:  python scripts/update_courier_data.py
It prints anything it could not parse or resolve, so ambiguous values can be reviewed.
"""

import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://oldschool.runescape.wiki/api.php"
MAPPING = "https://prices.runescape.wiki/api/v1/osrs/mapping"
UA = {"User-Agent": "port-tasks-routing data script (github.com/layne-burns/port-tasks)"}
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/com/nucleon/porttasks"
SIZES = ["Tiny", "Small", "Medium", "Large", "Huge"]
# Misspellings on the wiki's reward-bag pages (as of 2026-09-25) -> Port Tasks' PortLocation names.
PORT_ALIASES = {"Jatizo": "Jatizso", "Priffddinas": "Prifddinas"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def wikitext(page):
    q = urllib.parse.urlencode({"action": "parse", "page": page, "prop": "wikitext|revid",
                                "format": "json", "formatversion": 2})
    p = get(f"{API}?{q}")["parse"]
    return p["wikitext"], p["revid"]


def template_args(body):
    args = {}
    for part in body.split("|")[1:]:
        if "=" in part:
            k, v = part.split("=", 1)
            args[k.strip()] = v.strip()
    return args


def parse_quantity(q):
    """'13-18 (noted)' -> (13, 18, True); '26859' -> (26859, 26859, False). None if unparseable."""
    noted = "noted" in q.lower()
    nums = [int(n.replace(",", "")) for n in re.findall(r"\d[\d,]*", q)]
    if not nums:
        return None
    return nums[0], nums[-1] if len(nums) > 1 else nums[0], noted


def infobox_id(name):
    """Item id from the wiki infobox, for items the price mapping lacks (untradeables)."""
    q = urllib.parse.urlencode({"action": "parse", "page": name, "prop": "wikitext", "format": "json",
                                "formatversion": 2, "redirects": 1})
    d = get(f"{API}?{q}")
    if "error" in d:
        return None
    m = re.search(r"\|\s*id\d*\s*=\s*(\d+)", d["parse"]["wikitext"])
    return int(m.group(1)) if m else None


def bag_items(problems):
    """Item id -> {type: coin|reward, size}. Reward bags have one id per port (30 per size)."""
    out = {}
    for size in SIZES:
        for kind in ("coin", "reward"):
            page = f"{size} port {kind} bag"
            q = urllib.parse.urlencode({"action": "parse", "page": page, "prop": "wikitext", "format": "json",
                                        "formatversion": 2, "redirects": 1})
            d = get(f"{API}?{q}")
            text = d.get("parse", {}).get("wikitext", "")
            ids = [int(x) for m in re.findall(r"\|\s*id\d*\s*=\s*([\d, ]+)", text) for x in m.split(",") if x.strip()]
            if not ids:
                problems.append(f"no item ids for {page}")
            for i in ids:
                out[str(i)] = {"type": kind, "size": size}
    return out


def courier_tasks(problems):
    text, rev = wikitext("Courier tasks")
    tasks = {}
    for m in re.finditer(r"\{\{CourierTaskLine\|(.*?)\}\}", text, re.S):
        a = template_args("x|" + m.group(1))
        tid = a.get("taskId")
        xp = a.get("xp", "").replace(",", "")
        if not tid:
            problems.append(f"task without id: {a.get('transcript')}")
            continue
        if not xp.isdigit():
            problems.append(f"task {tid} ({a.get('transcript')}): XP unknown on the wiki")
        tasks[tid] = {
            "name": a.get("transcript"),
            "level": int(a["level"]),
            "xp": int(xp) if xp.isdigit() else None,
            "noticeBoard": a.get("noticeBoard"),
            "cargoPort": a.get("cargoLocation"),
            "destination": a.get("destination"),
            "crates": int(a.get("qty", "1") or 1),
        }
    return tasks, rev


def reward_bags(items, problems):
    bags = {"shared": {}, "signature": {}}
    revs = {}
    for size in SIZES:
        text, rev = wikitext(f"{size} port reward bag")
        revs[size] = rev
        shared, signature = [], {}
        for m in re.finditer(r"\{\{DropsLineReward\|(.*?)\}\}", text, re.S):
            a = template_args("x|" + m.group(1))
            name = a.get("name")
            qty = parse_quantity(a.get("quantity", ""))
            if not name or qty is None:
                problems.append(f"{size}: unparseable drop line {m.group(0)[:80]}")
                continue
            entry = {"name": name, "id": None if name == "Coins" else items.get(name.lower(), {}).get("id"),
                     "min": qty[0], "max": qty[1], "noted": qty[2]}
            if a.get("rarity"):
                entry["rarity"] = a["rarity"]
            port = a.get("dropversion")
            if port in PORT_ALIASES:
                problems.append(f"{size}: wiki port name {port!r} read as {PORT_ALIASES[port]!r}")
                port = PORT_ALIASES[port]
            (signature.setdefault(port, []) if port else shared).append(entry)
        bags["shared"][size] = shared
        bags["signature"][size] = signature
    return bags, revs


def main():
    problems = []
    items = {x["name"].lower(): x for x in get(MAPPING)}
    tasks, task_rev = courier_tasks(problems)
    bags, bag_revs = reward_bags(items, problems)

    drops = [d for size in SIZES
             for d in bags["shared"][size] + [x for v in bags["signature"][size].values() for x in v]]
    looked_up = {}
    for d in drops:
        if d["id"] is None and d["name"] != "Coins":
            if d["name"] not in looked_up:
                looked_up[d["name"]] = infobox_id(d["name"])
            d["id"] = looked_up[d["name"]]

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "courier_tasks.json").write_text(json.dumps(
        {"source": {"page": "Courier tasks", "revid": task_rev}, "tasks": tasks}, indent=1), encoding="utf-8")
    items_by_id = bag_items(problems)
    (OUT / "reward_bags.json").write_text(json.dumps(
        {"source": {f"{s} port reward bag": r for s, r in bag_revs.items()}, **bags, "bagItems": items_by_id},
        indent=1), encoding="utf-8")
    print(f"bag item ids: {len(items_by_id)}")

    print(f"courier tasks: {len(tasks)} (wiki rev {task_rev})")
    for size in SIZES:
        print(f"{size:6} bag: {len(bags['shared'][size])} shared drops, signature drops for {len(bags['signature'][size])} ports")
    print(f"untradeables resolved from infoboxes: {sorted((n, i) for n, i in looked_up.items())}")
    for p in problems:
        print("problem:", p)
    return 1 if any(i is None for i in looked_up.values()) else 0


if __name__ == "__main__":
    sys.exit(main())
