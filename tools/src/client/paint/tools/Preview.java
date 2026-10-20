package client.paint.tools;

import client.paint.core.ModuleManager;
import client.paint.core.gui.ClickGuiView;
import client.paint.core.gui.HudData;
import client.paint.core.gui.HudRenderer;
import client.paint.core.gui.RasterGfx;
import client.paint.core.gui.Viewport;
import client.paint.core.layout.LayoutModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Renders the ClickGUI and HUD/layout editor to PNGs at the reference size, for fidelity checks. */
public final class Preview {
    /** Fake data matching Reference 2. */
    public static HudData demoData() {
        return new HudData() {
            public int fps() { return 144; }
            public int ping() { return 38; }
            public double x() { return -1204; }
            public double y() { return 64; }
            public double z() { return 3877; }
            public float yaw() { return 180f; }
            public boolean inNether() { return false; }
            public List<Effect> effects() { return List.of(new Effect("Speed II", 0x7CCBFF, 20 * 102), new Effect("Strength I", 0xFFA6A6, 20 * 190), new Effect("Fire Resistance", 0xFFB86B, 20 * 480)); }
            public List<ArmourPiece> armour() { return List.of(new ArmourPiece("Helmet", 0x7CCBFF, 92), new ArmourPiece("Chestplate", 0x7CCBFF, 74), new ArmourPiece("Leggings", 0x7CCBFF, 82), new ArmourPiece("Boots", 0x7CCBFF, 58)); }
            public List<ModuleDot> activeModules() { return List.of(new ModuleDot("Auto Totem", 0xFF6E5A), new ModuleDot("Toggle Sprint", 0xFBB63E), new ModuleDot("Tracers", 0x40D9E3)); }
            public boolean keyDown(Key k) { return k == Key.W; }
            public int cpsLeft() { return 9; }
            public int cpsRight() { return 4; }
            @Override public Target target() { return new Target("Zombie", 14, 20, 7.4); }
            @Override public List<Counter> counters() { return List.of(new Counter("Totems", 3), new Counter("Golden apples", 12), new Counter("Ender pearls", 16), new Counter("Arrows", 64), new Counter("End crystals", 0), new Counter("Free slots", 9)); }
            @Override public List<StatLine> stats() { return List.of(new StatLine("Session", "0:42:17"), new StatLine("Speed", "4.3 b/s"), new StatLine("Distance", "6,820 m"), new StatLine("Jumps", "211"), new StatLine("Frame ms", "6.9 / 21.4")); }
            @Override public WaypointInfo nearestWaypoint() { return new WaypointInfo("Home", 842, 35); }
            public int minimapColor(int dx, int dz) {
                if (dz == 0 || dz == -1 && false) return 0xFF77777A;
                if (dx <= -3 && dz <= -4 && dx >= -7 && dz >= -7) return 0xFF2F6F8F;
                if (dx >= 2 && dz >= 3 && dx <= 7 && dz <= 6) return 0xFF7A6A3A;
                return 0xFF2D5A32;
            }
        };
    }

    public static BufferedImage renderGui(ModuleManager mm, LayoutModel layout, int w, int h, double userScale) {
        Java2DBackend be = new Java2DBackend(w, h);
        RasterGfx g = new RasterGfx(be, w, h);
        g.setViewport(Viewport.fit(w, h, ClickGuiView.DW, ClickGuiView.DH, userScale));
        ClickGuiView v = new ClickGuiView(mm, layout);
        v.render(g, 1000);
        return be.img;
    }

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "preview";
        new File(out).mkdirs();
        ModuleManager mm = ReferenceCatalog.build();
        LayoutModel layout = HudRenderer.defaultLayout(1407, 840);
        // Reference 1 shows Armour/Keystrokes/Module list off and the other four on.
        ImageIO.write(renderGui(mm, layout, 1407, 840, 1.0), "png", new File(out, "gui_1407x840.png"));

