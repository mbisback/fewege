package client.paint.core.game;

import client.paint.core.hud.NotificationCenter;

import java.nio.file.Path;

/** Shared services handed to every module. */
public final class ModuleContext {
    public final GameAccess game;
    public final EventBus events;
    public final Path dataDir;
    public final UiFlags flags = new UiFlags();
    public final NotificationCenter notifications = new NotificationCenter();

    public ModuleContext(GameAccess game, EventBus events, Path dataDir) {
        this.game = game; this.events = events; this.dataDir = dataDir;
    }
}
