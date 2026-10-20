# Paint Client (Fabric, Minecraft 26.2)

A client-side utility mod: ClickGUI, HUD layout editor, ESP (players, entities, storage, ores, portals), projectile trajectories, chunk borders, Base Finder, Freecam, Self Nametag, custom crosshair, waypoints, chat tweaks, screenshot mode and more.
It loads through the normal Fabric mod loader (a standard mod jar in `mods/`).

## STATUS - read this first
| Part | State |
|---|---|
| Core logic (modules, settings, config, GUI, layout editor, HUD, scanner, scoring, freecam maths, projection, ESP drawing) | **Compiled with javac and tested: 474/474 checks pass** (`./run-tests.sh`, Java 17+) |
| ClickGUI / editor / HUD / ESP **appearance** | Rendered to PNG (see `docs/preview/`). The ClickGUI and the original widgets were compared to your two reference images before the latest batch (4.4% / 3.4% mean pixel difference, mostly fonts). The editor now also shows the four new widgets, which the reference does not have, so it is no longer pixel-comparable |
| Minecraft adapter (`src/main/java/client/paint/mc/*`) | **Written but NEVER compiled or run.** Expect a handful of compile errors on the first build (see below) |
| Build files | Configured for Fabric 26.2 using official example-mod versions |
| The `.jar` | **Not built.** You build it with the steps below |

Nothing here has been run inside Minecraft. "Works in game" is unverified until you test it.

## Build the jar
Requirements: **JDK 25** (Minecraft 26.2 needs it) and internet access.
```
cd paint-client
./gradlew build
```
The jar is `build/libs/paint-client-0.1.0.jar` (not the `-sources` one). To try it without installing: `./gradlew runClient`.
Verify it contains classes: `jar tf build/libs/paint-client-0.1.0.jar | grep PaintClient`.

Run the logic tests any time (no Minecraft needed, JDK 17+): `./run-tests.sh` -> expect `TOTAL: 474 passed, 0 failed`.

## Expected first-build fixes
Minecraft 26.x changed many rendering and input names. Only these lines touch such APIs and are tagged `VERIFY`:
* `McBackend` - text drawing (`drawString`, `pose().pushMatrix/translate/scale/popMatrix`).
* `PaintScreen` - `Screen` method names and parameter types (`extractRenderState`, `mouseClicked(MouseButtonEvent, boolean)`, `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)`).
* `PaintClient` - `HudElementRegistry.attachElementBefore`, `KeyMapping.Category.register`, `Identifier`, `InputConstants.isKeyDown(Window, int)`.
* `McGame` - `RemotePlayer` constructor, `ClientInput` constructor, `yRotO/xRotO`, `entitiesForRendering`, `setOldPosAndRot`, chunk position accessors, plus (new) `getAttackStrengthScale`, `fallDistance`, `onGround()`, `getTicksUsingItem`, `DataComponents.CHARGED_PROJECTILES`, `Camera` position/rotation accessors (`cameraPose`), item/identifier `getPath`.
* `PaintClient` (new) - `HudElementRegistry.replaceElement` for the crosshair and chat, the `HudElement` method name (`extractRenderState` vs `render`), `ClientReceiveMessageEvents.ALLOW/MODIFY_GAME/CHAT` signatures (`registerCrosshairAndChat`). If the replace-element calls fight you, delete that one method: everything else still works, you just lose the custom crosshair, chat hiding and chat timestamps.
* `McBlocks` / `McHudData` - `getMinY/getMaxY`, `getFps`, `getLatency`, `MapColor.col`.
Each failing line points at one Minecraft call; fix the name from your IDE's autocomplete or docs.fabricmc.net. The core is untouched by such fixes.
If a Fabric API package name differs, search for the class in your IDE (e.g. `HudElementRegistry`, `KeyMappingHelper`).

## Install, troubleshooting, removal
* Install: put `paint-client-0.1.0.jar` and **Fabric API 0.161.0+26.2** in `.minecraft/mods`, launch the Fabric 26.2 profile.
* Open the menu with **U**, the HUD editor with **H**, panic (disables everything) with **End**. All three are rebindable in Minecraft's Controls screen.
* Chat commands: `/paint panic`, `/paint reset`, `/paint profile save|load|list|delete <name>`, `/paint wp add|remove <name>`, `/paint wp note <name> <text>`, `/paint scan clear`.
* Config and data live in `.minecraft/config/paintclient/` (`config.properties`, `layout.properties`, `candidates.tsv`, `waypoints.tsv`, `explored.txt`, `profiles/`). A corrupt config is renamed `*.corrupt` and defaults are used.
* Game crashes at startup: remove the jar, check `logs/latest.log` for `paintclient`. Mod missing: wrong Minecraft/Java version or no Fabric API.
* Removal: delete the jar (and optionally `config/paintclient`).

## Known limitations (by design or version)
* **ESP is drawn in screen space** (the 2D HUD layer) by projecting world points. It is always "through walls", is only drawn in first person or Freecam, and its FOV comes from the FOV option (sprint/speed FOV effects are not included, so outlines can drift slightly while sprinting).
* **Zoom** is limited to FOV 30..110 (vanilla range), about 2.3x from FOV 70.
* **Fullbright** uses a client-side night-vision effect (no gamma hack).
* **Text** uses Minecraft's built-in font, not the reference's typeface, so text differs from the screenshots. Shipping a TTF font resource would close this.
* **Not implemented** (no placeholder modules are listed): combat automation (Kill Aura, Auto Clicker, Trigger Bot, Aim Assist, Auto Crystal...), flight/speed/step/no-fall style movement, cave/terrain visualisation (X-Ray style), hit feedback, combat statistics beyond CPS, a minimap beyond the surface-colour one, a TTF font resource. These were left out on purpose (automated combat is out of scope; the reference's module names exist only in `tools/ReferenceCatalog` to compare screenshots).
* **Trajectory** is an estimate: vanilla per-tick motion for bow/crossbow/trident/pearl/snowball/egg/potion, stopping at the first block. It ignores entity hits and anything the server decides differently, and it does not model wind charges or Riptide.
* **Block ESP** only shows blocks the server has already sent you (anti-xray plugins on a server can send fake ores). Scanning is time-budgeted (default 1 ms/tick), so large radii fill in gradually.
* **Entity health** is only as accurate as what the server sends (many servers hide it).
* **Self nametag** is drawn in screen space above your character in third person (and at the top centre in first person if you enable that); it is local only.
* **Screenshot Mode** hides the mod's overlays, widgets and optionally chat; vanilla F1 is unaffected.
* Base Finder: UNUSUAL_VEGETATION is not measured (needs biome data). Villages, mineshafts and ruined portals contain player-style blocks and will score as clues.
