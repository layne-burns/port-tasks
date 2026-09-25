# Courier route planning — Specification (draft v0.2)

A personal extension of Port Tasks (not for the Plugin Hub). It plans the order of stops for your active courier tasks, shows only the path to the next stop, and rates notice-board tasks by how well they fit the tasks you already have.

Decisions so far (2026-09-25):
- Courier tasks only; no bounties.
- What to optimise is a setting.
- Show only the next leg's path.
- Built into this fork, on top of Port Tasks' tracking and port data.
- Cargo capacity is ignored: the hold is 160 and never binds.
- A stop costs a constant (a setting).
- Hazards are tabled until the user flags a route.
- The first pass is limited to the western region (§3.1).

---

## 1. Inputs (all readable in game; Port Tasks already reads them)

| Symbol | Meaning | Source |
|---|---|---|
| `p₀` | Where the player is: at a port, or at sea | player world position; `PortLocation` coordinates |
| `T` | Active courier tasks, at most 5 | varbits `PORT_TASK_SLOT_{0..4}_ID` → `CourierTaskData` (from the game's `PortTask` table) |
| `aᵢ`, `bᵢ` | Task `i`'s cargo (pickup) port and delivery port | `CourierTaskData.cargoLocation` / `deliveryLocation` |
| `nᵢ` | Number of crates in task `i` | `CourierTaskData.cargoAmount` |
| `takenᵢ`, `deliveredᵢ` | Crates of task `i` picked up and delivered so far | varbits `PORT_TASK_SLOT_k_CARGO_TAKEN` / `_CARGO_DELIVERED` |
| `rᵢ` | Reward for task `i`: base XP, and the expected bag value that follows from it | wiki data (§6); the game's task table has no XP column |
| `d(u, v)` | Travel cost between ports `u` and `v` (and from `p₀`) | §3 |
| `s` | Fixed cost of one stop (dock, walk to the ledger, load or unload, flashcards) | a setting, in **tile-equivalents** ("a stop costs as much as sailing `s` tiles"), so every cost is in tiles |

## 2. Problems

### 2.1 Route order (feature 1)

Find a sequence of stops, starting at `p₀`, that:
- **picks up** every crate not yet taken, at `aᵢ`, and **delivers** every crate taken but not yet delivered, at `bᵢ`;
- never delivers a crate before it has been picked up (precedence).

and minimises `Σ d(consecutive stops) + s × (number of distinct stops)`. Consecutive actions at the same port count as one stop. That is where shared ports pay off.

Without a capacity limit, this is an **open travelling-salesman path with precedence constraints**: a single-vehicle pickup-and-delivery problem with no return to the start. It's NP-hard in general, but here there are at most 5 tasks, so at most 10 stop events. There are at most 10!/2⁵ ≈ 113 000 precedence-respecting orders, so an exact depth-first search with pruning finishes in milliseconds.

The objective setting only changes the cost used:
- **Least time:** cost = sailed tiles + `s` × stops.

**Start port.** The user teleports to boards to take tasks, then starts the route from the far end. Summoning the boat moves it, but clears any cargo aboard. So:
- Before any crate is aboard, the start port is a free choice among ports the player can teleport to and summon the boat at, and the solver also picks it.
- Once cargo is aboard, the route starts at the boat's current position.
- **XP/hour:** for a fixed task set the reward is fixed, so the best order is the same as for least time. The setting matters in §2.2.
- **Coins:** as XP/hour, using the coin reward.

### 2.2 Board selection (feature 2)

At a notice board offering tasks `O`, with `f` free slots, score each subset `S ⊆ O` with `|S| ≤ f` by its **marginal cost**

  `Δ(S) = best(T ∪ S) − best(T)`   (best = the §2.1 optimum)

Enumerating the subsets is cheap: a board shows few tasks and `f ≤ 5`. Several metrics are shown side by side, and one setting picks which one ranks:

| Metric | Formula | What it captures |
|---|---|---|
| **XP per added tile** (the user's proposal) | `ΔXP / Δ(S)` | Reward rate of what the pick adds, including stop costs |
| **Coins per added tile** | `E[coins](S) / Δ(S)` | Same for coin bags (§6) |
| **Route fit** | `Δ(S) / standalone(S)` | Share of the pick's own route that is new sailing. 0 means it rides entirely along the current route |
| **Plan rate after** | `XP(T ∪ S) / best(T ∪ S)` | Whether taking it raises the XP per tile of the whole plan, not just the margin |

The last one guards against a high marginal ratio on a tiny pick that lowers the overall rate.

### 2.3 Output

- The **next leg only**: draw the path from `p₀` to the first stop of the optimal route. This replaces Port Tasks' "all paths at once" while planning is on.
- Re-plan whenever a task is taken, completed or dropped, or crates are picked up or delivered (the varbits change), and on leaving a port.
- Board scores: the metrics above, shown on or next to each offered task. The exact layout is still to be decided.

## 3. Travel cost `d(u, v)`

No available plugin does sea pathfinding. As of 2026-09-24, Shortest Path says in its code that it doesn't model sailing navigation. Duckblade's Sailing plugin draws hazard overlays but doesn't plan routes. Plan:

1. **v1, port-path graph.** Port Tasks' 164 hand-drawn `PortPaths` (sequences of relative moves) become weighted edges between ports, weighted by path length in tiles. All-pairs shortest paths (Floyd–Warshall over about 30 ports) then give `d` for any pair. The next leg is drawn by concatenating the underlying hand-drawn paths.
2. **v2, learned times.** Record real sailing times between ports during play, and replace or calibrate tile lengths with measured seconds (averaged per pair). Also measure the stop overhead `s`.
3. **v3, only if needed.** A real sea pathfinder over water tiles, which needs the boat's size and turning behaviour.

`p₀` at sea: use the nearest point on any port path, or the distance to the nearest port plus that port's `d`.

### 3.1 Region, first pass

Western ports only:
- Void Knights' Outpost (Pest Control), Deepfin Point, Aldarin, Sunset Coast
- Civitas illa Fortis, Port Roberts, Port Tyras
- Prifddinas (needs Song of the Elves, so check it's unlocked)
- Land's End, Hosidius, Port Piscarilius, Piscatoris
- Rellekka, Lunar Isle, Jatizso, Neitiznot, Etceteria

The region is a set of ports, so it can be widened later. Tasks with any port outside it are shown as out of region, not planned. The user has Sailing level 92, so every port is unlocked except those behind Song of the Elves.

## 4. Hazards

Tabled. The user will flag routes that cross dangerous water; a flagged route-graph edge then gets a penalty, or is removed.

## 5. Open questions

1. How does the boat get to the chosen start port: Summon Boat after teleporting, or another way? Is there a cooldown or cost worth modelling?
2. Default stop cost `s` in tile-equivalents: pick a starting value (for example 60 tiles), then tune it.
3. Board display: a badge on each offered task, a side list, or both.
4. Can reward bags be priced at all? If not, the coin metric counts coin bags only.

## 6. Reward data (checked against the wiki, 2026-09-25)

- **XP isn't in the game's task table**, so it has to come from outside. Port Tasks v1.6.0 (June 2026) hardcodes `TaskReward` for 240 entries (couriers plus bounties), keyed by DB row. The wiki's *Courier tasks* page lists **439** standard courier tasks, keyed by task ID. Many values differ: the smallest by 1–4 XP, some by 10% or more. **Plan:** a script generates `courier_tasks.json` (task ID → level, XP, ports, crate count) from the wiki page; the plugin joins it on `CourierTaskData.id` and falls back to `TaskReward`. The task ID mapping will be verified in game.
- **Bags (26 August 2026 update):** each courier task gives **one** bag: a coin bag with probability 4/5, a reward bag with probability 1/5. Bounty tasks stopped giving bags on 2 September. The size depends only on the task's **base XP** (before the keg of horizon's lure):

| Size | Base XP | Coins in a coin bag | Mean |
|---|---|---|---|
| Tiny | < 400 | 800–1,200 | 1,000 |
| Small | 400–999 | 1,613–2,714 | ~2,160 |
| Medium | 1,000–2,499 | 2,486–3,595 | ~3,040 |
| Large | 2,500–5,999 | 3,864–5,350 | ~4,610 |
| Huge | ≥ 6,000 | 6,400–9,600 | 8,000 |

  So expected coins per task = 0.8 × the mean for its size, plus 0.2 × the reward bag's value if it can be priced. The step thresholds make "just over 2,500 XP" tasks noticeably better value for coins.
- Wiki sources: *Courier tasks* (revision 15327263), *Port coin bag*, *Port reward bag*, and *Module:CourierTaskLine* (bag thresholds, edited 27 August 2026).
