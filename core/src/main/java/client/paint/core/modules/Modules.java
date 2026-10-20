package client.paint.core.modules;

import client.paint.core.ModuleManager;
import client.paint.core.game.ModuleContext;

/**
 * The real module registry. Only modules that actually do something are registered. NOT implemented
 * (and therefore not listed in the GUI): combat automation, flight/speed-style movement, and cave/terrain visualisation modules.
 */
public final class Modules {
    private Modules() {}

    public static void registerAll(ModuleManager mm, ModuleContext ctx) {
        mm.register(new MovementModules.ToggleSprint(ctx));
        mm.register(new MovementModules.Freecam(ctx));
        mm.register(new MovementModules.Zoom(ctx));
        mm.register(new MovementModules.Fullbright(ctx));
        mm.register(new RenderModules.PlayerEsp(ctx));
        mm.register(new RenderModules.StorageEsp(ctx));
        mm.register(new RenderModules.Tracers(ctx));
        mm.register(new WorldModules.WaypointsModule(ctx));
        mm.register(new WorldModules.BaseFinder(ctx));
        mm.register(new RenderModules.SelfNametag());
        mm.register(new ExtraRenderModules.EntityEsp(ctx));
        mm.register(new ExtraRenderModules.BlockEsp(ctx));
        mm.register(new ExtraRenderModules.ProjectileTrajectory(ctx));
        mm.register(new ExtraRenderModules.ChunkBorders(ctx));
        mm.register(new UtilityModules.CustomCrosshair(ctx));
        mm.register(new UtilityModules.ToggleSneak(ctx));
        mm.register(new UtilityModules.ChatTweaks(ctx));
        mm.register(new UtilityModules.ScreenshotMode(ctx));
        mm.register(new UtilityModules.Exploration(ctx));
        mm.register(new UtilityModules.SessionStats(ctx));
        mm.register(new Appearance());
    }
}
