package client.paint.core.modules;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.Risk;
import client.paint.core.Setting;
import client.paint.core.chat.ChatTools;
import client.paint.core.game.EventBus.WorldEventType;
import client.paint.core.game.GameAccess;
import client.paint.core.game.ModuleContext;
import client.paint.core.gui.CrosshairRenderer;
import client.paint.core.gui.Gfx;
import client.paint.core.hud.PerfMonitor;
import client.paint.core.hud.StatsTracker;
import client.paint.core.world.ExplorationHistory;

import java.io.IOException;
import java.time.ZoneId;

/** Toggle Sneak, Screenshot Mode, Chat Tweaks, Custom Crosshair, Exploration History, Session Stats. */
public final class UtilityModules {
    private UtilityModules() {}

    /** Press-to-toggle sneak: the module being on keeps you sneaking; turning it off releases it. */
    public static final class ToggleSneak extends Module {
        private final ModuleContext ctx;

        public ToggleSneak(ModuleContext ctx) {
            super("Toggle Sneak", "Keeps you sneaking without holding the key. Client-side input only; turning it off releases sneak.",
                    Category.MOVEMENT, Risk.SAFE);
            this.ctx = ctx;
        }

        @Override protected void onEnable() {
            addCleanup(() -> ctx.game.holdSneak(false));
            addCleanup(ctx.events.onTick(() -> { if (ctx.game.inWorld() && !ctx.game.playerDead()) ctx.game.holdSneak(true); }));
        }
    }

    /** Hides every world overlay and optionally the HUD and chat for clean screenshots. Restores on disable. */
    public static final class ScreenshotMode extends Module {
        private final ModuleContext ctx;
        public final Setting.Bool hideHud = add(new Setting.Bool("Hide HUD widgets", true));
        public final Setting.Bool hideChat = add(new Setting.Bool("Hide chat", false));

        public ScreenshotMode(ModuleContext ctx) {
            super("Screenshot Mode", "Hides ESP, outlines, HUD widgets and (optionally) chat so screenshots are clean. Turn off to restore everything.",
                    Category.MISC, Risk.SAFE);
            this.ctx = ctx;
        }

        @Override protected void onEnable() {
            addCleanup(() -> { ctx.events.renderSuppressed = false; ctx.flags.reset(); });
            ctx.events.renderSuppressed = true;
            addCleanup(ctx.events.onTick(() -> { ctx.flags.hideHud = hideHud.get(); ctx.flags.hideChat = hideChat.get(); }));
            ctx.flags.hideHud = hideHud.get();
            ctx.flags.hideChat = hideChat.get();
        }
    }

    /** Adds timestamps to incoming chat and hides messages containing chosen words. Display-only. */
    public static final class ChatTweaks extends Module {
        private final ModuleContext ctx;
        private final ChatTools.Filter filter = new ChatTools.Filter();
        public final Setting.Bool timestamps = add(new Setting.Bool("Timestamps", true));
        public final Setting.Bool hour24 = add(new Setting.Bool("24-hour clock", true));
        public final Setting.Text hideWords = add(new Setting.Text("Hide messages containing (comma separated)", "", 120));
        private ZoneId zone = ZoneId.systemDefault();

        public ChatTweaks(ModuleContext ctx) {
            super("Chat Tweaks", "Adds timestamps to chat and hides messages containing words you list. Changes only what you see.",
                    Category.MISC, Risk.SAFE);
            this.ctx = ctx;
        }

        public void setZone(ZoneId z) { zone = z; }

        /** @return the text to display, or null if the message should be hidden. Pass-through while disabled. */
        public String process(String message) {
            if (!isEnabled() || message == null) return message;
            filter.clear();
            for (String w : hideWords.get().split(",")) filter.add(w);
            if (filter.shouldHide(message)) return null;
            return timestamps.get() ? ChatTools.timestamp(ctx.game.nowMs(), zone, hour24.get()) + message : message;
        }
    }

