package client.paint.core.gui;

import client.paint.core.gui.Gfx.Font;
import client.paint.core.layout.LayoutModel;
import client.paint.core.layout.LayoutModel.Handle;
import client.paint.core.layout.LayoutModel.Widget;

/**
 * Overlay (layout) editor, Reference 2. Works in screen pixels of the layout's screen size; the bottom
 * toolbar is centred. Drag a widget to move it, drag a corner handle of the selected widget to resize,
 * click "Snap to grid" to toggle snapping, "Reset" to restore defaults, "Done" to leave (and save).
 */
public final class EditorView {
    private final LayoutModel layout;
    private final HudData data;
    private final double screenW, screenH;

    /** Draw the dark gradient backdrop (preview / when no world is behind the screen). */
    public boolean drawBackdrop = true;
    public Runnable onDone = () -> { };
    public Runnable onChanged = () -> { };

    private enum Mode { NONE, MOVE, RESIZE }
    private Mode mode = Mode.NONE;
    private Handle handle;
    private double lastX, lastY;

    private double toolbarX, toolbarY, toolbarW, toolbarH;
    private double snapX, snapW, resetX, resetW, doneX, doneW, chipY, chipH;

    public EditorView(LayoutModel layout, HudData data, double screenW, double screenH) {
        this.layout = layout; this.data = data; this.screenW = screenW; this.screenH = screenH;
        computeToolbar();
    }

    private void computeToolbar() {
        double k = Math.max(0.75, Math.min(1.5, Math.min(screenW / 1407.0, screenH / 840.0)));
        toolbarW = 566 * k; toolbarH = 42 * k;
        toolbarX = (screenW - toolbarW) / 2; toolbarY = screenH * (710.0 / 840.0);
        chipH = 26 * k; chipY = toolbarY + (toolbarH - chipH) / 2;
        snapW = 116 * k; resetW = 56 * k; doneW = 56 * k;
        doneX = toolbarX + toolbarW - 12 * k - doneW;
        resetX = doneX - 12 * k - resetW;
        snapX = resetX - 14 * k - snapW;
    }

    public void render(Gfx g) {
        double k = Math.max(0.75, Math.min(1.5, Math.min(screenW / 1407.0, screenH / 840.0)));
        if (drawBackdrop) backdrop(g);

        // crosshair
        double cx = screenW / 2, cy = screenH * (419.0 / 840.0);
        g.fillRect(cx - 12, cy - 1, 24, 2, 0xE6FFFFFF);
        g.fillRect(cx - 1, cy - 12, 2, 24, 0xE6FFFFFF);

        HudRenderer.draw(g, layout, data, true, layout.selected());

        // toolbar pill
        g.fillRoundRect(toolbarX, toolbarY, toolbarW, toolbarH, toolbarH / 2, 0xF2100C18);
        g.strokeRoundRect(toolbarX, toolbarY, toolbarW, toolbarH, toolbarH / 2, 1, 0xFF2E2A38);
        double ty = toolbarY + toolbarH / 2, s = 13 * k;
        g.textMid("Layout editor", toolbarX + 15 * k, ty, s, Theme.TEXT, Font.BOLD, 0);
        g.textMid("Drag widgets to move them", toolbarX + 113 * k, ty, 12 * k, Theme.MUTED, Font.SANS, 0);
        chip(g, snapX, snapW, "Snap to grid: " + (layout.isSnap() ? "on" : "off"), 0xFF24222B, Theme.TEXT, k);
        chip(g, resetX, resetW, "Reset", 0xFF24222B, Theme.TEXT, k);
        chip(g, doneX, doneW, "Done", Theme.CORAL, 0xFF2A0F0A, k);

        hotbar(g, k);
    }

    private void chip(Gfx g, double x, double w, String label, int fill, int text, double k) {
        g.fillRoundRect(x, chipY, w, chipH, chipH / 2, fill);
        double tw = g.textWidth(label, 12 * k, Font.BOLD, 0);
        g.textMid(label, x + (w - tw) / 2, chipY + chipH / 2, 12 * k, text, Font.BOLD, 0);
    }

    private void hotbar(Gfx g, double k) {
        double w = 357 * k, h = 44 * k, x = (screenW - w) / 2, y = screenH * (771.0 / 840.0);
        g.fillRoundRect(x, y, w, h, 4, 0xB3120E18);
        g.strokeRoundRect(x, y, w, h, 4, 1, 0xFF2A2733);
        for (int i = 0; i < 9; i++) {
            double sx = x + 5 * k + i * 39 * k, sy = y + 6 * k, sz = 34 * k;
            g.fillRoundRect(sx, sy, sz, sz, 3, i == 0 ? 0xFF4A4A4E : 0xFF1C1A21);
            if (i == 0) g.strokeRoundRect(sx - 1, sy - 1, sz + 2, sz + 2, 3, 2, 0xFFFFFFFF);
        }
    }

    private void backdrop(Gfx g) {
        g.fillRect(0, 0, screenW, screenH, 0xFF1A0F26);
        g.verticalGradient(0, 0, screenW, screenH * 0.5, 0xFF22143A, 0xFF2A1332);
        g.verticalGradient(0, screenH * 0.5, screenW, screenH * 0.28, 0xFF2A1332, 0xFF33151C);
        g.verticalGradient(0, screenH * 0.78, screenW, screenH * 0.22, 0xFF33151C, 0xFF1A0E0B);
        g.radialGlow(screenW * 0.25, screenH * 0.2, screenW * 0.4, screenH * 0.35, 0x66402068);
        g.radialGlow(screenW * 0.5, screenH * 0.88, screenW * 0.32, screenH * 0.14, 0x995A2A1A);
    }

    // ------------------------------------------------------------------ input (screen pixels)

    /** @return true if consumed. */
    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0) return false;
        if (my >= chipY && my <= chipY + chipH) {
            if (mx >= snapX && mx <= snapX + snapW) { layout.setSnap(!layout.isSnap()); onChanged.run(); return true; }
            if (mx >= resetX && mx <= resetX + resetW) { layout.resetAll(); onChanged.run(); return true; }
            if (mx >= doneX && mx <= doneX + doneW) { onChanged.run(); onDone.run(); return true; }
        }
        Handle h = layout.handleAt(mx, my, 7);
        if (h != null) { mode = Mode.RESIZE; handle = h; lastX = mx; lastY = my; return true; }
        Widget w = layout.pick(mx, my, true);
        if (w != null) { mode = Mode.MOVE; lastX = mx; lastY = my; return true; }
        mode = Mode.NONE;
        return false;
    }

    public void mouseDragged(double mx, double my) {
        Widget w = layout.selected();
        if (w == null || mode == Mode.NONE) return;
        double dx = mx - lastX, dy = my - lastY;
        if (mode == Mode.MOVE) {
            // Accumulate sub-grid motion so snapping doesn't swallow slow drags.
            double ox = w.x, oy = w.y;
            layout.drag(w, dx, dy);
            if (w.x == ox && dx != 0 && layout.isSnap() && Math.abs(dx) < 4) return;
            if (w.y == oy && dy != 0 && layout.isSnap() && Math.abs(dy) < 4) return;
        } else {
            layout.resize(w, handle, dx, dy);
        }
        lastX = mx; lastY = my;
    }

    public void mouseUp() {
        if (mode != Mode.NONE) onChanged.run();
        mode = Mode.NONE;
    }

    public boolean keyPressed(int key) {
        if (key == GuiKeys.ESCAPE) { onChanged.run(); onDone.run(); return true; }
        return false;
    }
}
