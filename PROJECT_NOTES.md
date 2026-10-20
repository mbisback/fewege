# PROJECT NOTES

## Versions (verified 2026-10-19 from meta.fabricmc.net, maven.fabricmc.net and FabricMC/fabric-example-mod)
Minecraft 26.2, Loader 0.19.5, Fabric API 0.161.0+26.2, Loom `net.fabricmc.fabric-loom` 1.18-SNAPSHOT, Gradle 9.5.1, Java 25. 26.x is unobfuscated: no mappings, `implementation` not `modImplementation`.

## Architecture
- `core/` pure Java, no Minecraft types: framework, config, profiles, GUI views drawn through the `Gfx` interface (`RasterGfx` turns shapes into rectangle fills + text), layout model, HUD renderer, modules written against `GameAccess`/`WorldView`/`RenderSink`/`EventBus`, scanner, scoring, projection.
- `src/main/java/client/paint/mc/` thin adapter: `McBackend` (fill/text), `McGame` (GameAccess+WorldView), `McBlocks`, `McHudData`, `PaintScreen`, `PaintClient`.
- `tools/` preview-only (Java2D backend, reference catalog stand-ins, PNG renderer). Not part of the mod.
- No mixins: Freecam via camera-entity swap; ESP in screen space; zoom via FOV option; fullbright via night-vision effect.

## Design (from the references)
Colours were sampled from the images (see `core/.../gui/Theme.java`). ClickGUI is laid out in 1407x840 design units and fitted/centred in the window with a user scale; panels 214 wide at x=25+229n, y=93; bottom cards at y=459. HUD default layout matches Reference 2 at 1407x840; widgets scale their content to their resized box.

## Decisions
- Only working modules are registered. The reference's other module names exist only in `tools/ReferenceCatalog` for image comparison.
- Automated combat and flight-style modules were not written. Cave/terrain visualisation (X-Ray style) was also left out; Block ESP only marks ores/portals the server has already sent.
- New in the latest batch: modules share a `UiFlags` (hide HUD/chat) and `EventBus.renderSuppressed` through `ModuleContext`, so Screenshot Mode is one switch; notifications live in `ModuleContext.notifications` and are pushed by `GameAccess.notifyPlayer`.
- Optional `GameAccess`/`WorldView`/`HudData` methods have defaults so older fakes and previews keep working.
- Config: key=value UTF-8, `configVersion`, atomic writes, `.corrupt` quarantine, per-value skip.
- Draw ESP through the 2D HUD pass because the 26.x world-render pipeline is new and only documented via a long manual example.

## Known API limitations
See README "Expected first-build fixes". Screen method names/signatures, text drawing and a few entity/level accessors are unconfirmed for 26.2.
