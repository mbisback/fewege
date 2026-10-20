# PROGRESS
Last updated: 2026-10-20

## Headline
- **Tested:** all Minecraft-independent logic (474/474 checks, `./run-tests.sh`).
- **Written, never compiled:** the Minecraft adapter in `src/main/java/client/paint/mc` (the build sandbox could not reach Minecraft/Fabric downloads). A syntax-only javac pass over it reports only unresolved Minecraft symbols (no syntax errors).
- **Not built:** no jar exists. See README.md for the build steps and the expected first-build fixes (all marked `VERIFY`).

## Verified build configuration (from FabricMC/fabric-example-mod master + meta.fabricmc.net, 2026-10-19)
Minecraft 26.2 (stable) | Fabric Loader 0.19.5 | Fabric API 0.161.0+26.2 | Loom plugin `net.fabricmc.fabric-loom` 1.18-SNAPSHOT | Gradle 9.5.1 | Java 25 | no mappings (unobfuscated).
The sandbox has Java 21 and no network to Maven, so nothing could be built here.

## Results (actual)
| Check | Result |
|---|---|
| Core compile (`javac -Xlint:all`) | 0 errors, 0 warnings |
| Unit/behaviour tests | 474 passed, 0 failed (framework 33, layout/basefinder/freecam/nametag/render 75, hud/waypoints/chat/profiles 42, modules+scanner+scheduler+registry 86, ClickGUI/editor/viewport 51, projection+ESP sink 30, **new features 157**) |
| Previews | `docs/preview/`: ClickGUI, editor, ESP scene, all HUD widgets, features scene (trajectory, entity/ore ESP, chunk borders, crosshair, nametag, notification) rendered by the real core code through a Java2D backend |
| Adapter | syntax-only javac: only unresolved-Minecraft-symbol errors |
| Gradle build / jar / launch / in-game behaviour | NOT RUN |

## Done (tested)
Module lifecycle + panic, validated settings, keybind conflicts, versioned atomic config with corruption recovery, profiles + import/export + reset, ClickGUI (panels, toggles, risk badges, search, favourites, recents, tooltips, drag/reset panels, scroll, settings card, keybind capture, overlay toggles with scrolling for 11 widgets, risk legend), layout editor, **12 HUD widgets** (watermark, FPS/ping, coordinates, potions, armour, minimap, module list, keystrokes, + Target, Item counters, Session stats, Waypoint compass), BaseFinder, Freecam logic, Self Nametag style **and renderer**, ESP modules + screen-space projection/drawing, Toggle Sprint, Zoom, Fullbright, Waypoints (**with notes**), Appearance.

### Added in the latest batch (all with tests)
Entity ESP (hostile/passive/items with item names/projectiles, health labels), Block ESP (diamond, ancient debris, emerald, gold, iron, other ores, Nether portals; own bounded incremental scanner), Trajectory (bow draw power curve, crossbow, trident, pearl, snowball, egg, potion), Chunk Borders (+16-block layers), Toggle Sneak, Screenshot Mode (suppresses overlays/HUD/chat), Chat Tweaks (timestamps 12/24 h, hide keywords), Crosshair (size/gap/thickness/colour/dot/T-style/attack-cooldown bar), Exploration history (bounded, persisted), Session Stats + frame-time monitor, notification toasts, target selection, waypoint notes (`explored.txt`, 6-column `waypoints.tsv`; old 5-column files still load).

## Written but unverified in game
All of `client.paint.mc.*` (including the new hooks: crosshair/chat replacement, self-nametag drawing, held-projectile detection, inventory counters, entity health, camera pose), `fabric.mod.json`, `build.gradle`, lang file.

## Not implemented (on purpose or not yet)
Combat automation (Kill Aura, Auto Clicker, Trigger Bot, Aim Assist, Auto Crystal, ...), flight/speed/step/no-fall movement, cave/terrain visualisation (X-Ray), hit feedback, combat statistics beyond CPS, UNUSUAL_VEGETATION indicator, TTF font resource, Gradle wrapper jar (generate with `gradle wrapper`).

## Exact next steps
1. JDK 25 + internet: `gradle wrapper --gradle-version 9.5.1 && ./gradlew build`.
2. Fix the `VERIFY` lines the compiler flags (README lists them). Re-run `./run-tests.sh` (must stay 474/474).
3. `./gradlew runClient`: check menu (U), editor (H), a module toggle, Freecam start/stop/death/dimension change, Base Finder, ESP alignment, Block ESP on real ores, Trajectory with a pearl/bow, custom crosshair, chat timestamps, config save/restore.
4. Record real in-game results here.
