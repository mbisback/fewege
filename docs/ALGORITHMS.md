# Algorithms (logic core)

## BaseFinder scoring (`BaseFinderScorer`)
Input: `ChunkEvidence` = per-indicator counts for one chunk + `completeness` (0..1, how much of the chunk and its
neighbours the client had actually received). The scanner may only inspect loaded terrain.

```
for each indicator t:
    if count(t) < t.minCount: skip                     # noise / false-positive suppression
    contrib = min(count(t), t.cap) * weight(t)         # no single indicator can dominate
    add contrib to bucket[t.kind]                      # NATURAL | ACTIVITY | STRUCTURE
natural = min(natural, naturalCap)                     # natural clues are always capped as a group
total   = (natural + activity + structure) * completeness
label   = structure >= structureThreshold ? Possible Structure
        : activity  >= activityThreshold  ? Possible Activity
        : Natural Indicator
confidence = LOW if label is Natural Indicator or data incomplete (completeness < minCompleteness)
           = HIGH if total >= highScore, MEDIUM if total >= mediumScore, else LOW
```

| Indicator | Data inspected (in-game scanner) | Natural? | Weight x cap (min count) |
|---|---|---|---|
| AMETHYST_LARGE | amethyst cluster/bud blocks in loaded sections | Yes (geodes) | 3.0 x 6 (3) |
| TALL_KELP | kelp column heights | Yes | 0.5 x 20 (6) |
| LONG_VINES | vine column lengths | Yes | 0.5 x 20 (6) |
| UNUSUAL_VEGETATION | biome-mismatched plants | Yes (weak) | 1.0 x 10 (4) |
| EXCAVATION | straight 1x2 corridors through solid rock | Rarely | 1.5 x 12 (3) |
| PLAYER_BLOCKS | non-natural block types | No | 0.5 x 60 (8) |
| CONSTRUCTION_PATTERN | flat floors / regular straight walls | Very rarely | 15 x 4 (1) |

Guarantees (unit tested): natural-only chunks never exceed LOW confidence or the natural cap; structure evidence
outranks any amount of natural evidence; nothing is ever labelled "confirmed".
Boundaries/incomplete data: score scales with completeness; below `minCompleteness` the result is flagged `incomplete`
and confidence is capped at LOW. Rescans never overwrite a stronger earlier hit (`CandidateStore.upsert`).
Honest limit: weights are starting points; real false-positive rates can only be tuned by scanning real worlds in-game.

