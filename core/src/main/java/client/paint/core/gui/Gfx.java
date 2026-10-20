package client.paint.core.gui;

/**
 * Minimal 2D drawing interface the GUI is written against. Coordinates are "design units"
 * (the reference screenshots are 1407x840). Implementations: {@link RasterGfx} (pure Java, used by
 * the Minecraft adapter and by the PNG preview/tests).
 */
public interface Gfx {
    enum Font { SANS, BOLD, MONO, MONO_BOLD }

    void fillRect(double x, double y, double w, double h, int argb);
    void fillRoundRect(double x, double y, double w, double h, double r, int argb);
    void strokeRoundRect(double x, double y, double w, double h, double r, double thickness, int argb);
    void dashedRoundRect(double x, double y, double w, double h, double r, double thickness,
                         double dash, double gap, int argb);
    void fillCircle(double cx, double cy, double r, int argb);
    void fillPolygon(double[] xs, double[] ys, int argb);
    void line(double x1, double y1, double x2, double y2, double thickness, int argb);

    /** Soft elliptical glow, {@code argb}'s alpha is the centre opacity, fading to 0 at the edge. */
    void radialGlow(double cx, double cy, double rx, double ry, int argb);
    void verticalGradient(double x, double y, double w, double h, int topArgb, int bottomArgb);

    /** Text with its baseline at {@code baselineY}; {@code spacing} adds extra px between characters. */
    void text(String s, double x, double baselineY, double size, int argb, Font font, double spacing);
    double textWidth(String s, double size, Font font, double spacing);

    void pushClip(double x, double y, double w, double h);
    void popClip();

    /** Local coordinate system: (0,0) becomes (tx,ty) in the parent, scaled by {@code scale}. */
    void pushTransform(double tx, double ty, double scale);
    void popTransform();

    /** Visible area in current design coordinates: {x0, y0, x1, y1}. */
    double[] visibleBounds();

    // ---- convenience ----
    default void text(String s, double x, double baselineY, double size, int argb) {
        text(s, x, baselineY, size, argb, Font.SANS, 0);
    }

    /** Text vertically centred on {@code centerY}. */
    default void textMid(String s, double x, double centerY, double size, int argb, Font font, double spacing) {
        text(s, x, centerY + size * 0.36, size, argb, font, spacing);
    }

    default void textRight(String s, double rightX, double centerY, double size, int argb, Font font, double spacing) {
        textMid(s, rightX - textWidth(s, size, font, spacing), centerY, size, argb, font, spacing);
    }
}
