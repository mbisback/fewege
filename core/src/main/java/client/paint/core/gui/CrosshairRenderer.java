package client.paint.core.gui;

/** Custom crosshair drawing (screen pixels). */
public final class CrosshairRenderer {
    private CrosshairRenderer() {}

    public static void draw(Gfx g, double cx, double cy, double size, double gap, double thickness, int argb,
                            boolean dot, boolean tStyle, boolean showCooldown, double cooldown) {
        double t = Math.max(1, thickness), half = t / 2;
        int shadow = 0x66000000;
        arms(g, cx + 0.5, cy + 0.5, size, gap, t, shadow, tStyle);          // soft drop shadow for contrast
        arms(g, cx, cy, size, gap, t, argb, tStyle);
        if (dot) g.fillRect(cx - half, cy - half, t, t, argb);
        if (showCooldown && cooldown < 0.999) {
            double w = (size + gap) * 2, f = Math.max(0, Math.min(1, cooldown));
            double y = cy + size + gap + 5;
            g.fillRoundRect(cx - w / 2, y, w, 3, 1.5, 0x66000000);
            g.fillRoundRect(cx - w / 2, y, w * f, 3, 1.5, argb);
        }
    }

    private static void arms(Gfx g, double cx, double cy, double size, double gap, double t, int argb, boolean tStyle) {
        double half = t / 2;
        g.fillRect(cx - gap - size, cy - half, size, t, argb);   // left
        g.fillRect(cx + gap, cy - half, size, t, argb);          // right
        g.fillRect(cx - half, cy + gap, t, size, argb);          // down
        if (!tStyle) g.fillRect(cx - half, cy - gap - size, t, size, argb); // up (omitted for T-style)
    }
}
