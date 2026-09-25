# Courier route planning — Specification (draft v0.1)

A personal extension of Port Tasks (not for the Plugin Hub). It plans the order of stops for your active courier tasks, shows only the path to the next stop, and rates notice-board tasks by how well they fit the tasks you already have.

Decisions so far (2026-09-25): courier tasks only (no bounties); what to optimise is a setting; show only the next leg's path; built into this fork on top of Port Tasks' tracking and port data.

---

## 1. Inputs (all readable in game; Port Tasks already reads them)

| Symbol | Meaning | Source |
|---|---|---|
| `p₀` | Where the player is: at a port, or at sea | player world position; `PortLocation` coordinates |
| `T` | Active courier tasks, at most 5 | varbits `PORT_TASK_SLOT_{0..4}_ID` → `CourierTaskData` (from the game's `PortTask` table) |
| `aᵢ`, `bᵢ` | Task `i`'s cargo (pickup) port and delivery port | `CourierTaskData.cargoLocation` / `deliveryLocation` |
| `nᵢ` | Number of crates in task `i` | `CourierTaskData.cargoAmount` |
| `takenᵢ`, `deliveredᵢ` | Crates of task `i` picked up and delivered so far | varbits `PORT_TASK_SLOT_k_CARGO_TAKEN` / `_CARGO_DELIVERED` |
| `C` | Cargo hold capacity of the current boat | **to confirm** (§5, question 1) |
| `rᵢ` | Reward: XP (and coins?) for task `i` | `TaskReward`, `xpPerTile` |
| `d(u, v)` | Travel cost between ports `u` and `v` (and from `p₀`) | §3 |
| `s` | Fixed cost of one stop (dock, walk to the ledger, load or unload) | §3, **to measure** |

## 2. Problems

### 2.1 Route order (feature 1)

Find a sequence of stops, starting at `p₀`, that:
- **picks up** every crate not yet taken, at `aᵢ`, and **delivers** every crate taken but not yet delivered, at `bᵢ`;
- never delivers a crate before it has been picked up (precedence);
- never has more crates on board than `C` (capacity);

and minimises `Σ d(consecutive stops) + s × (number of distinct stops)`. Consecutive actions at the same port count as one stop. That is where shared ports pay off.

This is a single-vehicle **pickup-and-delivery problem with capacity** (an open route: no return to the start). It's NP-hard in general, but here there are at most 5 tasks, so at most 10 stop events. There are at most 10!/2⁵ ≈ 113 000 precedence-respecting orders, so an exact depth-first search with pruning finishes in milliseconds.

The objective setting only changes the cost used:
- **Least time:** cost = travel time + stop overhead.
- **XP/hour:** for a fixed task set the reward is fixed, so the best order is the same as for least time. The setting matters in §2.2.
- **Coins:** as XP/hour, using the coin reward.

### 2.2 Board selection (feature 2)

At a notice board offering tasks `O`, with `f` free slots, score each subset `S ⊆ O` with `|S| ≤ f` by its **marginal cost**

  `Δ(S) = best(T ∪ S) − best(T)`   (best = the §2.1 optimum)

and rank the subsets by `reward(S) / (Δ(S) + ε)` under the chosen objective (XP or coins per added minute), or by `Δ(S)` alone for least time. Tasks that lie along the current route have `Δ ≈ 0` and come out on top. Enumerating the subsets is cheap: a board shows few tasks and `f ≤ 5`.

### 2.3 Output

- The **next leg only**: draw the path from `p₀` to the first stop of the optimal route. This replaces Port Tasks' "all paths at once" while planning is on.
- Re-plan whenever a task is taken, completed or dropped, or crates are picked up or delivered (the varbits change), and on leaving a port.
- Board scores: presentation still to be decided (§5, question 5).

## 3. Travel cost `d(u, v)`

No available plugin does sea pathfinding. As of 2026-09-24, Shortest Path says in its code that it doesn't model sailing navigation. Duckblade's Sailing plugin draws hazard overlays but doesn't plan routes. Plan:

1. **v1, port-path graph.** Port Tasks' 164 hand-drawn `PortPaths` (sequences of relative moves) become weighted edges between ports, weighted by path length in tiles. All-pairs shortest paths (Floyd–Warshall over about 30 ports) then give `d` for any pair. The next leg is drawn by concatenating the underlying hand-drawn paths.
2. **v2, learned times.** Record real sailing times between ports during play, and replace or calibrate tile lengths with measured seconds (averaged per pair). Also measure the stop overhead `s`.
3. **v3, only if needed.** A real sea pathfinder over water tiles, which needs the boat's size and turning behaviour.

`p₀` at sea: use the nearest point on any port path, or the distance to the nearest port plus that port's `d`.

## 4. Hazards

Sharks, krakens, lightning clouds and rapids. If their areas are fixed, add extra cost to port-path edges that cross them: a penalty, or remove the edge entirely below a boat or level threshold. If they move, only live avoidance is possible, which is out of scope for v1. **To confirm** (§5, question 3).

## 5. Open questions

1. **Cargo hold:** how many crates does your boat hold, and does it ever force a task to be split across trips? If it never binds, capacity can be dropped from the model.
2. **Stop overhead:** roughly how long is a typical port stop (dock, walk to the ledger, load or unload, back aboard)? v2 can measure it, but a first guess sets the weight of "fewer stops" against "shorter sailing".
3. **Hazards:** are sharks, krakens and similar in fixed areas, or do they move? Which ones actually cost you time or boat HP?
4. **Other travel:** do you ever teleport, charter or walk between legs instead of sailing your boat? If so, those are extra edges in the graph.
5. **Board display:** how should board scores appear: a badge or number on each offered task, or highlighting the best pick?
6. **Rewards:** are `TaskReward` values the XP per task? Do you also get coins, and where would they come from?
