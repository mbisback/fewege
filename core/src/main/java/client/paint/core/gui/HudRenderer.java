package client.paint.core.gui;

import client.paint.core.gui.Gfx.Font;
import client.paint.core.gui.HudData.Key;
import client.paint.core.hud.HudMath;
import client.paint.core.layout.LayoutModel;
import client.paint.core.layout.LayoutModel.Widget;
import client.paint.core.world.Waypoints;

import java.util.List;
import java.util.Locale;

/**
 * Draws the HUD widgets (Reference 2). Each widget's content is laid out at its natural size and then
 * scaled to the widget's box, so resizing in the editor scales the content. {@code editing=true} adds
 * the dashed outline + caption chrome of the layout editor.
 */
public final class HudRenderer {
    private HudRenderer() {}

    public static final String WATERMARK = "watermark", FPS = "fps", COORDS = "coords", POTIONS = "potions",
            ARMOUR = "armour", MINIMAP = "minimap", MODULES = "modulelist", KEYS = "keys",
            TARGET = "target", COUNTERS = "counters", STATS = "stats", WAYPOINT = "waypoint";

    /** id, caption, natural width, natural height (design px). */
    public static final Object[][] SPECS = {
        {WATERMARK, "Watermark", 195, 58}, {FPS, "FPS and Ping", 195, 55}, {COORDS, "Coordinates", 244, 93},
        {POTIONS, "Potion Effects", 214, 119}, {ARMOUR, "Armour", 204, 139}, {MINIMAP, "Minimap", 194, 227},
        {MODULES, "Module List", 194, 109}, {KEYS, "Keystrokes", 194, 192},
        {TARGET, "Target", 214, 82}, {COUNTERS, "Counters", 194, 170}, {STATS, "Session Stats", 214, 152}, {WAYPOINT, "Waypoint", 214, 74},
    };

    private static final int CHROME_FILL = 0x8C170F22, CHROME_DASH = 0x9A9AA5B8, HUD_FILL = 0x99120C1C;

    /** Default layout matching Reference 2 at 1407x840 (fractions of the screen). */
    public static LayoutModel defaultLayout(double screenW, double screenH) {
        LayoutModel m = new LayoutModel(screenW, screenH);
        double W = 1407, H = 840;
        add(m, WATERMARK, 24, 24, W, H); add(m, FPS, 24, 97, W, H); add(m, COORDS, 24, 171, W, H);
        add(m, POTIONS, 24, 288, W, H); add(m, ARMOUR, 24, 677, W, H); add(m, MINIMAP, 1189, 24, W, H);
        add(m, MODULES, 1189, 293, W, H); add(m, KEYS, 1189, 624, W, H);
        add(m, TARGET, 596, 470, W, H); add(m, COUNTERS, 1189, 420, W, H); add(m, STATS, 24, 420, W, H); add(m, WAYPOINT, 596, 24, W, H);
        for (String id : new String[] {TARGET, COUNTERS, STATS, WAYPOINT}) m.get(id).defVisible = false;
        m.get(ARMOUR).defVisible = false; m.get(KEYS).defVisible = false; m.get(MODULES).defVisible = false;
        m.resetAll();
        return m;
    }

    private static void add(LayoutModel m, String id, double x, double y, double W, double H) {
        for (Object[] s : SPECS) {
            if (s[0].equals(id)) {
                double w = (int) s[2], h = (int) s[3];
                m.add(new Widget(id, (String) s[1], x / W, y / H, w / W, h / H, w * 0.5, h * 0.5));
                return;
            }
        }
    }

    private static Object[] spec(String id) {
        for (Object[] s : SPECS) if (s[0].equals(id)) return s;
        return null;
    }

    public static void draw(Gfx g, LayoutModel layout, HudData d, boolean editing, Widget selected) {
        for (Widget w : layout.widgets()) {
            if (!editing && !w.visible) continue;
            drawWidget(g, w, d, editing, w == selected);
        }
    }

