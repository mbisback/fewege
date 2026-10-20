package client.paint.core.gui;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.ModuleManager;
import client.paint.core.Risk;
import client.paint.core.Setting;
import client.paint.core.gui.Gfx.Font;
import client.paint.core.layout.LayoutModel;
import client.paint.core.layout.LayoutModel.Widget;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The main ClickGUI (Reference 1), laid out in 1407x840 design units. Immediate-mode: every frame
 * {@link #render} rebuilds the clickable regions, and input is routed through the last frame's regions.
 *
 * <p>Controls: left-click a row = toggle, right-click = open its settings card, middle-click = favourite,
 * drag a panel header = move it (right-click header = reset position), mouse wheel over a panel = scroll,
 * type = search, click the keybind chip then press a key = rebind.
 */
public final class ClickGuiView {
    public static final double DW = 1407, DH = 840;
    private static final double PANEL_W = 214, PANEL_PITCH = 229, PANEL_X0 = 25, PANEL_Y0 = 93, ROW_PITCH = 29.2;
    private static final int MAX_ROWS = 10;

    private interface ClickFn { void click(double mx, double my, int button); }
    private interface DragFn { void drag(double mx, double my, double dx, double dy); }

    private static final class Region {
        final double x, y, w, h;
        ClickFn click;
        DragFn drag;
        Module hoverModule;
        Region(double x, double y, double w, double h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(double px, double py) { return px >= x && px <= x + w && py >= y && py <= y + h; }
    }

    private final ModuleManager mm;
    private final LayoutModel layout;
    private final Category[] cats = Category.values();
    private final double[] posX = new double[cats.length], posY = new double[cats.length];
    private final int[] scroll = new int[cats.length];

    private final List<Region> regions = new ArrayList<>();
    private final Map<Module, Double> anim = new HashMap<>();
    private final Set<String> favorites = new HashSet<>();
    private final Deque<String> recents = new ArrayDeque<>();

    private Module selected;
    private Module capturing;
    private Setting.Text editing;
    private int settingsScroll;
    private int overlayScroll;
    private Module lastCardModule;
    private String captureMessage = "";
    private int pendingReplaceKey = -2;
    private String search = "";

    private double mouseX, mouseY, lastMouseX, lastMouseY;
    private Region active;
    private Module hoverModule;
    private long hoverSince, lastNow;

    /** 0..1 background darkness (for in-game use over the world). 1 = reference look. */
    public double backgroundOpacity = 1.0;
    public double animationSpeed = 1.0;
    /** Called after any state change that should be persisted. */
    public Runnable onChanged = () -> { };

    public ClickGuiView(ModuleManager mm, LayoutModel layout) {
        this.mm = mm; this.layout = layout;
        resetPanelPositions();
        List<Module> all = mm.all();
        if (!all.isEmpty()) selected = all.get(0);
    }

    // ------------------------------------------------------------------ state accessors

    public Module selected() { return selected; }
    public void select(Module m) { selected = m; }
    public boolean isCapturingKey() { return capturing != null; }
    public boolean isEditingText() { return editing != null; }
    /** True while a keybind or text field is waiting for typing (the host must not treat keys as hotkeys). */
    public boolean wantsKeyboard() { return capturing != null || editing != null; }
    public String search() { return search; }
    public boolean isFavorite(Module m) { return favorites.contains(m.name); }
    public Set<String> favorites() { return favorites; }
    public List<String> recents() { return new ArrayList<>(recents); }
    public double panelX(Category c) { return posX[c.ordinal()]; }
    public double panelY(Category c) { return posY[c.ordinal()]; }

    public void resetPanelPositions() {
        for (int i = 0; i < cats.length; i++) { posX[i] = PANEL_X0 + PANEL_PITCH * i; posY[i] = PANEL_Y0; scroll[i] = 0; }
    }

    // ------------------------------------------------------------------ rendering

    public void render(Gfx g, long nowMs) {
        double dt = lastNow == 0 ? 0 : Math.min(0.1, (nowMs - lastNow) / 1000.0);
        lastNow = nowMs;
        regions.clear();

        drawBackground(g);
        drawHeader(g);

        Module newHover = null;
        for (int i = 0; i < cats.length; i++) {
            Module hm = drawPanel(g, i, dt);
            if (hm != null) newHover = hm;
        }
        if (newHover != hoverModule) { hoverModule = newHover; hoverSince = nowMs; }

        drawModuleCard(g);
        drawOverlayCard(g);
        drawRiskCard(g);

        if (hoverModule != null && nowMs - hoverSince > 450 && capturing == null) drawTooltip(g, hoverModule);
    }

    private void drawBackground(Gfx g) {
        double[] b = g.visibleBounds();
        int base = Theme.BG_BASE;
        int a = (int) Math.round(255 * Math.max(0, Math.min(1, backgroundOpacity)));
        g.fillRect(b[0], b[1], b[2] - b[0], b[3] - b[1], (a << 24) | (base & 0xFFFFFF));
        double gl = backgroundOpacity;
        g.radialGlow(-20, -60, 760, 420, glowAlpha(Theme.GLOW_TEAL, 0.95 * gl));
        g.radialGlow(1250, 930, 820, 470, glowAlpha(Theme.GLOW_PURPLE, 0.95 * gl));
    }

    private static int glowAlpha(int rgb, double alpha) {
        return ((int) Math.round(255 * Math.max(0, Math.min(1, alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    private void drawHeader(Gfx g) {
        HudRenderer.drop(g, 39, 53, 8);
        g.text("Paint", 65, 57, 25, Theme.TEXT, Font.BOLD, 0);
        g.text("CLIENT", 139, 54, 10, Theme.MUTED, Font.SANS, 3);

        // right-hand pills (positions from the reference)
        pill(g, 981, 33, 216, 34, "Drag a panel header to move it", null);
        pill(g, 1207, 33, 77, 34, "Menu: ", "U");
        pill(g, 1294, 33, 89, 34, "Overlay: ", "H");

        if (!search.isEmpty()) {
            double w = g.textWidth("Search: " + search, 12, Font.BOLD, 0) + 28;
            pill(g, 981 - 10 - w, 33, w, 34, "Search: ", search);
        } else if (!recents.isEmpty()) {
            String r = "Recent: " + String.join(", ", recents);
            g.text(r, 250, 54, 11, Theme.DIM);
        }
    }

    private void pill(Gfx g, double x, double y, double w, double h, String text, String bold) {
        g.fillRoundRect(x, y, w, h, h / 2, Theme.PILL_FILL);
        g.strokeRoundRect(x, y, w, h, h / 2, 1, Theme.PILL_BORDER);
        double tw = g.textWidth(text, 12, Font.SANS, 0) + (bold == null ? 0 : g.textWidth(bold, 12, Font.BOLD, 0));
        double tx = x + (w - tw) / 2;
        g.textMid(text, tx, y + h / 2, 12, Theme.MUTED, Font.SANS, 0);
        if (bold != null) g.textMid(bold, tx + g.textWidth(text, 12, Font.SANS, 0), y + h / 2, 12, Theme.TEXT, Font.BOLD, 0);
    }

    private Module drawPanel(Gfx g, int ci, double dt) {
        Category cat = cats[ci];
        List<Module> list = mm.inCategory(cat);
        int vis = Math.min(list.size(), MAX_ROWS);
        double x = posX[ci], y = posY[ci];
        double h = vis <= 0 ? 60 : 80 + (vis - 1) * ROW_PITCH;
        int color = Theme.categoryColor(cat);
        int maxScroll = Math.max(0, list.size() - MAX_ROWS);
        scroll[ci] = Math.max(0, Math.min(maxScroll, scroll[ci]));

        g.fillRoundRect(x, y, PANEL_W, h, 12, Theme.PANEL_FILL);
        g.strokeRoundRect(x, y, PANEL_W, h, 12, 1, Theme.PANEL_BORDER);
        g.fillRect(x + 1, y + 40, PANEL_W - 2, 1, Theme.DIVIDER);

        // header
        g.fillCircle(x + 19, y + 20, 4.5, color);
        g.textMid(cat.title.toUpperCase(Locale.ROOT), x + 33, y + 20, 11, Theme.TEXT, Font.BOLD, 1.8);
        g.textRight(mm.enabledCount(cat) + " on", x + PANEL_W - 15, y + 20, 11, Theme.MUTED, Font.SANS, 0);

        final int idx = ci;
        Region head = new Region(x, y, PANEL_W, 40);
        head.drag = (mx, my, dx, dy) -> {
            posX[idx] = Math.max(-PANEL_W + 60, Math.min(DW - 60, posX[idx] + dx));
            posY[idx] = Math.max(0, Math.min(DH - 40, posY[idx] + dy));
        };
        head.click = (mx, my, b) -> { if (b == 1) { posX[idx] = PANEL_X0 + PANEL_PITCH * idx; posY[idx] = PANEL_Y0; } };
        regions.add(head);
        Region body = new Region(x, y + 40, PANEL_W, h - 40);
        body.click = (mx, my, b) -> { };
        regions.add(body);

        Module hovered = null;
        g.pushClip(x + 1, y + 41, PANEL_W - 2, h - 42);
        if (vis == 0) g.textMid("No modules in this category", x + 14, y + 51, 11, Theme.DIM, Font.SANS, 0);
        for (int v = 0; v < vis; v++) {
            Module m = list.get(scroll[ci] + v);
            double cy = y + 59 + v * ROW_PITCH;
            boolean over = mouseX >= x + 4 && mouseX <= x + PANEL_W - 4 && Math.abs(mouseY - cy) <= ROW_PITCH / 2;
            if (over) { g.fillRoundRect(x + 5, cy - 13, PANEL_W - 10, 26, 7, 0x0DFFFFFF); hovered = m; }

            double p = animate(m, dt);
            double nameX = x + 14;
            if (favorites.contains(m.name)) {
                g.textMid("★", nameX, cy, 10, Theme.AMBER, Font.SANS, 0);
                nameX += 12;
            }
            g.textMid(m.name, nameX, cy, 13, m == selected ? 0xFFFFFFFF : Theme.TEXT, Font.SANS, 0);

            double tw = 28, tx = x + PANEL_W - 13 - tw;
            double bw = g.textWidth(m.risk.name(), 9, Font.BOLD, 0.6) + 12;
            double bx = tx - 9 - bw;
            g.fillRoundRect(bx, cy - 8, bw, 16, 5, Theme.badgeFill(m.risk));
            g.textMid(m.risk.name(), bx + 6, cy, 9, Theme.badgeText(m.risk), Font.BOLD, 0.6);
            toggle(g, tx, cy - 8, p, color);

            if (!search.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                g.fillRect(x + 1, cy - ROW_PITCH / 2, PANEL_W - 2, ROW_PITCH, 0xB30D0F13);
            }

            Region row = new Region(x + 4, cy - ROW_PITCH / 2, PANEL_W - 8, ROW_PITCH);
            row.hoverModule = m;
            row.click = (mx, my, b) -> {
                if (b == 0) { m.toggle(); touchRecent(m); onChanged.run(); }
                else if (b == 1) selected = m;
                else if (b == 2) { if (!favorites.remove(m.name)) favorites.add(m.name); onChanged.run(); }
            };
            regions.add(row);
        }
        g.popClip();
        return hovered;
    }

    private double animate(Module m, double dt) {
        double target = m.isEnabled() ? 1 : 0;
        double cur = anim.getOrDefault(m, target);
        double speed = 9 * Math.max(0.1, animationSpeed);
        if (cur < target) cur = Math.min(target, cur + dt * speed);
        else if (cur > target) cur = Math.max(target, cur - dt * speed);
        anim.put(m, cur);
        return cur;
    }

    private static int lerp(int a, int b, double t) {
        int out = 0;
        for (int sh = 24; sh >= 0; sh -= 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
        }
        return out;
    }

    /** Pill toggle, 28x16. {@code p} 0 = off, 1 = on. */
    private static void toggle(Gfx g, double x, double y, double p, int onColor) {
        g.fillRoundRect(x, y, 28, 16, 8, lerp(Theme.TOGGLE_OFF, onColor, p));
        g.fillCircle(x + 8 + 12 * p, y + 8, 5.5, lerp(Theme.KNOB_OFF, Theme.KNOB_ON, p));
    }

    private void touchRecent(Module m) {
        recents.remove(m.name);
        recents.addFirst(m.name);
        while (recents.size() > 4) recents.removeLast();
    }

    // ------------------------------------------------------------------ bottom cards

    private void card(Gfx g, double x, double y, double w, double h) {
        g.fillRoundRect(x, y, w, h, 14, Theme.PANEL_FILL);
        g.strokeRoundRect(x, y, w, h, 14, 1, Theme.PANEL_BORDER);
    }

    private void drawModuleCard(Gfx g) {
        final double x = 25, y = 459, w = 589, h = 283;
        card(g, x, y, w, h);
        Module m = selected;
        if (m == null) {
            g.textMid("Right-click a module to see its settings", x + 20, y + 30, 13, Theme.MUTED, Font.SANS, 0);
            return;
        }
        int color = Theme.categoryColor(m.category);
        g.fillCircle(x + 24, y + 29, 4.5, color);
        g.textMid(m.name, x + 41, y + 29, 15, Theme.TEXT, Font.BOLD, 0);
        double tw = g.textWidth(m.name, 15, Font.BOLD, 0);
        double bw = g.textWidth(m.risk.name(), 9, Font.BOLD, 0.6) + 12, bx = x + 41 + tw + 9;
        g.fillRoundRect(bx, y + 21, bw, 16, 5, Theme.badgeFill(m.risk));
        g.textMid(m.risk.name(), bx + 6, y + 29, 9, Theme.badgeText(m.risk), Font.BOLD, 0.6);

        // keybind chip (right aligned)
        String keyText = capturing == m ? "…" : GuiKeys.name(m.getKeyCode());
        double kw = Math.max(24, g.textWidth(keyText, 11, Font.BOLD, 0) + 16);
        double kx = x + w - 20 - kw;
        g.fillRoundRect(kx, y + 18, kw, 22, 5, capturing == m ? 0xFF3A2A27 : Theme.CHIP_FILL);
        if (capturing == m) g.strokeRoundRect(kx, y + 18, kw, 22, 5, 1, Theme.CORAL);
        g.textMid(keyText, kx + (kw - g.textWidth(keyText, 11, Font.BOLD, 0)) / 2, y + 29, 11, Theme.TEXT, Font.BOLD, 0);
        g.textRight("Keybind", kx - 14, y + 29, 11, Theme.MUTED, Font.SANS, 0);
        Region chip = new Region(kx - 60, y + 14, kw + 60, 30);
        chip.click = (mx, my, b) -> {
            if (b == 0) { capturing = m; captureMessage = "Press a key. Esc cancels, Backspace unbinds."; pendingReplaceKey = -2; }
        };
        regions.add(chip);
        Region titleReset = new Region(x + 14, y + 14, 200, 30);
        titleReset.click = (mx, my, b) -> { if (b == 1) { m.resetSettings(); onChanged.run(); } };
        regions.add(titleReset);

        // settings grid: two columns, 47px rows
        if (m != lastCardModule) { lastCardModule = m; settingsScroll = 0; editing = null; }
        List<Setting<?>> ss = m.settings();
        double colW = 261;
        int maxScrollRows = Math.max(0, (ss.size() + 1) / 2 - 4);
        settingsScroll = Math.max(0, Math.min(maxScrollRows, settingsScroll));
        int first = settingsScroll * 2;
        int shown = Math.min(ss.size() - first, 8);
        if (maxScrollRows > 0) {
            g.textRight((settingsScroll + 1) + "/" + (maxScrollRows + 1) + "  scroll", x + w - 20, y + h - 14, 10, Theme.DIM, Font.SANS, 0);
        }
        for (int i = 0; i < shown; i++) {
            Setting<?> s = ss.get(first + i);
            double cx = x + 20 + (i % 2) * 288;
            double cy = y + 61 + (i / 2) * 47;
            drawSetting(g, s, cx, cy, colW, color);
        }
        int rowsUsed = Math.max(1, (shown + 1) / 2);
        double descY = y + 61 + (rowsUsed - 1) * 47 + 35;
        if (capturing == m) {
            g.textMid(captureMessage, x + 20, descY, 12, Theme.CORAL, Font.SANS, 0);
        } else {
            double yy = descY;
            for (String line : wrap(g, m.description, w - 40, 12)) {
                g.textMid(line, x + 20, yy, 12, Theme.MUTED, Font.SANS, 0);
                yy += 17;
            }
        }
    }

    private void drawSetting(Gfx g, Setting<?> s, double cx, double cy, double colW, int color) {
        g.textMid(s.name, cx, cy, 13, Theme.TEXT, Font.SANS, 0);
        if (s instanceof Setting.Num n) {
            double v = n.get();
            String vt = n.step >= 1 ? String.valueOf(Math.round(v)) : String.format(Locale.US, "%.1f", v);
            g.textRight(vt, cx + colW, cy, 13, color, Font.BOLD, 1.0);
            double ty = cy + 18, frac = (v - n.min) / (n.max - n.min);
            g.fillRoundRect(cx, ty, colW, 5, 2.5, Theme.TRACK);
            g.fillRoundRect(cx, ty, Math.max(5, colW * frac), 5, 2.5, color);
            Region r = new Region(cx - 4, ty - 8, colW + 8, 22);
            DragFn set = (mx, my, dx, dy) -> {
                double f = Math.max(0, Math.min(1, (mx - cx) / colW));
                double raw = n.min + f * (n.max - n.min);
                n.setClamped(Math.round(raw / n.step) * n.step);
                onChanged.run();
            };
            r.drag = set;
            r.click = (mx, my, b) -> { if (b == 0) set.drag(mx, my, 0, 0); };
            regions.add(r);
        } else if (s instanceof Setting.Choice<?> c) {
            String t = c.display();
            double pw = g.textWidth(t, 11, Font.SANS, 0) + 24;
            double px = cx + colW - pw;
            g.fillRoundRect(px, cy - 11, pw, 22, 6, Theme.CHIP_FILL);
            g.textMid(t, px + 12, cy, 11, Theme.TEXT, Font.SANS, 0);
            Region r = new Region(px, cy - 11, pw, 22);
            r.click = (mx, my, b) -> { if (b == 0) c.cycle(1); else if (b == 1) c.cycle(-1); onChanged.run(); };
            regions.add(r);
        } else if (s instanceof Setting.Color col) {
            int n = Setting.Color.PRESETS.length;
            double sw = 17, gap = 3, total = n * sw + (n - 1) * gap, sx0 = cx + colW - total;
            for (int i = 0; i < n; i++) {
                final int preset = Setting.Color.PRESETS[i];
                double sx = sx0 + i * (sw + gap);
                boolean cur = (col.argb() & 0xFFFFFF) == (preset & 0xFFFFFF);
                g.fillRoundRect(sx, cy - 8, sw, 16, 4, preset);
                if (cur) g.strokeRoundRect(sx - 1.5, cy - 9.5, sw + 3, 19, 5, 1.5, 0xFFFFFFFF);
                Region r = new Region(sx, cy - 10, sw, 20);
                r.click = (mx, my, b) -> { if (b == 0) { col.setRgb(preset); onChanged.run(); } };
                regions.add(r);
            }
        } else if (s instanceof Setting.Text t) {
            boolean ed = editing == t;
            String shown = t.get().isEmpty() && !ed ? "(default)" : t.get() + (ed ? "|" : "");
            double pw = Math.max(90, Math.min(160, g.textWidth(shown, 11, Font.SANS, 0) + 24)), px = cx + colW - pw;
            g.fillRoundRect(px, cy - 11, pw, 22, 6, ed ? 0xFF3A2A27 : Theme.CHIP_FILL);
            if (ed) g.strokeRoundRect(px, cy - 11, pw, 22, 6, 1, Theme.CORAL);
            g.pushClip(px, cy - 11, pw, 22);
            g.textMid(shown, px + 12, cy, 11, t.get().isEmpty() && !ed ? Theme.DIM : Theme.TEXT, Font.SANS, 0);
            g.popClip();
            Region r = new Region(px, cy - 11, pw, 22);
            r.click = (mx, my, b) -> { if (b == 0) editing = t; else if (b == 1) { t.reset(); onChanged.run(); } };
            regions.add(r);
        } else if (s instanceof Setting.Bool b) {
            double p = b.get() ? 1 : 0;
            toggle(g, cx + colW - 28, cy - 8, p, color);
            Region r = new Region(cx + colW - 34, cy - 12, 40, 24);
            r.click = (mx, my, bt) -> { if (bt == 0) { b.set(!b.get()); onChanged.run(); } };
            regions.add(r);
        }
    }

    private static final String[][] OVERLAY_ROWS = {
        {HudRenderer.FPS, "FPS"}, {HudRenderer.COORDS, "Coordinates"}, {HudRenderer.POTIONS, "Potion effects"},
        {HudRenderer.MINIMAP, "Minimap"}, {HudRenderer.ARMOUR, "Armour HUD"}, {HudRenderer.KEYS, "Keystrokes"},
        {HudRenderer.MODULES, "Module list"}, {HudRenderer.TARGET, "Target info"}, {HudRenderer.COUNTERS, "Item counters"},
        {HudRenderer.STATS, "Session stats"}, {HudRenderer.WAYPOINT, "Waypoint compass"},
    };
    private static final int OVERLAY_VISIBLE = 7;

    private void drawOverlayCard(Gfx g) {
        final double x = 629, y = 459, w = 446, h = 283;
        card(g, x, y, w, h);
        g.fillCircle(x + 25, y + 28, 4.5, Theme.CYAN);
        g.textMid("Overlay widgets", x + 40, y + 28, 15, Theme.TEXT, Font.BOLD, 0);
        int maxOv = Math.max(0, OVERLAY_ROWS.length - OVERLAY_VISIBLE);
        overlayScroll = Math.max(0, Math.min(maxOv, overlayScroll));
        if (maxOv > 0) g.textRight((overlayScroll + 1) + "/" + (maxOv + 1) + "  scroll", x + w - 20, y + 28, 10, Theme.DIM, Font.SANS, 0);
        for (int v = 0; v < OVERLAY_VISIBLE && overlayScroll + v < OVERLAY_ROWS.length; v++) {
            int i = overlayScroll + v;
            double cy = y + 64 + 31 * v;
            Widget wd = layout.get(OVERLAY_ROWS[i][0]);
            if (wd == null) continue;
            g.textMid(OVERLAY_ROWS[i][1], x + 20, cy, 13, Theme.TEXT, Font.SANS, 0);
            toggle(g, x + w - 20 - 28, cy - 8, wd.visible ? 1 : 0, Theme.CYAN);
            Region r = new Region(x + 10, cy - 15, w - 20, 30);
            r.click = (mx, my, b) -> { if (b == 0) { wd.visible = !wd.visible; onChanged.run(); } };
            regions.add(r);
        }
    }

    private void drawRiskCard(Gfx g) {
        final double x = 1091, y = 459, w = 291, h = 283;
        card(g, x, y, w, h);
        g.textMid("Ban risk", x + 19, y + 28, 15, Theme.TEXT, Font.BOLD, 0);
        double top = y + 50;
        for (Risk r : Risk.values()) {
            List<String> lines = wrap(g, r.description, 200, 12);
            double bw = g.textWidth(r.name(), 9, Font.BOLD, 0.6) + 12;
            g.fillRoundRect(x + 19, top + 1, bw, 16, 5, Theme.badgeFill(r));
            g.textMid(r.name(), x + 25, top + 9, 9, Theme.badgeText(r), Font.BOLD, 0.6);
            for (int i = 0; i < lines.size(); i++) g.textMid(lines.get(i), x + 72, top + 9 + 18 * i, 12, Theme.MUTED, Font.SANS, 0);
            top += lines.size() * 18 + 11;
        }
    }

    private void drawTooltip(Gfx g, Module m) {
        String t = m.description;
        double w = Math.min(280, g.textWidth(t, 11, Font.SANS, 0) + 20);
        List<String> lines = wrap(g, t, w - 20, 11);
        double h = 14 + lines.size() * 15;
        double tx = Math.min(DW - w - 6, mouseX + 14), ty = Math.min(DH - h - 6, mouseY + 18);
        g.fillRoundRect(tx, ty, w, h, 7, 0xF2101318);
        g.strokeRoundRect(tx, ty, w, h, 7, 1, Theme.PILL_BORDER);
        for (int i = 0; i < lines.size(); i++) g.textMid(lines.get(i), tx + 10, ty + 14 + 15 * i, 11, Theme.TEXT, Font.SANS, 0);
    }

    static List<String> wrap(Gfx g, String text, double maxW, double size) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && g.textWidth(trial, size, Font.SANS, 0) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else cur = new StringBuilder(trial);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ------------------------------------------------------------------ input (design coordinates)

    public void mouseMoved(double mx, double my) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        mouseX = mx; mouseY = my;
    }

    /** @return true if the click hit a GUI element. */
    public boolean mouseDown(double mx, double my, int button) {
        mouseMoved(mx, my);
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.contains(mx, my)) {
                if (r.click != null) r.click.click(mx, my, button);
                active = (button == 0 && r.drag != null) ? r : null;
                return true;
            }
        }
        active = null;
        return false;
    }

    public void mouseDragged(double mx, double my) {
        double dx = mx - mouseX, dy = my - mouseY;
        mouseX = mx; mouseY = my;
        if (active != null && active.drag != null) active.drag.drag(mx, my, dx, dy);
    }

    public void mouseUp() { active = null; onChanged.run(); }

    public void scroll(double mx, double my, double amount) {
        if (mx >= 629 && mx <= 1075 && my >= 459 && my <= 742) {
            overlayScroll = Math.max(0, Math.min(OVERLAY_ROWS.length - OVERLAY_VISIBLE, overlayScroll - (int) Math.signum(amount)));
            return;
        }
        if (mx >= 25 && mx <= 614 && my >= 459 && my <= 742) {
            settingsScroll = Math.max(0, settingsScroll - (int) Math.signum(amount));
            return;
        }
        for (int i = 0; i < cats.length; i++) {
            double h = 80 + (Math.min(mm.inCategory(cats[i]).size(), MAX_ROWS) - 1) * ROW_PITCH;
            if (mx >= posX[i] && mx <= posX[i] + PANEL_W && my >= posY[i] && my <= posY[i] + h) {
                int max = Math.max(0, mm.inCategory(cats[i]).size() - MAX_ROWS);
                scroll[i] = Math.max(0, Math.min(max, scroll[i] - (int) Math.signum(amount)));
                return;
            }
        }
    }

    /** Raw key press (GLFW code). @return true if the GUI consumed it. */
    public boolean keyPressed(int key) {
        if (editing != null) {
            if (key == GuiKeys.ESCAPE || key == GuiKeys.ENTER) { editing = null; onChanged.run(); }
            else if (key == GuiKeys.BACKSPACE && !editing.get().isEmpty()) editing.set(editing.get().substring(0, editing.get().length() - 1));
            return true;
        }
        if (capturing != null) {
            Module m = capturing;
            if (key == GuiKeys.ESCAPE) { capturing = null; return true; }
            if (key == GuiKeys.BACKSPACE || key == GuiKeys.DELETE) { mm.unbind(m); capturing = null; onChanged.run(); return true; }
            List<Module> conflicts = mm.bind(m, key, false);
            if (conflicts.isEmpty()) { capturing = null; onChanged.run(); return true; }
            if (pendingReplaceKey == key) { mm.bind(m, key, true); capturing = null; onChanged.run(); return true; }
            pendingReplaceKey = key;
            captureMessage = GuiKeys.name(key) + " is used by " + conflicts.get(0).name + ". Press it again to replace.";
            return true;
        }
        if (key == GuiKeys.ESCAPE && !search.isEmpty()) { search = ""; return true; }
        if (key == GuiKeys.BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.length() - 1); return true; }
        return false;
    }

    public boolean charTyped(char c) {
        if (editing != null) {
            if (c >= 32 && c != 127 && c != '\u00A7') editing.set(editing.get() + c);
            return true;
        }
        if (capturing != null) return true;
        if (c >= 32 && c != 127 && search.length() < 24) { search += c; return true; }
        return false;
    }
}
