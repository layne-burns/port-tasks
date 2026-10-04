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

**Best set (built 2026-09-27).** The metrics above rank single tasks; the suggestion is now a whole set: the `S` (with `|S| ≤ f`, `S = ∅` allowed) maximising **plan rate after**, `(R(T) + R(S)) / best(T ∪ S)`, with `R` = XP or expected bag value (setting). Rationale: continuous sailing is a renewal process whose long-run rate is reward per cycle over tiles per cycle, and choosing the cycle with the best ratio is Dinkelbach's criterion (take a set exactly when it beats the going rate). Every set is tried (`Σ_{i≤f} C(|O|, i)`, e.g. 1,586 for 12 tasks and 5 slots); the planner does one plan in ~30 µs (array DP over the 3^t reachable event masks), so a large board takes ~0.2 s, run off the client thread. Above 5,000 sets only the strongest single candidates are kept and the result is marked approximate. `f` = slots for the Sailing level (1, +1 at 7, 28, 56, 84) minus tasks held. Known limit: where the plan ends isn't valued (a set ending near good boards isn't preferred).

### 2.3 Output

- The **next leg only**: draw the path from `p₀` to the first stop of the optimal route. This replaces Port Tasks' "all paths at once" while planning is on.
- Re-plan whenever a task is taken, completed or dropped, or crates are picked up or delivered (the varbits change), and on leaving a port.
- Board scores (phase 4, agreed 2026-09-25):
  - Each metric in §2.2 has its own **show in tooltip** toggle, and one setting picks the **ranking** metric.
  - **Tooltip:** the existing Port Tasks board tooltip gains the enabled metrics, plus the task's **signature drops** (the delivery port's unique items for its bag size), always shown.
  - **Side list:** in the Port Tasks panel, every offered task with the enabled metrics, sorted by the ranking metric.
  - **On the board:** a rank badge on each offered task, and the best pick highlighted.
  - Scoring is per offered task (`S` = one task). Combinations are out of scope for now.
- **Wanted items:** a settings list, one item per line, with an optional `= value` (for example `Crystal shard = 2000`).
  - Tasks that can give a listed item are highlighted, and the item is marked in their tooltip.
  - A given value replaces the item's alch value in the value metrics. This replaces the separate crystal-shard and spirit-flake settings.
- Leg drawing (phase 3, changed 2026-09-25): the Shortest Path plugin now paths at sea, so by default the next leg is drawn by it. We send the next stop's dock as its target over its PluginMessage API, and only when the next stop changes. **Changed 2026-09-27:** Shortest Path is the only leg drawing; our own drawn-path rendering, Port Tasks' per-task lines and their tracer were removed, and routing is always on.

### 2.4 The loop (added 2026-09-29)

A **loop** is a small set of ports `L` the player sails between for a whole session, chosen so that the notice boards there offer mostly large bags for tasks inside `L`. It is a setting (**Loop ports**, a list of names), separate from the bag-size filter: the filter decides *which rewards* count, the loop decides *where to sail*. They meet only in the loop suggestions (§2.4.1), which count allowed bag sizes only.

**Editing the port lists** (3 October 2026). The three port-list settings (**Loop ports**, **Gather by sea only**, **Not in loop suggestions**) are edited as tick boxes in the side panel (**Edit loop ports**: one row per port, alphabetical, columns Loop / Sea / Skip; Sea is greyed for ports without a notice board). Typed names were error-prone, so the text fields are hidden from the config panel. The settings themselves stay comma-separated full port names (suggestions write the same setting, and the boxes follow any change). A newly ticked loop port goes last, so the loop keeps the order its ports were ticked in, which is the order boards are gathered in.

- A task is **in the loop** if its pickup and delivery ports are both in `L` (it is taken at a loop board anyway).
- **Board ranking:** in-loop tasks rank first, then the rest, each group by the ranking metric. Off-loop tasks are still shown and scored: dimmed under an "Off loop" divider in the side list, and badged "off loop" in grey on the board. Rationale: the §2.2 margins don't see the cost of getting back into the loop afterwards (the plan's end isn't valued), so the loop stands in for that.
- **Best set:** chosen from in-loop tasks only.

