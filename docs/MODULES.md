# Module reference (registered modules only)

| Module | Category | Risk tag | What it does | Update | Cost | Limits |
|---|---|---|---|---|---|---|
| Toggle Sprint | Movement | SAFE | Holds sprint while moving forward (optionally sideways). Never while sneaking or when hunger forbids. | per tick | negligible | Does not bypass game rules |
| Freecam | Render | LOW | Independent camera (client-only camera entity); real player input is suppressed so the player does not move. Ends on death, disconnect, dimension change, disable. | per frame | low | Needs the in-game adapter verified; third-person is forced off while active |
| Zoom | Render | SAFE | Narrows FOV by a factor. | per frame | negligible | Vanilla FOV range 30..110 |
| Fullbright | Render | LOW | Client-side night-vision effect. | per frame | negligible | Removed on disable only if this module added it |
| Player ESP | Render | LOW | Screen-space box + name/distance for other players; range, visibility mode, colour, thickness, fill. | per frame | O(players) | First person/Freecam only |
| Storage ESP | Render | LOW | Boxes for chests, trapped chests, ender chests, shulkers, barrels, spawners in loaded chunks. | per frame, list refreshed every 10 ticks | O(block entities in 21x21 chunks) | Only loaded chunks |
| Tracers | Render | LOW | Lines from the crosshair to players (optional hostiles). | per frame | O(entities) | Targets behind you are skipped |
| Nametags (Self Nametag) | Render | SAFE | Local-only styling of your own name: text, gradient, bold/shadow/outline, background, padding, scale, symbols, 1st/3rd person. | on render | negligible | Cosmetic, others never see it. Drawn in screen space above your character (third person) or top-centre (first person, opt-in); adapter hook unverified in game |
| Waypoints | Render | SAFE | Named locations per dimension with distance/arrow labels and optional notes; persisted; feeds the Waypoint compass widget. | per frame | O(waypoints) | Add/remove/note via `/paint wp` |
| Base Finder | Base Hunt | LOW | Time-budgeted chunk scanner + weighted scoring + persistent candidates. See ALGORITHMS.md. | per tick (budget, default 1 ms) | bounded by budget | Clues, not proof; no biome data |
| Entity ESP | Render | LOW | Boxes for hostile mobs, passive mobs, dropped items (labelled with the item name and count) and projectiles; name/health/distance labels; per-kind colours; range fade. | per frame | O(entities) | Health only if the server sends it; first person/Freecam only |
| Block ESP | Render | LOW | Outlines diamond / ancient debris / emerald / gold / iron / other ores and Nether portals. Own incremental scanner (default 1 ms/tick), max 512 hits per chunk, 400 chunks kept (LRU), draw cap 300 boxes, nearest first, rescan every 20 s. | per tick + per frame | bounded by budget and caps | Only blocks the server has sent (anti-xray can fake ores) |
| Trajectory | Render | SAFE | Predicted path and impact marker for a drawn bow, loaded crossbow, trident (after 10 ticks), ender pearl, snowball, egg, potion. | per frame | up to 300 steps | Estimate: ignores entity hits and server corrections |
| Chunk Borders | Render | SAFE | Chunk edge lines and 16-block layers around the camera, current chunk highlighted. | per frame | <= (2r+2)^2 + 2(2r+2)(2s/16+1) lines | Radius 0..6 |
| Crosshair | Render | SAFE | Replaces the vanilla crosshair: size, gap, thickness, colour, dot, T-style, attack-cooldown bar. | per frame | negligible | Hook replaces the vanilla HUD element (unverified API names) |
| Toggle Sneak | Movement | SAFE | Holds sneak while enabled; releases on disable. | per tick | negligible | Client-side input only |
| Chat Tweaks | Misc | SAFE | Timestamps (12/24 h) and hide-by-keyword for incoming chat. | per message | negligible | Display only; hook uses Fabric message events (unverified signatures) |
| Screenshot Mode | Misc | SAFE | Suppresses all world overlays; optionally hides HUD widgets and chat. Restored on disable and by panic. | per tick | none | Vanilla F1 is separate |
| Exploration | Base Hunt | SAFE | Remembers visited chunks per dimension (200k cap), saved to `explored.txt`. | per tick | O(1) | Counts chunks you stood in, not chunks you saw |
| Session Stats | Misc | SAFE | Session time, speed (1 s window), distance (teleports ignored), jumps, biggest fall, average/max frame time; feeds the Session stats widget. | per tick/frame | negligible | Must be enabled for the widget to show data |
| Appearance | Misc | SAFE | GUI scale, background opacity, animation speed. Not affected by panic. | on change | none | |

## HUD widgets (Overlay widgets card / H editor)
Watermark, FPS and ping, Coordinates, Potion effects, Armour, Minimap, Module list, Keystrokes (original eight), plus **Target** (aimed entity: name, health bar, distance; 64 blocks, 8 degree cone), **Item counters** (totems, golden apples, pearls, arrows, crystals, free slots), **Session stats**, **Waypoint compass** (nearest waypoint, distance, turn arrow). The four new ones are off by default and do not change the reference default layout.

Panic (`End` or `/paint panic`) disables every module except Appearance and runs all cleanups.
Every module: keybind (with conflict detection), validated settings, reset (middle-click the module title in the settings card), persistence.