    private static void drawWidget(Gfx g, Widget w, HudData d, boolean editing, boolean selected) {
        Object[] sp = spec(w.id);
        if (sp == null) return;
        double natW = (int) sp[2], natH = (int) sp[3];
        double k = Math.max(0.5, Math.min(3.0, Math.min(w.w / natW, w.h / natH)));

        if (editing) {
            g.fillRoundRect(w.x, w.y, w.w, w.h, 8, CHROME_FILL);
            if (selected) g.strokeRoundRect(w.x, w.y, w.w, w.h, 8, 1.5, Theme.CORAL);
            else g.dashedRoundRect(w.x + 0.5, w.y + 0.5, w.w - 1, w.h - 1, 8, 1, 3, 2.6, CHROME_DASH);
        } else {
            g.fillRoundRect(w.x, w.y, w.w, w.h, 8, HUD_FILL);
        }

        g.pushClip(w.x, w.y, w.w, w.h);
        g.pushTransform(w.x, w.y, k);
        if (editing) g.text(((String) sp[1]).toUpperCase(Locale.ROOT), 12, 19, 8.5, 0xFF9C94B0, Font.SANS, 1.2);
        switch (w.id) {
            case WATERMARK -> watermark(g);
            case FPS -> fps(g, d);
            case COORDS -> coords(g, d);
            case POTIONS -> potions(g, d);
            case ARMOUR -> armour(g, d);
            case MINIMAP -> minimap(g, d);
            case MODULES -> moduleList(g, d);
            case KEYS -> keystrokes(g, d);
            case TARGET -> target(g, d);
            case COUNTERS -> counters(g, d);
            case STATS -> stats(g, d);
            case WAYPOINT -> waypoint(g, d);
            default -> { }
        }
        g.popTransform();
        g.popClip();

        if (editing && selected) {
            for (double[] c : new double[][] {{w.x, w.y}, {w.x + w.w, w.y}, {w.x, w.y + w.h}, {w.x + w.w, w.y + w.h}}) {
                g.fillRect(c[0] - 3, c[1] - 3, 6, 6, Theme.CORAL);
            }
        }
    }

    // ------------------------------------------------------------------ widgets (natural-size local coords)

    /** Teardrop logo centred at (cx,cy). */
    public static void drop(Gfx g, double cx, double cy, double r) {
        g.fillPolygon(new double[] {cx, cx + r * 0.92, cx - r * 0.92}, new double[] {cy - r * 1.7, cy - r * 0.35, cy - r * 0.35}, Theme.CORAL);
        g.fillCircle(cx, cy, r, Theme.CORAL);
        g.fillCircle(cx + r * 0.25, cy + r * 0.2, r * 0.38, 0x66FFFFFF);
    }

    private static void watermark(Gfx g) {
        drop(g, 21, 38, 7);
        g.text("Paint Client", 40, 43, 13, Theme.TEXT, Font.BOLD, 0);
        g.text("1.0", 129, 42, 10, Theme.MUTED, Font.SANS, 0);
    }

    private static void fps(Gfx g, HudData d) {
        g.text("FPS", 12, 41, 13, Theme.MUTED);
        g.text(String.valueOf(d.fps()), 40, 41, 13, Theme.TEXT, Font.BOLD, 0);
        g.text("Ping", 86, 41, 13, Theme.MUTED);
        g.text(String.valueOf(d.ping()), 119, 41, 13, Theme.TEXT, Font.BOLD, 0);
    }

    private static void coords(Gfx g, HudData d) {
        g.text(String.format(Locale.US, "X %,d   Y %,d   Z %,d", (long) Math.floor(d.x()), (long) Math.floor(d.y()), (long) Math.floor(d.z())),
                12, 46, 13, Theme.TEXT, Font.MONO_BOLD, 0);
        String alt = d.inNether()
                ? String.format(Locale.US, "Overworld X %,d   Z %,d", (long) (d.x() * 8), (long) (d.z() * 8))
                : String.format(Locale.US, "Nether X %,d   Z %,d", (long) (d.x() / 8), (long) (d.z() / 8));
        g.text(alt, 12, 63, 11, Theme.MUTED, Font.MONO, 0);
        g.text("Facing " + Waypoints.facing(d.yaw()), 12, 80, 11, Theme.MUTED);
    }

