package client.paint.core.layout;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Overlay (HUD) layout editor model: selection, drag, resize, grid snapping, hit-testing and
 * resolution-independent persistence. Rendering/mouse input call into this; it holds no UI code.
 * Positions are saved as fractions of the screen so a layout survives resolution/GUI-scale changes.
 */
public final class LayoutModel {
    public enum Handle { NW, NE, SW, SE }

    public static final class Widget {
        public final String id;
        public final String label;
        public final double minW, minH;
        public final double defX, defY, defW, defH; // defaults in fractions of the screen
        public double x, y, w, h;                   // pixels
        public boolean visible = true;      // shown in the in-game HUD
        public boolean defVisible = true;   // value restored by reset

        public Widget(String id, String label, double fx, double fy, double fw, double fh,
                      double minW, double minH) {
            this.id = id; this.label = label; this.minW = minW; this.minH = minH;
            this.defX = fx; this.defY = fy; this.defW = fw; this.defH = fh;
        }

        boolean contains(double px, double py) { return px >= x && px <= x + w && py >= y && py <= y + h; }
    }

    private final List<Widget> widgets = new ArrayList<>(); // later = drawn on top
    private double screenW, screenH;
    private double grid = 8;
    private boolean snap = true;
    private Widget selected;

    public LayoutModel(double screenW, double screenH) {
        this.screenW = screenW; this.screenH = screenH;
    }

    public void add(Widget w) {
        widgets.add(w);
        applyDefault(w);
    }

    public List<Widget> widgets() { return Collections.unmodifiableList(widgets); }
    public Widget get(String id) { for (Widget w : widgets) if (w.id.equals(id)) return w; return null; }
    public Widget selected() { return selected; }
    public boolean isSnap() { return snap; }
    public void setSnap(boolean on) { snap = on; }
    public void setGrid(double g) { if (g >= 1) grid = g; }

    /** Topmost visible widget under the point, or null. Also selects it (and raises it). */
    public Widget pick(double px, double py) { return pick(px, py, false); }

    /** As {@link #pick(double, double)}; the editor passes true so widgets switched off in the HUD can still be moved. */
    public Widget pick(double px, double py, boolean includeHidden) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if ((includeHidden || w.visible) && w.contains(px, py)) {
                selected = w;
                widgets.remove(i);
                widgets.add(w);
                return w;
            }
        }
        selected = null;
        return null;
    }

    /** Which corner handle (within {@code radius} px) of the selected widget is under the point. */
    public Handle handleAt(double px, double py, double radius) {
        if (selected == null) return null;
        Widget w = selected;
        boolean left = Math.abs(px - w.x) <= radius, right = Math.abs(px - (w.x + w.w)) <= radius;
        boolean top = Math.abs(py - w.y) <= radius, bottom = Math.abs(py - (w.y + w.h)) <= radius;
        if (left && top) return Handle.NW;
        if (right && top) return Handle.NE;
        if (left && bottom) return Handle.SW;
        if (right && bottom) return Handle.SE;
        return null;
    }

    /** Move by a mouse delta. Result is snapped to the grid (if on) and kept fully on screen. */
    public void drag(Widget w, double dx, double dy) {
        double nx = w.x + dx, ny = w.y + dy;
        if (snap) { nx = snapV(nx); ny = snapV(ny); }
        w.x = clamp(nx, 0, Math.max(0, screenW - w.w));
        w.y = clamp(ny, 0, Math.max(0, screenH - w.h));
    }

    /** Resize from a corner. Opposite corner stays fixed; minimum size and screen bounds enforced. */
    public void resize(Widget w, Handle handle, double dx, double dy) {
        double left = w.x, top = w.y, right = w.x + w.w, bottom = w.y + w.h;
        boolean west = handle == Handle.NW || handle == Handle.SW;
        boolean north = handle == Handle.NW || handle == Handle.NE;
        if (west) left += dx; else right += dx;
        if (north) top += dy; else bottom += dy;
        if (snap) {
            if (west) left = snapV(left); else right = snapV(right);
            if (north) top = snapV(top); else bottom = snapV(bottom);
        }
        left = Math.max(0, left); top = Math.max(0, top);
        right = Math.min(screenW, right); bottom = Math.min(screenH, bottom);
        if (right - left < w.minW) { if (west) left = right - w.minW; else right = left + w.minW; }
        if (bottom - top < w.minH) { if (north) top = bottom - w.minH; else bottom = top + w.minH; }
        w.x = left; w.y = top; w.w = right - left; w.h = bottom - top;
    }

    /** Call when the window size or GUI scale changes; keeps relative placement. */
    public void setScreenSize(double newW, double newH) {
        if (newW <= 0 || newH <= 0) return;
        for (Widget w : widgets) {
            w.x = w.x / screenW * newW; w.w = w.w / screenW * newW;
            w.y = w.y / screenH * newH; w.h = w.h / screenH * newH;
        }
        screenW = newW; screenH = newH;
    }

    public void resetAll() { widgets.forEach(this::applyDefault); selected = null; }

    private void applyDefault(Widget w) {
        w.x = w.defX * screenW; w.y = w.defY * screenH;
        w.w = Math.max(w.minW, w.defW * screenW); w.h = Math.max(w.minH, w.defH * screenH);
        w.visible = w.defVisible;
    }

    // ---- persistence (fractions of the screen) ----

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("layoutVersion", "1");
        p.setProperty("snap", Boolean.toString(snap));
        for (Widget w : widgets) {
            String b = "widget." + w.id + ".";
            p.setProperty(b + "x", Double.toString(w.x / screenW));
            p.setProperty(b + "y", Double.toString(w.y / screenH));
            p.setProperty(b + "w", Double.toString(w.w / screenW));
            p.setProperty(b + "h", Double.toString(w.h / screenH));
            p.setProperty(b + "visible", Boolean.toString(w.visible));
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { p.store(out, "Paint Client HUD layout"); }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** @return false if the file is missing or unreadable (defaults are kept). Bad values are skipped. */
    public boolean load(Path file) {
        if (!Files.exists(file)) return false;
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(in); }
        catch (IOException | RuntimeException e) { return false; }
        if ("true".equalsIgnoreCase(p.getProperty("snap"))) snap = true;
        else if ("false".equalsIgnoreCase(p.getProperty("snap"))) snap = false;
        for (Widget w : widgets) {
            String b = "widget." + w.id + ".";
            Double fx = frac(p.getProperty(b + "x")), fy = frac(p.getProperty(b + "y"));
            Double fw = frac(p.getProperty(b + "w")), fh = frac(p.getProperty(b + "h"));
            if (fx == null || fy == null || fw == null || fh == null) continue; // keep defaults
            double nw = Math.max(w.minW, fw * screenW), nh = Math.max(w.minH, fh * screenH);
            w.w = Math.min(nw, screenW); w.h = Math.min(nh, screenH);
            w.x = clamp(fx * screenW, 0, screenW - w.w);
            w.y = clamp(fy * screenH, 0, screenH - w.h);
            String v = p.getProperty(b + "visible");
            if ("false".equalsIgnoreCase(v)) w.visible = false; else if ("true".equalsIgnoreCase(v)) w.visible = true;
        }
        return true;
    }

    private static Double frac(String s) {
        if (s == null) return null;
        try { double d = Double.parseDouble(s.trim()); return (Double.isNaN(d) || d < 0 || d > 1) ? null : d; }
        catch (NumberFormatException e) { return null; }
    }

    private double snapV(double v) { return Math.round(v / grid) * grid; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