        Java2DBackend be = new Java2DBackend(1407, 840);
        RasterGfx g = new RasterGfx(be, 1407, 840);
        new client.paint.core.gui.EditorView(layout, demoData(), 1407, 840).render(g);
        ImageIO.write(be.img, "png", new File(out, "editor_1407x840.png"));
        espScene(out);
        hudAll(out);
        featuresScene(out);
        realGui(out);
        System.out.println("wrote " + out);
    }

    /** A synthetic first-person scene showing ESP, tracers, labels and a Base Finder outline drawn by the screen-space sink. */
    static void espScene(String out) throws Exception {
        int w = 1280, h = 720;
        Java2DBackend be = new Java2DBackend(w, h);
        RasterGfx g = new RasterGfx(be, w, h);
        g.verticalGradient(0, 0, w, h / 2.0, 0xFF6FA8DC, 0xFFB6D7F0);
        g.verticalGradient(0, h / 2.0, w, h / 2.0, 0xFF5B8C3A, 0xFF3B5F26);
        client.paint.core.render.Projection p = new client.paint.core.render.Projection();
        p.set(0, 65.62, 0, 0f, 8f, 70, w, h);
        client.paint.core.render.ScreenSpaceSink s = new client.paint.core.render.ScreenSpaceSink(g, p);
        s.box(-10, 64, 14, 6, 80, 30, 0xE6FF6E5A, 0x1AFF6E5A, 2f, true);
        s.label("Possible Structure 62", -2, 66, 22, 0xFFFFFFFF, 1f);
        s.box(-4.3, 64, 12, -3.7, 65.8, 12.6, 0xFFFF6E5A, 0x20FF6E5A, 2f, true);
        s.label("Alex 12m", -4, 66.3, 12.3, 0xFFFFFFFF, 1f);
        s.lineFromCrosshair(-4, 64.9, 12.3, 0xB340D9E3, 1.5f);
        s.box(5.7, 64, 25, 6.3, 65.8, 25.6, 0xB3FF6E5A, 0x10FF6E5A, 2f, true);
        s.label("Sam 26m", 6, 66.3, 25.3, 0xB3FFFFFF, 1f);
        s.lineFromCrosshair(6, 64.9, 25.3, 0x8040D9E3, 1.5f);
        s.box(2, 64, 9, 3, 65, 10, 0xFFFBB63E, 0x1AFBB63E, 1.5f, true);
        s.box(0, 64, 16, 1, 65, 17, 0xFF8E7BFA, 0x1A8E7BFA, 1.5f, true);
        javax.imageio.ImageIO.write(be.img, "png", new File(out, "esp_scene.png"));
    }

    /** Every HUD widget switched on, drawn as in game (no editor chrome). */
    static void hudAll(String out) throws Exception {
        LayoutModel lm = HudRenderer.defaultLayout(1407, 840);
        for (LayoutModel.Widget w : lm.widgets()) w.visible = true;
        Java2DBackend be = new Java2DBackend(1407, 840);
        RasterGfx g = new RasterGfx(be, 1407, 840);
        g.verticalGradient(0, 0, 1407, 420, 0xFF6FA8DC, 0xFFB6D7F0);
        g.verticalGradient(0, 420, 1407, 420, 0xFF5B8C3A, 0xFF3B5F26);
        HudRenderer.draw(g, lm, demoData(), false, null);
        ImageIO.write(be.img, "png", new File(out, "hud_all_widgets.png"));
    }

    /** Trajectory, entity/ore ESP, chunk borders, custom crosshair, styled self nametag and a notification, all from the real core code. */
    static void featuresScene(String out) throws Exception {
        int w = 1280, h = 720;
        Java2DBackend be = new Java2DBackend(w, h);
        RasterGfx g = new RasterGfx(be, w, h);
        g.verticalGradient(0, 0, w, h / 2.0, 0xFF6FA8DC, 0xFFB6D7F0);
        g.verticalGradient(0, h / 2.0, w, h / 2.0, 0xFF5B8C3A, 0xFF3B5F26);
        client.paint.core.render.Projection p = new client.paint.core.render.Projection();
        p.set(-4, 65.62, -3, 6f, 12f, 70, w, h);
        client.paint.core.render.ScreenSpaceSink s = new client.paint.core.render.ScreenSpaceSink(g, p);
        // chunk borders: a few grid lines and rings
        for (int i = -1; i <= 2; i++) for (int j = 0; j <= 3; j++) s.line(i * 16, 50, j * 16 + 4, i * 16, 90, j * 16 + 4, 0x9940D9E3, 1.5f);
        for (int y = 48; y <= 96; y += 16) { s.line(-16, y, 4, 32, y, 4, 0x6640D9E3, 1.5f); s.line(-16, y, 52, 32, y, 52, 0x6640D9E3, 1.5f); }
        // entity ESP
        s.box(-6.3, 64, 18, -5.7, 65.95, 18.6, 0xFFFF6E5A, 0x1AFF6E5A, 1.5f, true);
        s.label("Zombie 14/20  19m", -6, 66.3, 18.3, 0xFFFFFFFF, 1f);
        s.box(6.7, 64, 14, 7.6, 65.4, 14.9, 0xFFA1E83A, 0x1AA1E83A, 1.5f, true);
        s.label("Cow 10/10  15m", 7.1, 65.8, 14.4, 0xFFFFFFFF, 1f);
        s.box(2.4, 64, 10, 2.65, 64.25, 10.25, 0xFFFBB63E, 0x1AFBB63E, 1.5f, true);
        s.label("Diamond x3  10m", 2.5, 64.6, 10.1, 0xFFFFFFFF, 1f);
        // ore ESP
        for (double[] o : new double[][] {{-3, 60, 22}, {-2, 60, 22}, {-3, 59, 23}}) s.box(o[0], o[1], o[2], o[0] + 1, o[1] + 1, o[2] + 1, 0xFF40D9E3, 0x2640D9E3, 1.5f, true);
        s.box(5, 58, 20, 6, 59, 21, 0xFFC8924A, 0x26C8924A, 1.5f, true);
        // trajectory of a held ender pearl
        java.util.List<double[]> path = client.paint.core.render.Trajectory.simulate(client.paint.core.render.Trajectory.Kind.PEARL, 1, 0, 65.52, 0, 8f, -28f, 120, (x1, y1, z1, x2, y2, z2) -> y2 <= 64);
        for (int i = 1; i < path.size(); i++) { double[] a = path.get(i - 1), b = path.get(i); s.line(a[0], a[1], a[2], b[0], b[1], b[2], 0xFF5EEAD4, 1.5f); }
        double[] e = path.get(path.size() - 1);
        s.box(e[0] - .15, e[1] - .15, e[2] - .15, e[0] + .15, e[1] + .15, e[2] + .15, 0xFF5EEAD4, 0x595EEAD4, 1.5f, true);
        // custom crosshair, styled self nametag, notification
        client.paint.core.gui.CrosshairRenderer.draw(g, w / 2.0, h / 2.0, 7, 3, 2, 0xFFFFFFFF, true, false, true, 0.45);
        client.paint.core.nametag.NametagStyle st = new client.paint.core.nametag.NametagStyle();
        st.colorStart = 0xFFFF6E5A; st.colorEnd = 0xFFFBB63E; st.bold = true; st.prefixSymbol = "~"; st.suffixSymbol = "~";
        client.paint.core.gui.SelfNametagRenderer.draw(g, st, "Mason", w / 2.0, 120, 1.4);
        client.paint.core.hud.NotificationCenter nc = new client.paint.core.hud.NotificationCenter();
        nc.push("Base Finder: Possible Activity at chunk -75, 242 (score 41)", 1000);
        client.paint.core.gui.NotificationRenderer.draw(g, nc, 1500, w);
        ImageIO.write(be.img, "png", new File(out, "features_scene.png"));
    }

    /** The ClickGUI populated with the REAL registered modules (what the in-game menu lists), not the reference stand-ins. */
    static void realGui(String out) throws Exception {
        client.paint.core.game.GameAccess game = (client.paint.core.game.GameAccess) java.lang.reflect.Proxy.newProxyInstance(
                Preview.class.getClassLoader(), new Class<?>[] {client.paint.core.game.GameAccess.class}, (proxy, m, args) -> {
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false; if (r == int.class) return 0; if (r == double.class) return 0.0;
                    if (r == float.class) return 0f; if (r == long.class) return 0L; if (r == String.class) return "overworld";
                    return null; });
        ModuleManager mm = new ModuleManager();
        client.paint.core.modules.Modules.registerAll(mm, new client.paint.core.game.ModuleContext(game, new client.paint.core.game.EventBus(),
                java.nio.file.Files.createTempDirectory("paintreal")));
        mm.get("Toggle Sprint").setEnabled(true);
        ImageIO.write(renderGui(mm, HudRenderer.defaultLayout(1407, 840), 1407, 840, 1.0), "png", new File(out, "gui_real_modules.png"));
    }
}
