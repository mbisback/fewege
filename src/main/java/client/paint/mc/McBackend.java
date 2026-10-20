package client.paint.mc;

import client.paint.core.gui.Gfx.Font;
import client.paint.core.gui.RasterGfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Draws RasterGfx output with Minecraft's GUI graphics. Only fill() and text() are needed.
 * VERIFY (26.3): GuiGraphicsExtractor#fill(x1,y1,x2,y2,argb) is confirmed by the Fabric docs. The text call
 * (drawString) and the pose() matrix calls are NOT confirmed for 26.x - if they do not compile, check the
 * "Drawing to the GUI" page on docs.fabricmc.net for the current names (e.g. text(...)).
 */
final class McBackend implements RasterGfx.Backend {
    private static final float FONT_HEIGHT = 9f;
    private final GuiGraphicsExtractor g;
    private final net.minecraft.client.gui.Font font;

    McBackend(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font) { this.g = g; this.font = font; }

    @Override public void fill(int x, int y, int w, int h, int argb) { g.fill(x, y, x + w, y + h, argb); }

    private static Component comp(String s, Font f) {
        boolean bold = f == Font.BOLD || f == Font.MONO_BOLD;
        return bold ? Component.literal(s).withStyle(Style.EMPTY.withBold(true)) : Component.literal(s);
    }

    @Override public void text(String s, float x, float baselineY, float size, Font f, int argb) {
        float scale = size / FONT_HEIGHT;
        float top = baselineY - 7f * scale;
        g.pose().pushMatrix();                  // VERIFY: Matrix3x2fStack (1.21.6+)
        g.pose().translate(x, top);
        g.pose().scale(scale, scale);
        g.text(font, comp(s, f), 0, 0, argb, false);   // VERIFY: name/signature in 26.x
        g.pose().popMatrix();
    }

    @Override public float textWidth(String s, float size, Font f) { return font.width(comp(s, f)) * (size / FONT_HEIGHT); }
}
