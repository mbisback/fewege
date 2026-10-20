package client.paint.mc;

import client.paint.core.ConfigStore;
import client.paint.core.ModuleManager;
import client.paint.core.ProfileManager;
import client.paint.core.Module;
import client.paint.core.game.EventBus;
import client.paint.core.game.EventBus.WorldEventType;
import client.paint.core.game.ModuleContext;
import client.paint.core.gui.ClickGuiView;
import client.paint.core.gui.EditorView;
import client.paint.core.gui.Gfx;
import client.paint.core.gui.HudRenderer;
import client.paint.core.gui.RasterGfx;
import client.paint.core.layout.LayoutModel;
import client.paint.core.modules.Appearance;
import client.paint.core.modules.Modules;
import client.paint.core.modules.WorldModules;
import client.paint.core.modules.RenderModules;
import client.paint.core.modules.UtilityModules;
import client.paint.core.gui.CrosshairRenderer;
import client.paint.core.gui.NotificationRenderer;
import client.paint.core.gui.SelfNametagRenderer;
import client.paint.core.nametag.NametagStyle;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import client.paint.core.render.Projection;
import client.paint.core.render.ScreenSpaceSink;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

/**
 * Client entrypoint: wires the tested core to Minecraft. Import paths marked VERIFY come from Fabric's current docs only
 * partly (HudElementRegistry, KeyMappingHelper, KeyMapping.Category are documented; the exact package names below may differ).
 */
public final class PaintClient implements ClientModInitializer {
    private static final ModuleManager MODULES = new ModuleManager();
    private static final EventBus BUS = new EventBus();
    private static McGame game;
    private static ModuleContext ctx;
    private static McHudData hudData;
    private static LayoutModel layout;
    private static ClickGuiView gui;
    private static ProfileManager profiles;
    private static Path dir;
    private static final Projection PROJECTION = new Projection();
    private static final Appearance APPEARANCE_FALLBACK = new Appearance();

    private static KeyMapping menuKey, overlayKey, panicKey;
    private static boolean wasDead; private static String lastDimension = "";
    private static int tickCount;
    private static long lastFrameNanos;
    private static final java.util.Map<Module, Boolean> keyState = new java.util.IdentityHashMap<>();

    static Appearance appearance() {
        Module m = MODULES.get("Appearance");
        return m instanceof Appearance a ? a : APPEARANCE_FALLBACK;
    }

