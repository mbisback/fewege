package client.paint.core.gui;

import client.paint.core.gui.Gfx.Font;
import client.paint.core.nametag.NametagStyle;

/** Draws the styled self nametag (screen pixels). Returns the drawn width so callers can position/test it. */
public final class SelfNametagRenderer {
    private SelfNametagRenderer() {}

    /** Draws centred at (cx, baselineY). @return {width, height} of the tag box. */
    public static double[] draw(Gfx g, NametagStyle st, String realName, double cx, double baselineY, double distanceScale) {
        String text = st.resolveText(realName);
        if (text.isEmpty()) return new double[] {0, 0};
        double size = 12 * st.scale * Math.max(0.4, Math.min(2.5, distanceScale));
        Font font = st.bold ? Font.BOLD : Font.SANS;
        double tw = g.textWidth(text, size, font, 0);
        double px = st.paddingX * st.scale, py = st.paddingY * st.scale;
        double w = tw + 2 * px + 2, h = size + 2 * py + 2;
        double x = cx - w / 2, y = baselineY - size - py - 1;
        if ((st.backgroundArgb >>> 24) != 0) g.fillRoundRect(x, y, w, h, 3, st.backgroundArgb);
        double tx = x + px + 1, ty = baselineY;
        int n = text.length();
        double pos = tx;
        for (int i = 0; i < n; i++) {
            String ch = String.valueOf(text.charAt(i));
            int col = st.colorAt(i, n);
            if (st.outline) {
                int oc = 0xFF000000;
                g.text(ch, pos - 1, ty, size, oc, font, 0); g.text(ch, pos + 1, ty, size, oc, font, 0);
                g.text(ch, pos, ty - 1, size, oc, font, 0); g.text(ch, pos, ty + 1, size, oc, font, 0);
            } else if (st.shadow) {
                g.text(ch, pos + 1, ty + 1, size, 0xAA000000, font, 0);
            }
            g.text(ch, pos, ty, size, col, font, 0);
            pos += g.textWidth(ch, size, font, 0);
        }
        return new double[] {w, h};
    }
}
