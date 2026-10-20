package client.paint.tools;

import client.paint.core.gui.Gfx.Font;
import client.paint.core.gui.RasterGfx;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Preview-only backend: draws RasterGfx output into a BufferedImage (used to check the GUI offline). */
public final class Java2DBackend implements RasterGfx.Backend {
    public final BufferedImage img;
    private final Graphics2D g2;

    public Java2DBackend(int w, int h) {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private java.awt.Font font(Font f, float size) {
        String family = (f == Font.MONO || f == Font.MONO_BOLD) ? java.awt.Font.MONOSPACED : java.awt.Font.SANS_SERIF;
        int style = (f == Font.BOLD || f == Font.MONO_BOLD) ? java.awt.Font.BOLD : java.awt.Font.PLAIN;
        return new java.awt.Font(family, style, 1).deriveFont(size);
    }

    @Override public void fill(int x, int y, int w, int h, int argb) {
        g2.setColor(new Color(argb, true));
        g2.fillRect(x, y, w, h);
    }

    @Override public void text(String s, float x, float baselineY, float size, Font f, int argb) {
        g2.setFont(font(f, size));
        g2.setColor(new Color(argb, true));
        g2.drawString(s, x, baselineY);
    }

    @Override public float textWidth(String s, float size, Font f) {
        return (float) g2.getFontMetrics(font(f, size)).getStringBounds(s, g2).getWidth();
    }
}