#### 2.4.1 Loop suggestions

Boards show a random draw from a fixed pool per port (the wiki's *Courier tasks* table gives each task one board), so a loop is judged by its pools. On request ("Suggest loops" in the side panel), every set `L` of 2–4 usable ports is scored:

- **pool tasks in `L`:** tasks whose board, pickup and delivery are all in `L`, that the player can take (level) and wants (bag-size filter), with known XP;
- **loop tiles** `c(L)`: the shortest cycle through `L` in the port graph (for 2 ports, twice the distance; for 4, the best of the 3 distinct tours);
- **density** `D(L) = Σ XP of the pool tasks in L / c(L)`, shown per 1,000 tiles.

Loops need at least **3 pool tasks per port** to qualify (without that floor, two close ports sharing a couple of tasks come out on top), and rank by `D`. Usable ports: all, minus ports above the player's level, minus Prifddinas before Song of the Elves, minus the **Not in loop suggestions** list. Clicking a suggestion sets the loop. The search is ~28k port sets × a few hundred tasks as bit masks, a few milliseconds, off the client thread.

A density is not a rate: a board shows only part of its pool at a time, so the real XP per tile depends on the draw. It ranks loops by how much worthwhile work sits inside them per tile of sailing round them.

#### 2.4.2 Loop mode: gather, sail, dry (added 2026-09-29)

**Board behaviour it relies on.** Boards reroll together, reportedly after every **8 completed port tasks** and at the **daily reset** (00:00 UTC). Port Tasks' reset tracker counts this way, and the wiki marks the 8 as "confirmation needed". Between resets a board keeps its offers, minus what was taken. (This supersedes §6a's "boards change whenever tasks are handed in".) So:

- **Memory.** Every opened board's courier offers are remembered for the current reset cycle, in the profile (key `routingLoopBoards`). They're forgotten when the 8-task count rolls over or the UTC day turns. The player's tile at each board is also kept, for guidance. **Check in play:** a reopened board showing offers it didn't have before is logged (`[loop] ... new offers without a reset`), which is how the 8-task rule gets tested.
- A remembered offer is **worthwhile** if it is in the loop, its bag size is allowed, the level is high enough, and it isn't held.

**Phases** (loop ports without a notice board are skipped):

| Phase | When | What the plugin does |
|---|---|---|
| **Gather** | a loop board hasn't been seen this cycle, and the player hasn't boarded the boat since the reset (boarding ends the gather for the cycle, straight into Sail). Boards in **Gather by sea only** (e.g. Lunar Isle, awkward to teleport to) are left out: never a guidance target, not needed to finish; they're looked at when the route docks there | side panel lists each board as not seen / n to take; the next-stop panel says "Look at <port>". Off the boat, Shortest Path is pointed at the first unseen board (its remembered tile, else the port's dock tile) with the player's own settings, so teleports are used. On the boat, the sailing leg shows as usual |
| **Sail** | all seen, some worthwhile offers left | normal routing. Docked at a loop port with a free slot, the text above the player says how many loop tasks its board still has |
| **Dry** | all seen, none worthwhile | teleporting round again wouldn't help: nothing new comes before the reset. The board list switches to **fillers**: every task in or out of the loop, ranked by added cost (least first), with no best set. Fillers keep to the allowed bag sizes (sizes switched off stay dimmed and unlisted) unless **Dry loop: any bag size** is on, which lists every size and dims nothing (until 3 October 2026 that was the only behaviour, so the bag boxes seemed to do nothing while dry). It shows the tasks left until the reset. After the reset, the memory is empty and it's Gather again |

### 2.5 Bounty hunt (added 2026-09-30)

