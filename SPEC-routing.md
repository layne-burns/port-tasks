# Courier route planning — Specification (draft v0.3)

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
| `p₀` | Where the **boat** is: the dock it's moored at, or its position at sea. Plan from the boat, not the player, since the player teleports around taking tasks while the boat waits | boat position/dock (varbit to identify in phase 1); `PortLocation` coordinates |
| `T` | Active courier tasks, at most 5 | varbits `PORT_TASK_SLOT_{0..4}_ID` → `CourierTaskData` (from the game's `PortTask` table) |
| `aᵢ`, `bᵢ` | Task `i`'s cargo (pickup) port and delivery port | `CourierTaskData.cargoLocation` / `deliveryLocation` |
| `nᵢ` | Number of crates in task `i` | `CourierTaskData.cargoAmount` |
| `takenᵢ`, `deliveredᵢ` | Crates of task `i` picked up and delivered so far | varbits `PORT_TASK_SLOT_k_CARGO_TAKEN` / `_CARGO_DELIVERED` |
| `rᵢ` | Reward for task `i`: base XP, and the expected bag value that follows from it | wiki data (§6); the game's task table has no XP column |
| `d(u, v)` | Travel cost between ports `u` and `v` (and from `p₀`) | §3 |
| `s` | Fixed cost of one stop (dock, walk to the ledger, load or unload, flashcards) | a setting, in **tile-equivalents** ("a stop costs as much as sailing `s` tiles"), so every cost is in tiles. **Default 30**: the user's estimate is that most ledgers are 10–15 steps from the gangplank |

## 2. Problems

### 2.1 Route order (feature 1)

Find a sequence of stops, starting at `p₀`, that:
- **picks up** every crate not yet taken, at `aᵢ`, and **delivers** every crate taken but not yet delivered, at `bᵢ`;
- never delivers a crate before it has been picked up (precedence).

and minimises `Σ d(consecutive stops) + s × (number of distinct stops)`. Consecutive actions at the same port count as one stop. That is where shared ports pay off.

Without a capacity limit, this is an **open travelling-salesman path with precedence constraints**: a single-vehicle pickup-and-delivery problem with no return to the start. It's NP-hard in general, but here there are at most 5 tasks, so at most 10 stop events. There are at most 10!/2⁵ ≈ 113 000 precedence-respecting orders, so an exact depth-first search with pruning finishes in milliseconds.

The objective setting only changes the cost used:
- **Least time:** cost = sailed tiles + `s` × stops.

**The user's cycle ("sweep").** Work between two far ends of the region, for example Lunar Isle and Deepfin Point:
1. The boat is at one end.
2. The player teleports around, taking tasks at notice boards.
3. The player returns to the boat and sails to the other end, doing pickups and deliveries along the way.
4. Repeat from the other end.

So the route starts at the **boat's port**. The end is either free (open path) or fixed at the opposite end, which is a setting (**End port: free / \<port\>**). A fixed end prefers plans that leave the boat ready for the next sweep. Summoning the boat or moving it mid-route isn't modelled: it clears cargo, and the user doesn't do it. Teleport charges (for example the boots' 3 uses to reach Lunar Isle) are out of scope for now.
- **XP/hour:** for a fixed task set the reward is fixed, so the best order is the same as for least time. The setting matters in §2.2.
- **Coins:** as XP/hour, using the coin reward.

### 2.2 Board selection (feature 2)

At a notice board offering tasks `O`, with `f` free slots, score each subset `S ⊆ O` with `|S| ≤ f` by its **marginal cost**

  `Δ(S) = best(T ∪ S) − best(T)`   (best = the §2.1 optimum)

Enumerating the subsets is cheap: a board shows few tasks and `f ≤ 5`. Several metrics are shown side by side, and one setting picks which one ranks:

| Metric | Formula | What it captures |
|---|---|---|
| **XP per added tile** (the user's proposal) | `ΔXP / Δ(S)` | Reward rate of what the pick adds, including stop costs |
| **Value per added tile** | `E[value](S) / Δ(S)` | Same for the expected bag value, coin and reward bags (§6) |
| **Route fit** | `Δ(S) / standalone(S)` | Share of the pick's own route that is new sailing. 0 means it rides entirely along the current route |
| **Plan rate after** | `XP(T ∪ S) / best(T ∪ S)` | Whether taking it raises the XP per tile of the whole plan, not just the margin |

The last one guards against a high marginal ratio on a tiny pick that lowers the overall rate.

### 2.3 Output

- The **next leg only**: draw the path from `p₀` to the first stop of the optimal route. This replaces Port Tasks' "all paths at once" while planning is on.
- Re-plan whenever a task is taken, completed or dropped, or crates are picked up or delivered (the varbits change), and on leaving a port.
- Board scores:
  - **Side list:** in the Port Tasks panel, every offered task with all the metrics, sorted by the chosen one.
  - **On the board:** a badge or highlight on each offered task, showing the chosen metric's rank or value, with the best pick highlighted.

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

1. Which varbit, or other game state, gives the boat's current dock or position? Find this in phase 1.
2. Reward-bag drop rates aren't on the wiki. Uniform weighting is the stand-in until better data turns up.

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

- **Expected value per task** = 0.8 × E[coin bag] + 0.2 × E[reward bag] for its size and **destination port**.
  - Reward bags have shared contents for each size, plus the destination port's **signature drops** (for example grapes at Aldarin). The wiki lists the items and quantities (*Tiny/…/Huge port reward bag* and the port table on *Port reward bag*), but **no rates**, so each listed drop is weighted equally.
  - Items are valued at their **high-alchemy price**, because the user is an ironman and GE prices mean nothing to them. The price is read at runtime from the item definition (`ItemComposition.getHaPrice()`). Coins count at face value.
  - Items with no alch value (for example sawmill coupons) take a value you set per item, default 0.
  - The step thresholds make tasks just over a threshold (for example 2,500 XP) noticeably better value than those just under.
- Wiki sources: *Courier tasks* (revision 15327263), *Port coin bag*, *Port reward bag*, and *Module:CourierTaskLine* (bag thresholds, edited 27 August 2026).

## 7. Build phases (each ends with an in-game check by the user)

1. **Data.**
   - A script pulls the wiki into `courier_tasks.json` (task ID → level, XP, ports, crates) and `reward_bags.json` (size → shared drops; port → signature drops).
   - Join on `CourierTaskData.id`, and verify in game that task names match.
   - Identify the boat-position varbit.
2. **Route graph.** `PortPaths` becomes a weighted graph, then all-pairs shortest paths, filtered to the region. Unit tests on known pairs.
3. **Solver and next leg.** An exact search over precedence-respecting orders, with optional fixed end, from the boat's port. Draw only the next leg; re-plan on task or cargo varbit changes. Unit tests with hand-checked small cases.
4. **Board scoring.** The four metrics for every subset up to the free slots; the side list in the panel; badges and highlight on the board interface.
5. **Learned times (v2).** Record leg times and stop durations during play, and calibrate `d` and `s`.