    private static void potions(Gfx g, HudData d) {
        List<HudData.Effect> list = d.effects();
        for (int i = 0; i < list.size() && i < 6; i++) {
            double cy = 42 + 27 * i;
            g.fillRoundRect(13, cy - 8, 16, 16, 4, 0xFF000000 | list.get(i).rgb());
            g.textMid(list.get(i).name(), 37, cy, 12, Theme.TEXT, Font.BOLD, 0);
            g.textRight(HudMath.formatEffectTicks(list.get(i).ticksLeft()), 201, cy, 11, Theme.MUTED, Font.MONO, 0);
        }
    }

    private static void armour(Gfx g, HudData d) {
        List<HudData.ArmourPiece> list = d.armour();
        for (int i = 0; i < list.size() && i < 4; i++) {
            double cy = 40 + 25.5 * i;
            HudData.ArmourPiece p = list.get(i);
            g.fillRoundRect(13, cy - 8, 16, 16, 4, 0xFF000000 | p.rgb());
            g.textMid(p.name(), 37, cy, 12, Theme.TEXT, Font.BOLD, 0);
            g.fillRoundRect(141, cy - 2, 50, 4, 2, Theme.TRACK);
            int pct = Math.max(0, Math.min(100, p.percent()));
            if (pct > 0) g.fillRoundRect(141, cy - 2, 50.0 * pct / 100.0, 4, 2, Theme.TEAL);
        }
    }

    private static void minimap(Gfx g, HudData d) {
        final int cells = 17, cell = 10;
        double ox = 12, oy = 28;
        g.fillRoundRect(ox, oy, cells * cell, cells * cell, 4, 0xFF0F1A14);
        g.pushClip(ox, oy, cells * cell, cells * cell);
        for (int cz = 0; cz < cells; cz++) {
            for (int cx = 0; cx < cells; cx++) {
                int c = d.minimapColor(cx - cells / 2, cz - cells / 2);
                if ((c >>> 24) != 0) g.fillRect(ox + cx * cell, oy + cz * cell, cell, cell, c);
            }
        }
        g.popClip();
        g.text("N", ox + 4, oy + 12, 9, Theme.TEXT, Font.BOLD, 0);
        double px = ox + cells * cell / 2.0, py = oy + cells * cell / 2.0 - 2;
        g.fillPolygon(new double[] {px, px + 8, px - 8}, new double[] {py - 8, py + 7, py + 7}, Theme.CORAL);
        String c = String.format(Locale.US, "%,d / %,d", (long) Math.floor(d.x()), (long) Math.floor(d.z()));
        g.text(c, 97 - g.textWidth(c, 11, Font.MONO, 0) / 2, 214, 11, Theme.MUTED, Font.MONO, 0);
    }

    private static void moduleList(Gfx g, HudData d) {
        List<HudData.ModuleDot> list = d.activeModules();
        for (int i = 0; i < list.size() && i < 6; i++) {
            double cy = 40 + 23.5 * i;
            g.fillCircle(177, cy, 3.5, 0xFF000000 | list.get(i).rgb());
            g.textRight(list.get(i).name(), 166, cy, 12, Theme.TEXT, Font.SANS, 0);
        }
    }

    private static void keystrokes(Gfx g, HudData d) {
        key(g, d, Key.W, "W", 73, 29, 47, 37);
        key(g, d, Key.A, "A", 21, 73, 46, 36);
        key(g, d, Key.S, "S", 73, 73, 47, 36);
        key(g, d, Key.D, "D", 126, 73, 47, 36);
        key(g, d, Key.SPACE, "SPACE", 21, 116, 152, 25);
        g.fillRoundRect(21, 147, 73, 35, 6, d.keyDown(Key.LMB) ? Theme.CORAL : 0xFF2A272D);
        g.fillRoundRect(100, 147, 73, 35, 6, d.keyDown(Key.RMB) ? Theme.CORAL : 0xFF2A272D);
        centered(g, "LMB", 57.5, 160, 10, Theme.TEXT, Font.BOLD);
        centered(g, d.cpsLeft() + " CPS", 57.5, 174, 9, Theme.MUTED, Font.SANS);
        centered(g, "RMB", 136.5, 160, 10, Theme.TEXT, Font.BOLD);
        centered(g, d.cpsRight() + " CPS", 136.5, 174, 9, Theme.MUTED, Font.SANS);
    }