## ESP rendering decision (`EspFilter`, `BoundedCache`)
Per target per frame: `distSq > max^2 -> skip`; then visibility mode (always / only obstructed / only visible);
opacity fades linearly to `minOpacity` at max distance. Caches are LRU-bounded and must be cleared on world unload,
dimension change and module disable. (Frustum culling and drawing are deferred - need Minecraft's camera/matrices.)

## Freecam (`FreecamCamera`)
Minecraft convention: yaw 0 = +Z, yaw -90 = +X, pitch + = down.
```
forward = (-sin yaw, cos yaw)      right = (-cos yaw, -sin yaw)
if |(f, s)| > 1: normalise          # no faster diagonals
pos += (forward*f + right*s) * speed * dt ; y += vertical * speed * dt
```
`begin` snapshots the real player view once; `stop(reason)` returns it exactly once (death, disconnect, dimension
change, module disable all funnel here), so double-stops are harmless. The camera never changes the player entity;
the in-game camera swap and input suppression are described in the "Freecam in game" section below (written, not yet verified in game).

## Self Nametag (`NametagStyle`)
Local and cosmetic only; other players cannot see it. Text = sanitised prefix + (custom text or real username) + suffix.
Sanitising removes control characters and legacy `§` format codes and bounds length. Gradient: per-channel linear
interpolation across characters. `reset()` restores vanilla-looking defaults; disabling the module must simply stop
applying the style (rendered by `SelfNametagRenderer` in screen space: per-character gradient, shadow/outline, background, scale clamped by distance; adapter hook unverified in game).

## Chunk scanner (`ChunkScanner`, `ScanScheduler`) - how the indicators are measured
Per column the scanner walks y from min to max (blocks classified by the adapter into AIR / SOLID_NATURAL / AMETHYST / KELP / VINE / PLAYER_PLACED / WATER / OTHER):
* Kelp / vine: vertical run lengths (>= 10 / >= 8) counted once per column.
* Player blocks: count; **construction pattern**: 4x4 aligned patches whose 16 cells are all PLAYER_PLACED at the same y.
* **Excavation**: an AIR cell with AIR above, natural solid floor/ceiling and natural solid walls on both sides along one axis is a corridor cell; runs of >= 6 cells along that axis count (wide caves never qualify).
* Unloaded neighbouring chunks are "unknown": never treated as wall or air. Completeness = (1 + loaded neighbours)/9 and scales the score.
Scheduler: nearest unscanned loaded chunk first, split into column slices, stops at the per-tick time budget, aborts a job if its chunk unloads, rescans after 60 s, prunes bookkeeping.

## Screen-space ESP (`Projection`, `ScreenSpaceSink`)
```
f = (-sin(yaw)cos(pitch), -sin(pitch), cos(yaw)cos(pitch));  r = (-cos(yaw), 0, -sin(yaw));  u = r x f
camera-space (x,y,z) = ((P-C).r, (P-C).u, (P-C).f);   screen = (W/2 + x/z*s, H/2 - y/z*s),  s = (H/2)/tan(fovY/2)
```
Box = 12 edges, each clipped to the near plane (z >= 0.05) before projection; frustum test uses the projected corner bounds and stays conservative when a box straddles the camera plane.

## Freecam in game (adapter)
Camera entity swap (no mixin): a client-only RemotePlayer becomes the camera entity; the real player's input object is replaced with an empty one and attack/use clicks are swallowed each tick; mouse look is read as the player's rotation delta, which is then reset. Stop paths (disable, death, disconnect, dimension change) all call the same cleanup.

## Trajectory (`Trajectory.simulate`)
Vanilla per-tick motion: `pos += vel; vel *= drag(0.99); vel.y -= gravity` (arrow/bow/crossbow/trident/potion 0.05, pearl/snowball/egg 0.03), start speed 3.0 x power (bow), 3.15 crossbow, 2.5 trident, 1.5 throwables, 0.5 potion. Bow power `p = min(1, (c^2 + 2c)/3)` for charge `c = ticks/20`; below 0.1 nothing is drawn. Each step asks `WorldView.segmentBlocked(from, to)` (a level raycast in game); the first blocked segment ends the path and is the impact point. Steps are capped (default 120) and the path also stops below y = -128.

## Block ESP (`BlockHighlighter`)
Picks the nearest loaded chunk that is unscanned (or older than 20 s), scans 16 columns per slice over the full height, records blocks whose `Kind` is wanted (ore/portal), stops at the time budget. Bounds: 512 hits per chunk, 400 chunks (LRU), chunks pruned when unloaded, cache cleared on dimension change / disable / setting change. Rendering walks chunk rings outward from the camera and stops at the box cap, culling by distance and frustum.

## Chunk Borders
Grid of `(2r+2)^2` vertical lines at chunk corners (span +-s around the camera) plus, if enabled, two rings of lines per 16-block layer within +-s: total `(2r+2)^2 + 2(2r+2)(2*(s/16)+1)`. Worst case (r=6, s=96) is under 2500 lines (tested).

## Target selection (`TargetSelector`)
Skips self, items and projectiles; angle to the view direction `acos(dot(f, v/|v|))` from the eye to the entity's centre; must be within max distance and cone; smallest angle wins.

## Session statistics (`StatsTracker`, `PerfMonitor`)
Speed = horizontal displacement over the last 1000 ms of tick samples / elapsed time. A tick that moves more than 100 blocks is a teleport and is not counted. A jump = ground -> air with `dy > 0.2`. PerfMonitor keeps the last N frame times (ignores <= 0 and > 5 s), reports average, max and fps.

## Exploration (`ExplorationHistory`)
Insertion-ordered set of `dimension cx cz` keys, oldest dropped past the cap, per-dimension counts kept in step; text file with a version header, malformed lines skipped on load, atomic write.
