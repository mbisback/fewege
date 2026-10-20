package client.paint.core.gui;

/** Maps design coordinates to device pixels (uniform scale + centring) and back for mouse input. */
public final class Viewport {
    public final double scale, offX, offY;

    public Viewport(double scale, double offX, double offY) {
        this.scale = scale; this.offX = offX; this.offY = offY;
    }

    public static Viewport identity() { return new Viewport(1, 0, 0); }

    /** Fit a designW x designH layout into the window, centred, with a user GUI-scale multiplier. */
    public static Viewport fit(double winW, double winH, double designW, double designH, double userScale) {
        double s = Math.min(winW / designW, winH / designH) * Math.max(0.25, userScale);
        s = Math.max(0.25, s);
        return new Viewport(s, (winW - designW * s) / 2, (winH - designH * s) / 2);
    }

    public double toDesignX(double deviceX) { return (deviceX - offX) / scale; }
    public double toDesignY(double deviceY) { return (deviceY - offY) / scale; }
}