    private static void target(Gfx g, HudData d) {
        HudData.Target t = d.target();
        if (t == null) { g.text("No target", 12, 44, 12, Theme.MUTED); return; }
        g.text(t.name(), 12, 41, 13, Theme.TEXT, Font.BOLD, 0);
        g.textRight(Math.round(t.distance()) + " m", 202, 38, 11, Theme.MUTED, Font.MONO, 0);
        if (t.health() >= 0 && t.maxHealth() > 0) {
            double f = Math.max(0, Math.min(1, t.health() / t.maxHealth()));
            g.fillRoundRect(12, 49, 190, 6, 3, Theme.TRACK);
            if (f > 0) g.fillRoundRect(12, 49, 190 * f, 6, 3, f > 0.5 ? Theme.TEAL : f > 0.25 ? 0xFFFBB63E : Theme.CORAL);
            g.text(Math.round(t.health()) + " / " + Math.round(t.maxHealth()), 12, 70, 11, Theme.MUTED, Font.MONO, 0);
        } else {
            g.text("health unknown", 12, 70, 11, Theme.MUTED);
        }
    }

    private static void counters(Gfx g, HudData d) {
        List<HudData.Counter> list = d.counters();
        for (int i = 0; i < list.size() && i < 6; i++) {
            double cy = 42 + 22 * i;
            g.textMid(list.get(i).label(), 13, cy, 12, Theme.MUTED, Font.SANS, 0);
            g.textRight(String.valueOf(list.get(i).value()), 182, cy, 12, Theme.TEXT, Font.MONO_BOLD, 0);
        }
    }

    private static void stats(Gfx g, HudData d) {
        List<HudData.StatLine> list = d.stats();
        for (int i = 0; i < list.size() && i < 5; i++) {
            double cy = 42 + 23 * i;
            g.textMid(list.get(i).label(), 13, cy, 12, Theme.MUTED, Font.SANS, 0);
            g.textRight(list.get(i).value(), 202, cy, 12, Theme.TEXT, Font.MONO_BOLD, 0);
        }
    }

    private static void waypoint(Gfx g, HudData d) {
        HudData.WaypointInfo w = d.nearestWaypoint();
        if (w == null) { g.text("No waypoints here", 12, 44, 12, Theme.MUTED); return; }
        g.text(w.name(), 12, 42, 13, Theme.TEXT, Font.BOLD, 0);
        g.text(Waypoints.formatDistance(w.distance()), 12, 64, 12, Theme.MUTED, Font.MONO, 0);
        // arrow: bearing 0 points up, positive turns right
        double cx = 180, cy = 46, r = 15, a = Math.toRadians(w.bearingDeg());
        double sx = Math.sin(a), sy = -Math.cos(a);
        g.fillPolygon(new double[] {cx + sx * r, cx - sy * r * 0.6 - sx * r * 0.6, cx + sy * r * 0.6 - sx * r * 0.6},
                new double[] {cy + sy * r, cy + sx * r * 0.6 - sy * r * 0.6, cy - sx * r * 0.6 - sy * r * 0.6}, Theme.CORAL);
        g.strokeRoundRect(cx - r - 4, cy - r - 4, 2 * r + 8, 2 * r + 8, r + 4, 1, Theme.TRACK);
    }

    private static void key(Gfx g, HudData d, Key k, String label, double x, double y, double w, double h) {
        boolean down = d.keyDown(k);
        g.fillRoundRect(x, y, w, h, 6, down ? Theme.CORAL : 0xFF2A272D);
        double size = label.length() > 1 ? 9 : 12;
        double sp = label.length() > 1 ? 1.0 : 0;
        double tw = g.textWidth(label, size, Font.BOLD, sp);
        g.textMid(label, x + (w - tw) / 2, y + h / 2, size, down ? 0xFF3A130C : Theme.TEXT, Font.BOLD, sp);
    }

    private static void centered(Gfx g, String s, double cx, double baseline, double size, int argb, Font f) {
        g.text(s, cx - g.textWidth(s, size, f, 0) / 2, baseline, size, argb, f, 0);
    }
}
