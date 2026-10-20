package client.paint.core.gui;

import client.paint.core.gui.Gfx.Font;
import client.paint.core.hud.NotificationCenter;

import java.util.List;

/** Draws active notifications as small pills in the top-right corner (screen pixels). */
public final class NotificationRenderer {
    private NotificationRenderer() {}

    public static void draw(Gfx g, NotificationCenter nc, long nowMs, double screenW) {
        List<NotificationCenter.Note> list = nc.active(nowMs);
        double y = 14;
        for (NotificationCenter.Note n : list) {
            double age = nowMs - n.createdMs();
            double fade = age > nc.ttlMs - 400 ? Math.max(0, (nc.ttlMs - age) / 400.0) : 1;
            int a = (int) Math.round(255 * fade);
            double tw = g.textWidth(n.text(), 12, Font.SANS, 0), w = tw + 28, x = screenW - w - 14;
            g.fillRoundRect(x, y, w, 26, 13, ((int) Math.round(0xE6 * fade) << 24) | 0x0D0F13);
            g.strokeRoundRect(x, y, w, 26, 13, 1, (a << 24) | 0x23272E);
            g.fillCircle(x + 14, y + 13, 3.5, (a << 24) | 0xFF6E5A);
            g.textMid(n.text(), x + 24, y + 13, 12, (a << 24) | 0xE9EBEF, Font.SANS, 0);
            y += 32;
        }
    }
}