For monsters the player picks (**Bounty hunt** setting, or the side panel's "Choose monsters"), where each body part's bounty task can be had.

- **Data:** `scripts/update_bounty_data.py` writes `bounty_tasks.json` from the wiki's *Bounty tasks* table: task id → level, base XP, notice board, monster, part, quantity, drop rarity, **guaranteed**, bag size. Each board always offers exactly one bounty (its guaranteed one; the script checks there is one per board); the rest are a random draw from its pool. Every monster is guaranteed somewhere, but not every part (e.g. Great white shark liver: 6 possible boards, none guaranteed). Bag sizes come from the page's own bounty XP table.
- **Board states** per part and board, using the board memory of §2.4.2 (now bounty offers too): *offered* (on the board when last opened this reset cycle), *always* (not opened, but it's the board's guaranteed bounty), *check* (not opened, in its pool), *not now* (opened this cycle without it). Tasks above the player's level are left out, and so are boards the player can't use. **Ports vs boards:** a port can be reached (docked at) without its board being usable. Prifddinas needs Song of the Elves; Port Tyras needs Regicide to dock, but its **board** needs Song of the Elves (seen in game, 30 September 2026). Unreachable ports are left out of loop suggestions entirely; an unusable board's tasks are left out of the hunt, the suggestion pool and the loop gather, while its port still counts as a stop. Quest states are read once per login, off the event handlers; until then those ports and boards count as unusable. A part with a held task is shown as held.
- **Search order:** offered and always first, then check, then not now; within each, nearest first by straight-line distance from the player (a rough guide, since teleports ignore it).
- **Guidance:** off the boat, Shortest Path is pointed at the first part's first board (not held, not "not now"), with the player's own settings. Loop mode's gather takes precedence when both apply.
- **Value:** each line shows quantity, bag size and the expected bag value at high-alchemy prices: 4/5 coin bag, 1/5 reward bag (assumed the same split as couriers), counting only the drops all ports' bags share, because a bounty is claimed at any port master and the wiki doesn't say whose port-themed items its reward bag holds.
- **On the board:** hunted bounties (not held) are outlined in the wanted-item colour and labelled "Hunt".

#### 2.5.1 Sailing to the monsters (added 2026-09-30)

- **Data:** `scripts/update_bounty_spawns.py` writes `bounty_spawns.json` from each bounty monster's wiki page: its *Locations* table, one area per LocLine (a named sea area and its spawn tiles; two spellings of coordinates on the wiki). All 20 monsters have areas. Each area's target is the spawn tile nearest its centre: a real spawn, so a sea tile Shortest Path can reach.
- **When:** at sea (on the boat, not docked), setting **Sail to bounty monsters** on, and a held bounty still missing parts.
- **Which area:** the one estimated quickest to sail to, over the monsters of all incomplete held bounties. There is no sea pathfinder, so the estimate goes through the port graph: `est(A) = |boat − p₀| + min_p ( d(p₀, p) + |p − A| )`, where `p₀` is the port nearest the boat, `d` the port-graph distance and `|x − A|` the straight distance to A's nearest spawn. A plain straight line would cross land.
- **Staying:** the choice is locked to that task until its parts are in (`items collected ≥ quantity`), then the next is chosen from where the boat is. With every held bounty done and no courier plan, it heads for the nearest reachable port (any port master claims).
- **Shortest Path:** sent as a sailing leg (sea only), starting from the boat's position when the target was chosen, and it wins over the courier leg while set. It is only re-sent when the target changes.

#### 2.5.2 Safespots

- The wiki maps a safespot for only two bounty monsters (Great white shark, Lonely Sea; Orca, near Neitiznot), as `{{Map|mtype=rectangle}}` pins, read here as a centre and a size (**to confirm in game**: if the drawn tiles are offset, the pin is a corner). Five more pages describe one in words only (Armoured kraken, Hammerhead shark, Stingray, Veiled kraken; Albatross by picture); those sentences are kept as **notes** and shown in the side panel.
- **Save safespot here** (side panel, while sailing for a monster) records the boat's tile as a safespot for it (profile key `routingSafespots`); **Forget saved** clears them.
- A monster with any safespot (wiki or saved) is sailed to at its nearest safespot instead of its spawn areas. The target safespot's tiles are outlined on the sea (top-level world view; drawn only while one is the target).

#### 2.5.3 Bounty AFK (added 2026-09-30)

A blackout-while-planted mode for AFKing a bounty monster (usually on another monitor). Port Tasks is the base; Watchdog shows the blackout (it has no incoming-plugin-message trigger, so it matches chat lines); AnkiScape's Bounty mode runs cards.

- **Arm:** examine the monster (setting **AFK monster**: Auto = whichever bounty monster is examined, or one monster). Examining it again turns it off.
- **States:** OFF → WAITING (`[Bounty AFK] on: <monster>`; blackout) → corpses of that monster within 20 tiles (the dead NPC, `deadNpcId`) are counted, and the **loot alert** goes when **N wait** (default 3) or the **oldest is within S seconds** (default 20) of its despawn (**corpse lifetime** setting, default 120 s: four unlooted corpses despawned at exactly 120 s in play, 2026-09-30; upstream's despawn timer assumed 300 ticks and is corrected to 200) → DOWN (`<n> to loot: <monster> down`; blackout off + flash) → all looted, then the boat hits one → WAITING (`<monster> fighting`; blackout). Batched looting, without losing a corpse to despawn.
- **Attack** = a hitsplat marked `mine` on that monster within 20 tiles of the boat. The crew's cannon hits are marked `mine` too (phase 0 log, 2026-09-30), so crew-only fire keeps it going, and other players' hits don't.
- **Auto arm:** the boat hitting the monster of a held bounty that still needs parts switches it on (Auto: any; else only the chosen monster). Examine is the manual switch. After examining it off, auto mode leaves that monster alone until the player leaves the boat.
- **Crew mode:** when the crew leaves free-for-all by itself, the player's overhead says "Fire!". That's a Watchdog overhead-text alert (flash), with no Port Tasks code.
- **Off** (`off: <reason>`; blackout off + flash): that monster's held bounty has all its parts; docked (move mode 4); off the boat; examined again; or the boat moved (move mode 1–3) and didn't hit the monster within **AFK grace after moving** (default 20 s). The grace is checked each tick only while it runs.
- **Anki:** PluginMessage `porttasks` / `bountyAfk` `{action, monster}`: "start" the first time it's armed with the boat parked (move mode 0), "stop" when it goes off.
- **Phase 0 logging** (`[bountyafk]` in the client log, only on the boat): hitsplats on bounty monsters, overhead text, non-chat messages (the crew's fire-mode lines), NPC clicks, corpse spawn/despawn. Temporary.

## 3. Travel cost `d(u, v)`

No available plugin does sea pathfinding. As of 2026-09-24, Shortest Path says in its code that it doesn't model sailing navigation. Duckblade's Sailing plugin draws hazard overlays but doesn't plan routes. Plan:

1. **v1, port-path graph.** Port Tasks' 164 hand-drawn `PortPaths` (sequences of relative moves) become weighted edges between ports, weighted by path length in tiles. All-pairs shortest paths (Floyd–Warshall over about 30 ports) then give `d` for any pair. The next leg is drawn by concatenating the underlying hand-drawn paths.
2. **v2, learned times.** Record real sailing times between ports during play, and replace or calibrate tile lengths with measured seconds (averaged per pair). Also measure the stop overhead `s`.
   Phase 2 result (2026-09-25): all 18 western ports are connected, and the unit tests check symmetry, the triangle inequality, that no route is longer than a drawn path, and that each route's paths add up to its distance.
   - Known weakness: pairs with no drawn path between them get routed through other ports. For example, Neitiznot ↔ Jatizso comes out at 598 tiles via Rellekka, though the islands are neighbours. v2's learned times fix these.
3. **v3, only if needed.** A real sea pathfinder over water tiles, which needs the boat's size and turning behaviour.

`p₀` at sea: use the nearest point on any port path, or the distance to the nearest port plus that port's `d`.

### 3.1 Region

**Changed 2026-09-25:** routes are planned for **all ports** by default; every port is connected in the graph (tested). The western set below was a first-pass restriction; its setting was removed on 2026-09-27.


Western ports only:
- Void Knights' Outpost (Pest Control), Deepfin Point, Aldarin, Sunset Coast
- Civitas illa Fortis, Port Roberts, Port Tyras
- Prifddinas (needs Song of the Elves, so check it's unlocked)
- Land's End, Hosidius, Port Piscarilius, Piscatoris
- Rellekka, Lunar Isle, Jatizso, Neitiznot, Etceteria
- Red Rock (added 2026-09-25: newer, and well levelled)

The region is a set of ports, so it can be widened later. Tasks with any port outside it are shown as out of region, not planned. The user has Sailing level 92, so every port is unlocked except those behind Song of the Elves.

## 4. Hazards

Tabled. The user will flag routes that cross dangerous water; a flagged route-graph edge then gets a penalty, or is removed.

## 5. Open questions

1. Which varbit, or other game state, gives the boat's current dock or position? Find this in phase 1.
2. Reward-bag drop rates aren't on the wiki. Uniform weighting is the stand-in until better data turns up.

## 6. Reward data (checked against the wiki, 2026-09-25)

- **XP isn't in the game's task table**, so it has to come from outside. Port Tasks v1.6.0 (June 2026) hardcodes `TaskReward` for 240 entries (couriers plus bounties), keyed by DB row. The wiki's *Courier tasks* page lists **439** standard courier tasks, keyed by task ID. Many values differ: the smallest by 1–4 XP, some by 10% or more. **Plan:** a script generates `courier_tasks.json` (task ID → level, XP, ports, crate count) from the wiki page; the plugin joins it on `CourierTaskData.id` and falls back to `TaskReward`. The task ID mapping will be verified in game.
- **Learned XP:** when a task's final crate is delivered, the next Sailing XP gain (within 3 ticks) is recorded as that task's base XP and overrides the wiki value. It's kept in the profile under `routingLearnedXp`. A value more than 15% away from the wiki's is treated as boosted (keg of horizon's lure) and not learned. Tasks the wiki has no XP for are learned outright.
- **Boat location (confirmed in game):** `SAILING_LAST_PERSONAL_BOAT_BOARDED` gives the boat slot, and `SAILING_BOAT_n_PORT` gives that boat's dock ID. The `SailingDock` table maps the dock ID to a dock row, which is `PortLocation`'s key. Values that aren't docks, such as mooring points, resolve to "unknown".
- **Phase 1 in-game results (2026-09-25):** three tasks (IDs 339, 340, 328) joined correctly by task ID, with names, ports and crate counts matching. Port Tasks' XP was off by 2–5, and 0 for task 340, where the wiki says 9 605.
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

## 6a. Later ideas (not in scope yet)

Best combinations of offered tasks; swap suggestions when slots are full; an XP boost (keg) multiplier; splitting value into coins and items; XP/hour once times are learned; "against sweep" explanations; uncertainty markers. Remembering other boards' offers was dropped on the belief that boards change whenever tasks are handed in; loop mode (§2.4.2) now remembers loop boards, since boards change only at a reset.

## 7. Build phases (each ends with an in-game check by the user)

1. **Data.**
   - A script pulls the wiki into `courier_tasks.json` (task ID → level, XP, ports, crates) and `reward_bags.json` (size → shared drops; port → signature drops).
   - Join on `CourierTaskData.id`, and verify in game that task names match.
   - Identify the boat-position varbit.
2. **Route graph.** `PortPaths` becomes a weighted graph, then all-pairs shortest paths, filtered to the region. Unit tests on known pairs.
3. **Solver and next leg.** An exact search over precedence-respecting orders, with optional fixed end, from the boat's port. Draw only the next leg; re-plan on task or cargo varbit changes. Unit tests with hand-checked small cases.
4. **Board scoring.** The four metrics for every subset up to the free slots; the side list in the panel; badges and highlight on the board interface.
5. **Learned times (v2).** Record leg times and stop durations during play, and calibrate `d` and `s`.
