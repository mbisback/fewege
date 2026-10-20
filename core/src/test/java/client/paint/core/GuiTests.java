package client.paint.core;

import client.paint.core.gui.*;
import client.paint.core.gui.Gfx.Font;
import client.paint.core.layout.LayoutModel;
import client.paint.core.layout.LayoutModel.Widget;

import java.util.ArrayList;
import java.util.List;

/** GUI input/behaviour tests (headless: drawing goes to a no-op backend). */
public class GuiTests {
    static int passed, failed;
    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS " + name); } else { failed++; System.out.println("FAIL " + name); }
    }

    static final class NullBackend implements RasterGfx.Backend {
        int fills, texts;
        public void fill(int x, int y, int w, int h, int argb) { fills++; }
        public void text(String s, float x, float y, float size, Font f, int argb) { texts++; }
        public float textWidth(String s, float size, Font f) { return s.length() * size * 0.55f; }
    }

    enum Mode { SMOOTH, SNAP }
    static final class Mod extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 3.0, 0, 6, 0.5));
        final Setting.Bool flag = add(new Setting.Bool("Flag", false));
        final Setting.Choice<Mode> mode = add(new Setting.Choice<>("Mode", Mode.class, Mode.SMOOTH));
        final Setting.Color color = add(new Setting.Color("Colour", 0xFFFF0000));
        final Setting.Text text = add(new Setting.Text("Label", "", 8));
        Mod(String n, Category c) { super(n, "desc " + n, c, Risk.LOW); }
    }

    static ClickGuiView frame(ClickGuiView v) {
        NullBackend nb = new NullBackend();
        RasterGfx g = new RasterGfx(nb, 1407, 840);
        v.render(g, 1000);
        return v;
    }

    public static int[] runAll() {
        passed = 0; failed = 0;
        gui(); editor(); viewport();
        return new int[] {passed, failed};
    }
    public static void main(String[] a) {
        int[] r = runAll();
        System.out.println("\n" + r[0] + " passed, " + r[1] + " failed");
        if (r[1] > 0) System.exit(1);
    }

    static void gui() {
        ModuleManager mm = new ModuleManager();
        Mod a = new Mod("Alpha", Category.COMBAT), b = new Mod("Beta", Category.COMBAT);
        mm.register(a); mm.register(b);
        for (int i = 0; i < 14; i++) mm.register(new Mod("Move" + i, Category.MOVEMENT));
        LayoutModel layout = HudRenderer.defaultLayout(1407, 840);
        ClickGuiView v = new ClickGuiView(mm, layout);
        List<Integer> changes = new ArrayList<>();
        v.onChanged = () -> changes.add(1);
        frame(v);

        double rowY0 = 93 + 59, x = 25 + 100;
        v.mouseDown(x, rowY0, 0); frame(v);
        check("gui: left-click on a row toggles the module", a.isEnabled() && !changes.isEmpty());
        v.mouseDown(x, rowY0, 0); frame(v);
        check("gui: second click toggles it off", !a.isEnabled());
        v.mouseDown(x, rowY0 + 29.2, 1); frame(v);
        check("gui: right-click selects a module for the settings card", v.selected() == b);
        v.mouseDown(x, rowY0, 2); frame(v);
        check("gui: middle-click favourites", v.isFavorite(a));
        v.mouseDown(x, rowY0, 2); frame(v);
        check("gui: middle-click again un-favourites", !v.isFavorite(a));
        v.mouseDown(x, rowY0, 0); frame(v); v.mouseDown(x, rowY0 + 29.2, 0); frame(v);
        check("gui: recently used list tracks toggles (most recent first)", v.recents().get(0).equals("Beta") && v.recents().contains("Alpha"));
        a.setEnabled(false); b.setEnabled(false);

        // drag a panel header
        double hx = 25 + 100, hy = 93 + 20;
        double px0 = v.panelX(Category.COMBAT);
        v.mouseDown(hx, hy, 0); v.mouseDragged(hx + 60, hy + 40); v.mouseUp(); frame(v);
        check("gui: dragging a panel header moves the panel", v.panelX(Category.COMBAT) == px0 + 60 && v.panelY(Category.COMBAT) == 93 + 40);
        v.mouseDown(hx + 60, hy + 40, 1); frame(v);
        check("gui: right-click on header resets the panel position", v.panelX(Category.COMBAT) == px0 && v.panelY(Category.COMBAT) == 93);
        v.mouseDown(hx, hy, 0); v.mouseDragged(-5000, -5000); v.mouseUp();
        check("gui: panels cannot be dragged off-screen", v.panelX(Category.COMBAT) >= -214 + 60 && v.panelY(Category.COMBAT) >= 0);
        v.resetPanelPositions();

        // scrolling a long category
        v.scroll(25 + 229 + 100, 300, -1); frame(v);
        v.scroll(25 + 229 + 100, 300, -1); frame(v);
        double mrow = 93 + 59;
        v.mouseDown(25 + 229 + 100, mrow, 0); frame(v);
        check("gui: scrolled list clicks hit the right row (Move2 toggled)", mm.get("Move2").isEnabled() && !mm.get("Move0").isEnabled());
        v.scroll(25 + 229 + 100, 300, 1); v.scroll(25 + 229 + 100, 300, 1); frame(v);
        v.mouseDown(25 + 229 + 100, mrow, 0); frame(v);
        check("gui: scrolling back up restores the top rows", mm.get("Move0").isEnabled());
        for (int i = 0; i < 20; i++) v.scroll(25 + 229 + 100, 300, -1);
        frame(v);
        v.mouseDown(25 + 229 + 100, 93 + 59 + 9 * 29.2, 0); frame(v);
        check("gui: scroll is clamped at the end (last row Move13 is reachable, bottom row is the last module)", mm.get("Move13").isEnabled());

        // settings card: select Alpha, drag slider, bool, choice, colour
        v.mouseDown(x, rowY0, 1); frame(v);
        v.mouseDown(25 + 20 + 130, 459 + 61 + 18, 0); v.mouseUp(); frame(v);
        check("gui: clicking the slider sets the value (snapped to step)", a.range.get() == 3.0);
        v.mouseDown(25 + 20 + 261, 459 + 61 + 18, 0); v.mouseUp(); frame(v);
        check("gui: slider right edge gives max", a.range.get() == 6.0);
        v.mouseDown(25 + 20, 459 + 61 + 18, 0); v.mouseUp(); frame(v);
        check("gui: slider left edge gives min", a.range.get() == 0.0);
        v.mouseDown(25 + 20 + 288 + 261 - 10, 459 + 61, 0); v.mouseUp(); frame(v);
        check("gui: bool setting toggles", a.flag.get());
        v.mouseDown(25 + 20 + 261 - 20, 459 + 61 + 47, 0); v.mouseUp(); frame(v);
        check("gui: dropdown cycles to the next value", a.mode.get() == Mode.SNAP);
        v.mouseDown(25 + 20 + 261 - 20, 459 + 61 + 47, 1); v.mouseUp(); frame(v);
        check("gui: right-click cycles backwards", a.mode.get() == Mode.SMOOTH);
        double swx = 25 + 20 + 288 + 261 - (8 * 17 + 7 * 3) + 3 * 20 + 8;
        v.mouseDown(swx, 459 + 61 + 47, 0); v.mouseUp(); frame(v);
        check("gui: colour swatch selects a preset (index 3 = cyan)", (a.color.argb() & 0xFFFFFF) == (Setting.Color.PRESETS[3] & 0xFFFFFF));

        // text field editing
        v.mouseDown(25 + 20 + 261 - 20, 459 + 61 + 94, 0); frame(v);
        check("gui: clicking a text setting starts editing", v.isEditingText() && v.wantsKeyboard());
        v.charTyped('H'); v.charTyped('i');
        check("gui: typing edits the text", a.text.get().equals("Hi"));
        v.keyPressed(GuiKeys.BACKSPACE);
        check("gui: backspace deletes", a.text.get().equals("H"));
        for (int i = 0; i < 20; i++) v.charTyped('x');
        check("gui: text length limit enforced", a.text.get().length() <= 8);
        v.keyPressed(GuiKeys.ENTER);
        check("gui: enter finishes editing", !v.isEditingText());

        // keybinds
        double chipX = 25 + 589 - 20 - 12, chipY = 459 + 29;
        v.mouseDown(chipX, chipY, 0); frame(v);
        check("gui: clicking the keybind chip starts capture", v.isCapturingKey());
        v.keyPressed(82); frame(v);
        check("gui: key press binds and ends capture", a.getKeyCode() == 82 && !v.isCapturingKey());
        v.select(b); frame(v);
        v.mouseDown(chipX, chipY, 0); v.keyPressed(82); frame(v);
        check("gui: conflicting key is refused first (no rebind yet)", b.getKeyCode() == -1 && a.getKeyCode() == 82 && v.isCapturingKey());
        v.keyPressed(82); frame(v);
        check("gui: pressing it again replaces the old binding", b.getKeyCode() == 82 && a.getKeyCode() == -1 && !v.isCapturingKey());
        v.mouseDown(chipX, chipY, 0); v.keyPressed(GuiKeys.ESCAPE);
        check("gui: escape cancels capture and keeps the bind", !v.isCapturingKey() && b.getKeyCode() == 82);
        v.mouseDown(chipX, chipY, 0); v.keyPressed(GuiKeys.BACKSPACE);
        check("gui: backspace unbinds", b.getKeyCode() == -1);

        // search
        v.charTyped('b'); v.charTyped('e');
        check("gui: typing searches", v.search().equals("be"));
        v.keyPressed(GuiKeys.BACKSPACE);
        check("gui: backspace edits search", v.search().equals("b"));
        v.keyPressed(GuiKeys.ESCAPE);
        check("gui: escape clears search", v.search().isEmpty());

        // overlay widget toggles
        Widget fps = layout.get(HudRenderer.FPS);
        boolean was = fps.visible;
        v.mouseDown(629 + 100, 459 + 64, 0); frame(v);
        check("gui: overlay card toggles widget visibility", fps.visible != was);

        // non-GUI click
        check("gui: click on empty space is not consumed", !v.mouseDown(700, 800, 0));
        check("gui: renders without exceptions with zero modules", renderEmpty());
    }

    static boolean renderEmpty() {
        try { frame(new ClickGuiView(new ModuleManager(), HudRenderer.defaultLayout(1407, 840))); return true; }
        catch (RuntimeException e) { return false; }
    }

    static void editor() {
        LayoutModel layout = HudRenderer.defaultLayout(1407, 840);
        EditorView ev = new EditorView(layout, new HudData() {
            public int fps() { return 1; } public int ping() { return 1; } public double x() { return 0; } public double y() { return 0; }
            public double z() { return 0; } public float yaw() { return 0; } public boolean inNether() { return false; }
            public List<Effect> effects() { return List.of(); } public List<ArmourPiece> armour() { return List.of(); }
            public List<ModuleDot> activeModules() { return List.of(); } public boolean keyDown(Key k) { return false; }
            public int cpsLeft() { return 0; } public int cpsRight() { return 0; } public int minimapColor(int dx, int dz) { return 0; }
        }, 1407, 840);
        NullBackend nb = new NullBackend();
        ev.render(new RasterGfx(nb, 1407, 840));
        boolean[] done = {false}, changed = {false};
        ev.onDone = () -> done[0] = true; ev.onChanged = () -> changed[0] = true;

        Widget fps = layout.get(HudRenderer.FPS);
        double sx = fps.x + 20, sy = fps.y + 20;
        ev.mouseDown(sx, sy, 0); ev.mouseDragged(sx + 160, sy + 100); ev.mouseUp();
        check("editor: dragging a widget moves it (snapped to the grid)", fps.x > 100 && fps.x % 8 == 0 && fps.y % 8 == 0 && layout.selected() == fps);
        check("editor: a drag reports a change so the layout gets saved", changed[0]);
        double w0 = fps.w, h0 = fps.h;
        ev.mouseDown(fps.x + fps.w, fps.y + fps.h, 0); ev.mouseDragged(fps.x + fps.w + 40, fps.y + fps.h + 24); ev.mouseUp();
        check("editor: dragging a corner handle resizes", fps.w > w0 && fps.h > h0);
        Widget keys = layout.get(HudRenderer.KEYS);
        check("editor: widgets hidden in the HUD can still be picked and moved", !keys.visible && layout.pick(keys.x + 5, keys.y + 5, true) == keys);

        ev.mouseDown(700, 400, 0);
        check("editor: clicking empty space deselects", layout.selected() == null);

        ev.render(new RasterGfx(nb, 1407, 840));
        double ty = 840 * (710.0 / 840) + 21;
        // toolbar chips: find by scanning x for Snap chip
        boolean snapBefore = layout.isSnap();
        boolean hit = false;
        for (double x = 700; x < 990 && !hit; x += 4) {
            ev.mouseDown(x, ty, 0);
            if (layout.isSnap() != snapBefore) hit = true;
        }
        check("editor: 'Snap to grid' chip toggles snapping", hit);
        double fx = fps.x;
        for (double x = 700; x < 990; x += 4) ev.mouseDown(x, ty, 0); // sweeps Reset and Done chips too
        check("editor: 'Reset' restores default positions", fps.x != fx);
        check("editor: 'Done' fires the callback", done[0]);
        done[0] = false;
        check("editor: Escape leaves the editor", ev.keyPressed(GuiKeys.ESCAPE) && done[0]);

        // HUD renderer: nothing crashes for each widget with empty/odd data, at tiny and huge sizes
        LayoutModel l2 = HudRenderer.defaultLayout(300, 200);
        l2.widgets().forEach(w -> w.visible = true);
        try { HudRenderer.draw(new RasterGfx(nb, 300, 200), l2, new HudData() {
            public int fps() { return 0; } public int ping() { return -1; } public double x() { return 3e7; } public double y() { return -64; }
            public double z() { return -3e7; } public float yaw() { return 1234f; } public boolean inNether() { return true; }
            public List<Effect> effects() { return List.of(new Effect("x", 0, -5)); } public List<ArmourPiece> armour() { return List.of(new ArmourPiece("h", 0, 500)); }
            public List<ModuleDot> activeModules() { return List.of(); } public boolean keyDown(Key k) { return true; }
            public int cpsLeft() { return 99; } public int cpsRight() { return 99; } public int minimapColor(int dx, int dz) { return 0xFF112233; }
        }, false, null); check("hud: renders extreme data on a tiny screen without exceptions", true); }
        catch (RuntimeException e) { check("hud: renders extreme data on a tiny screen without exceptions: " + e, false); }
    }

    static void viewport() {
        Viewport v = Viewport.fit(2814, 1680, 1407, 840, 1.0);
        check("viewport: 2x window gives scale 2 and no offset", Math.abs(v.scale - 2) < 1e-9 && Math.abs(v.offX) < 1e-9);
        Viewport w = Viewport.fit(1920, 1080, 1407, 840, 1.0);
        check("viewport: wide window letterboxes horizontally and stays centred", w.offX > 0 && Math.abs(w.offY) < 1e-6 && Math.abs(w.toDesignX(1920 / 2.0) - 703.5) < 1e-6);
        Viewport u = Viewport.fit(1407, 840, 1407, 840, 0.5);
        check("viewport: user GUI-scale multiplier shrinks the layout", Math.abs(u.scale - 0.5) < 1e-9);
        check("viewport: mouse mapping round-trips", Math.abs(w.toDesignY(w.offY + w.scale * 400) - 400) < 1e-9);
        check("viewport: absurd scale values are clamped", Viewport.fit(100, 100, 1407, 840, 0).scale >= 0.25);
    }
}