    /** Replaces the vanilla crosshair with an adjustable one (the adapter calls {@link #draw}). */
    public static final class CustomCrosshair extends Module {
        private final ModuleContext ctx;
        public final Setting.Num size = add(new Setting.Num("Size", 6, 2, 20, 1));
        public final Setting.Num gap = add(new Setting.Num("Gap", 3, 0, 12, 1));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 2, 1, 6, 1));
        public final Setting.Color color = add(new Setting.Color("Colour", 0xFFFFFFFF));
        public final Setting.Bool dot = add(new Setting.Bool("Centre dot", false));
        public final Setting.Bool tStyle = add(new Setting.Bool("T-style (no top arm)", false));
        public final Setting.Bool cooldown = add(new Setting.Bool("Attack cooldown bar", true));

        public CustomCrosshair(ModuleContext ctx) {
            super("Crosshair", "Custom crosshair: size, gap, thickness, colour, dot, T-style and an attack-cooldown bar.", Category.RENDER, Risk.SAFE);
            this.ctx = ctx;
        }

        public void draw(Gfx g, double cx, double cy) {
            if (!isEnabled()) return;
            CrosshairRenderer.draw(g, cx, cy, size.get(), gap.get(), thickness.get(), color.argb(), dot.get(), tStyle.get(),
                    cooldown.get(), ctx.game.attackCooldown());
        }
    }

    /** Records which chunks you have been in, per dimension, and saves them. */
    public static final class Exploration extends Module {
        private final ModuleContext ctx;
        private final ExplorationHistory history = new ExplorationHistory(200_000);
        private final java.nio.file.Path file;
        private int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        private String lastDim = "";
        public final Setting.Bool announce = add(new Setting.Bool("Notify on new chunks every 100", false));

        public Exploration(ModuleContext ctx) {
            super("Exploration", "Remembers every chunk you have visited (per dimension) so you can see how much ground you have covered.",
                    Category.BASE_HUNT, Risk.SAFE);
            this.ctx = ctx;
            this.file = ctx.dataDir.resolve("explored.txt");
            history.load(file);
        }

        public ExplorationHistory history() { return history; }

        @Override protected void onEnable() {
            addCleanup(ctx.events.onTick(this::tick));
            addCleanup(ctx.events.onWorldEvent(t -> { if (t == WorldEventType.DISCONNECT) save(); }));
            addCleanup(this::save);
        }

        private void tick() {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return;
            int cx = Math.floorDiv((int) Math.floor(g.posX()), 16), cz = Math.floorDiv((int) Math.floor(g.posZ()), 16);
            String dim = g.dimension();
            if (cx == lastCx && cz == lastCz && dim.equals(lastDim)) return;
            lastCx = cx; lastCz = cz; lastDim = dim;
            if (history.visit(dim, cx, cz) && announce.get() && history.newThisSession() % 100 == 0) {
                g.notifyPlayer("Exploration: " + history.newThisSession() + " new chunks this session");
            }
        }

        public void save() { try { history.save(file); } catch (IOException ignored) { /* non-fatal */ } }
    }

    /** Session time, speed, jumps, distance, biggest fall and frame-time statistics for the HUD widgets. */
    public static final class SessionStats extends Module {
        private final ModuleContext ctx;
        private final StatsTracker stats = new StatsTracker();
        private final PerfMonitor perf = new PerfMonitor(120);

        public SessionStats(ModuleContext ctx) {
            super("Session Stats", "Tracks session time, speed, jumps, distance and frame times for the Stats HUD widget.", Category.MISC, Risk.SAFE);
            this.ctx = ctx;
        }

        public StatsTracker stats() { return stats; }
        public PerfMonitor perf() { return perf; }

        @Override protected void onEnable() {
            stats.reset(ctx.game.nowMs());
            addCleanup(ctx.events.onTick(() -> {
                GameAccess g = ctx.game;
                if (g.inWorld()) stats.update(g.nowMs(), g.posX(), g.posY(), g.posZ(), g.onGround(), g.fallDistance());
            }));
            addCleanup(ctx.events.onFrame(perf::frame));
            addCleanup(ctx.events.onWorldEvent(t -> { if (t == WorldEventType.DIMENSION_CHANGE) stats.reset(ctx.game.nowMs()); }));
        }
    }
}