    @Override
    public void onInitializeClient() {
        Minecraft mc = Minecraft.getInstance();
        dir = FabricLoader.getInstance().getConfigDir().resolve("paintclient");
        profiles = new ProfileManager(dir.resolve("profiles"));
        game = new McGame();
        BUS.setErrorSink(m -> System.err.println("[paintclient] " + m));
        MODULES.setErrorSink(m -> System.err.println("[paintclient] " + m));
        ctx = new ModuleContext(game, BUS, dir);
        game.setNotifications(ctx.notifications);
        Modules.registerAll(MODULES, ctx);
        ConfigStore.LoadResult r = ConfigStore.load(MODULES, dir.resolve("config.properties"));
        if (r.fileCorrupt) System.err.println("[paintclient] config was unreadable; moved to config.properties.corrupt and defaults loaded");

        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        layout = HudRenderer.defaultLayout(w, h);
        layout.load(dir.resolve("layout.properties"));
        hudData = new McHudData(game, MODULES);
        gui = new ClickGuiView(MODULES, layout);
        gui.onChanged = PaintClient::saveAll;

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("paintclient", "main"));   // VERIFY
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.paintclient.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_U, cat));
        overlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.paintclient.overlay", InputConstants.Type.KEYSYM, InputConstants.KEY_H, cat));
        panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.paintclient.panic", InputConstants.Type.KEYSYM, InputConstants.KEY_END, cat));

        ClientTickEvents.START_CLIENT_TICK.register(c -> { if (game.isSuppressed()) game.swallowClicks(); });
        ClientTickEvents.END_CLIENT_TICK.register(PaintClient::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, c) -> BUS.fireWorld(WorldEventType.WORLD_LOAD));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, c) -> { BUS.fireWorld(WorldEventType.DISCONNECT); saveAll(); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> saveAll());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("paintclient", "hud"), PaintClient::hud);   // VERIFY
        registerCrosshairAndChat();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    // ---------------------------------------------------------------- tick
    private static void tick(Minecraft mc) {
        if (!game.inWorld()) { wasDead = false; return; }
        boolean dead = game.playerDead();
        if (dead && !wasDead) BUS.fireWorld(WorldEventType.DEATH);
        wasDead = dead;
        String dim = game.dimension();
        if (!dim.equals(lastDimension)) { if (!lastDimension.isEmpty()) BUS.fireWorld(WorldEventType.DIMENSION_CHANGE); lastDimension = dim; }

        if (mc.gui.screen() == null) {
            while (menuKey.consumeClick()) openGui(mc);
            while (overlayKey.consumeClick()) openEditor(mc);
            if (panicKey.consumeClick()) { int n = MODULES.panic(); game.notifyPlayer("Panic: disabled " + n + " module(s)."); saveAll(); }
            pollModuleKeys(mc);
        }
        hudData.tick();
        if (++tickCount % 10 == 0) game.refreshStorage(10);
        BUS.fireTick();
    }

    /** Module hotkeys via direct key polling (they are user-assignable in the GUI, so they are not vanilla KeyMappings). */
    private static void pollModuleKeys(Minecraft mc) {
        for (Module m : MODULES.all()) {
            int k = m.getKeyCode();
            if (k < 0) continue;
            boolean down = InputConstants.isKeyDown(mc.getWindow(), k);       // VERIFY: Window vs long handle
            boolean was = keyState.getOrDefault(m, false);
            if (down && !was) { m.toggle(); saveAll(); }
            keyState.put(m, down);
        }
    }

    // ---------------------------------------------------------------- screens
    private static void openGui(Minecraft mc) {
        gui.select(gui.selected());
        mc.gui.setScreen(new PaintScreen(new PaintScreen.View() {
            public void render(Gfx g, int w, int h, long now) {
                Appearance a = appearance();
                gui.backgroundOpacity = Math.min(a.effectiveBackground(), 0.92);
                gui.animationSpeed = a.effectiveAnimation();
                gui.render(g, now);
            }
            public void mouseMoved(double x, double y) { gui.mouseMoved(x, y); }
            public boolean mouseDown(double x, double y, int b) { return gui.mouseDown(x, y, b); }
            public void mouseDragged(double x, double y) { gui.mouseDragged(x, y); }
            public void mouseUp() { gui.mouseUp(); }
            public void scroll(double x, double y, double a) { gui.scroll(x, y, a); }
            public boolean key(int code) { return gui.keyPressed(code); }
            public boolean typed(char c) { return gui.charTyped(c); }
            public boolean wantsKeyboard() { return gui.wantsKeyboard() || !gui.search().isEmpty(); }
            public void closed() { saveAll(); }
        }, true));
    }

    private static void openEditor(Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        layout.setScreenSize(w, h);
        EditorView ev = new EditorView(layout, hudData, w, h);
        ev.drawBackdrop = false;
        ev.onDone = () -> { saveAll(); mc.gui.setScreen(null); };
        mc.gui.setScreen(new PaintScreen(new PaintScreen.View() {
            public void render(Gfx g, int ww, int hh, long now) { ev.render(g); }
            public void mouseMoved(double x, double y) { }
            public boolean mouseDown(double x, double y, int b) { return ev.mouseDown(x, y, b); }
            public void mouseDragged(double x, double y) { ev.mouseDragged(x, y); }
            public void mouseUp() { ev.mouseUp(); }
            public void scroll(double x, double y, double a) { }
            public boolean key(int code) { return ev.keyPressed(code); }
            public boolean typed(char c) { return false; }
            public boolean wantsKeyboard() { return false; }
            public void closed() { saveAll(); }
        }, false));
    }

    // ---------------------------------------------------------------- HUD + ESP
    private static void hud(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {   // VERIFY types
        Minecraft mc = Minecraft.getInstance();
        if (!game.inWorld() || mc.options.hudHidden) return;
        if (mc.gui.screen() instanceof PaintScreen ps && !ps.isClickGui()) return;     // the editor draws the widgets itself
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float partial = dt.getGameTimeDeltaPartialTick(false);
        long nowN = System.nanoTime();
        double frameDt = lastFrameNanos == 0 ? 0.016 : (nowN - lastFrameNanos) / 1e9;
        lastFrameNanos = nowN;

        game.beginFrame(partial);
        BUS.fireFrame(frameDt);

        RasterGfx gfx = new RasterGfx(new McBackend(g, mc.font), w, h);
        boolean firstPerson = game.cameraActive() || mc.options.getCameraType().isFirstPerson();
        if (firstPerson) {
            double fov = mc.options.fov().get();
            if (game.cameraActive()) PROJECTION.set(game.camX, game.camY, game.camZ, game.camYaw, game.camPitch, fov, w, h);
            else {
                var eye = mc.player.getEyePosition(partial);
                PROJECTION.set(eye.x, eye.y, eye.z, mc.player.getViewYRot(partial), mc.player.getViewXRot(partial), fov, w, h);
            }
            BUS.fireRender(new ScreenSpaceSink(gfx, PROJECTION), frameDt);
        }
        if (ctx.flags.hideHud) return;                          // Screenshot Mode
        drawSelfNametag(gfx, mc, w, h, partial);
        if (!layout.widgets().isEmpty()) {
            layout.setScreenSize(w, h);
            HudRenderer.draw(gfx, layout, hudData, false, null);
        }
        NotificationRenderer.draw(gfx, ctx.notifications, System.currentTimeMillis(), w);
    }

    /** Styled self nametag, drawn in screen space: above your head in third person, top-centre in first person. Local only. */
    private static void drawSelfNametag(RasterGfx gfx, Minecraft mc, int w, int h, float partial) {
        if (!(MODULES.get("Nametags") instanceof RenderModules.SelfNametag nt) || mc.player == null) return;
        NametagStyle st = nt.current();
        if (st == null) return;
        boolean third = game.thirdPerson();
        if (!st.visibleIn(!third)) return;
        if (!third) { SelfNametagRenderer.draw(gfx, st, game.playerName(), w / 2.0, 44, 1.0); return; }
        double[] cam = game.cameraPose();
        PROJECTION.set(cam[0], cam[1], cam[2], (float) cam[3], (float) cam[4], mc.options.fov().get(), w, h);
        var p = mc.player.getPosition(partial);
        double[] out = new double[3];
        if (!PROJECTION.project(p.x, p.y + mc.player.getBbHeight() + 0.5, p.z, out)) return;
        double dist = Math.sqrt((p.x - cam[0]) * (p.x - cam[0]) + (p.y - cam[1]) * (p.y - cam[1]) + (p.z - cam[2]) * (p.z - cam[2]));
        SelfNametagRenderer.draw(gfx, st, game.playerName(), out[0], out[1], 4.0 / Math.max(1.0, dist));
    }

    /**
     * Crosshair replacement, chat hiding and chat hooks. VERIFY (26.3): HudElementRegistry#replaceElement, the HudElement
     * method name (render vs extractRenderState) and ClientReceiveMessageEvents signatures; each lambda only forwards to tested core code.
     */
    private static void registerCrosshairAndChat() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, dt) -> {
            if (MODULES.get("Crosshair") instanceof UtilityModules.CustomCrosshair ch && ch.isEnabled()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.hudHidden || !game.inWorld() || ctx.flags.hideHud) return;
                int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
                ch.draw(new RasterGfx(new McBackend(g, mc.font), w, h), w / 2.0, h / 2.0);
            } else original.extractRenderState(g, dt);                           // VERIFY: name of the HudElement method
        });
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, original -> (g, dt) -> {
            if (!ctx.flags.hideChat) original.extractRenderState(g, dt);         // VERIFY
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || chatText(message.getString()) != null);
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : stamp(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> chatText(message.getString()) != null);
    }

    private static String chatText(String s) {
        return MODULES.get("Chat Tweaks") instanceof UtilityModules.ChatTweaks ct ? ct.process(s) : s;
    }

    /** Prepends the timestamp prefix computed by the core, leaving the original message (and its formatting) intact. */
    private static Component stamp(Component message) {
        String raw = message.getString(), out = chatText(raw);
        if (out == null || out.equals(raw) || !out.endsWith(raw)) return message;
        return Component.literal(out.substring(0, out.length() - raw.length())).append(message);
    }

    // ---------------------------------------------------------------- persistence + commands
    static void saveAll() {
        try {
            ConfigStore.save(MODULES, dir.resolve("config.properties"));
            layout.save(dir.resolve("layout.properties"));
            if (MODULES.get("Base Finder") instanceof WorldModules.BaseFinder bf) bf.saveStore();
            if (MODULES.get("Exploration") instanceof UtilityModules.Exploration ex) ex.save();
        } catch (Exception e) { System.err.println("[paintclient] save failed: " + e); }
    }

    private static void say(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> c, String m) {
        c.getSource().sendFeedback(Component.literal("[Paint] " + m));
    }

    private static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> d) {
        var name = ClientCommands.argument("name", StringArgumentType.word());
        d.register(ClientCommands.literal("paint")
            .then(ClientCommands.literal("panic").executes(c -> { say(c, "Disabled " + MODULES.panic() + " module(s)."); saveAll(); return 1; }))
            .then(ClientCommands.literal("reset").executes(c -> { ProfileManager.resetAll(MODULES); saveAll(); say(c, "All modules disabled and reset to defaults."); return 1; }))
            .then(ClientCommands.literal("profile")
                .then(ClientCommands.literal("list").executes(c -> { say(c, "Profiles: " + profiles.list()); return 1; }))
                .then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name"); say(c, profiles.save(n, MODULES) ? "Saved profile " + n : "Invalid name (letters, digits, _ and - only)"); return 1; })))
                .then(ClientCommands.literal("load").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name"); say(c, profiles.load(n, MODULES) != null ? "Loaded profile " + n : "No such profile"); saveAll(); return 1; })))
                .then(ClientCommands.literal("delete").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name"); say(c, profiles.delete(n) ? "Deleted " + n : "No such profile"); return 1; }))))
            .then(ClientCommands.literal("wp")
                .then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    say(c, MODULES.get("Waypoints") instanceof WorldModules.WaypointsModule w && w.addHere(n) ? "Waypoint " + n + " saved here" : "Could not save waypoint"); return 1; })))
                .then(ClientCommands.literal("note").then(ClientCommands.argument("name", StringArgumentType.word())
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        String n = StringArgumentType.getString(c, "name"), t = StringArgumentType.getString(c, "text");
                        say(c, MODULES.get("Waypoints") instanceof WorldModules.WaypointsModule w && w.setNote(n, t) ? "Note saved on " + n : "No such waypoint here"); return 1; }))))
                .then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    say(c, MODULES.get("Waypoints") instanceof WorldModules.WaypointsModule w && w.remove(n) ? "Removed " + n : "No such waypoint here"); return 1; }))))
            .then(ClientCommands.literal("scan").then(ClientCommands.literal("clear").executes(c -> {
                if (MODULES.get("Base Finder") instanceof WorldModules.BaseFinder bf) bf.scheduler().clear();
                say(c, "Base Finder scan state cleared (history kept)."); return 1; }))));
    }
}
