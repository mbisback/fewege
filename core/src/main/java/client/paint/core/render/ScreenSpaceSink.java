package client.paint.core.render;

import client.paint.core.game.RenderSink;
import client.paint.core.gui.Gfx;
import client.paint.core.gui.Gfx.Font;

/**
 * {@link RenderSink} that draws ESP in screen space through {@link Gfx}. Boxes are 12 projected edges
 * (clipped to the near plane so objects partly behind you still draw), always visible through walls.
 */
public final class ScreenSpaceSink implements RenderSink {
    private static final int[][] EDGES = {
        {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private final Gfx g;
    private final Projection p;
    private final double[][] cam = new double[8][3];
    private final double[] s0 = new double[2], s1 = new double[2];

    public ScreenSpaceSink(Gfx g, Projection p) { this.g = g; this.p = p; }

    @Override public double camX() { return p.camX(); }
    @Override public double camY() { return p.camY(); }
    @Override public double camZ() { return p.camZ(); }

    private void corners(double x0, double y0, double z0, double x1, double y1, double z1) {
        for (int i = 0; i < 8; i++) {
            p.toCamera((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1, cam[i]);
        }
    }

    @Override public boolean inFrustum(double x0, double y0, double z0, double x1, double y1, double z1) {
        corners(x0, y0, z0, x1, y1, z1);
        boolean anyFront = false, anyBehind = false;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] c : cam) {
            if (c[2] < Projection.NEAR) { anyBehind = true; continue; }
            anyFront = true;
            p.cameraToScreen(c[0], c[1], c[2], s0);
            minX = Math.min(minX, s0[0]); maxX = Math.max(maxX, s0[0]);
            minY = Math.min(minY, s0[1]); maxY = Math.max(maxY, s0[1]);
        }
        if (!anyFront) return false;
        if (anyBehind) return true; // conservative: the box straddles the camera plane
        return maxX >= 0 && minX <= p.width() && maxY >= 0 && minY <= p.height();
    }

    @Override public void box(double x0, double y0, double z0, double x1, double y1, double z1,
                              int outline, int fill, float thickness, boolean throughWalls) {
        corners(x0, y0, z0, x1, y1, z1);
        boolean allFront = true;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] c : cam) {
            if (c[2] < Projection.NEAR) { allFront = false; break; }
            p.cameraToScreen(c[0], c[1], c[2], s0);
            minX = Math.min(minX, s0[0]); maxX = Math.max(maxX, s0[0]);
            minY = Math.min(minY, s0[1]); maxY = Math.max(maxY, s0[1]);
        }
        if (allFront && ((fill >>> 24) != 0)) g.fillRect(minX, minY, maxX - minX, maxY - minY, fill);
        for (int[] e : EDGES) segment(cam[e[0]], cam[e[1]], outline, thickness);
    }

    /** Draw a camera-space segment, clipped to the near plane. */
    private void segment(double[] a, double[] b, int argb, double thickness) {
        double az = a[2], bz = b[2];
        double[] pa = a, pb = b;
        if (az < Projection.NEAR && bz < Projection.NEAR) return;
        if (az < Projection.NEAR) pa = clip(a, b);
        else if (bz < Projection.NEAR) pb = clip(b, a);
        p.cameraToScreen(pa[0], pa[1], pa[2], s0);
        p.cameraToScreen(pb[0], pb[1], pb[2], s1);
        if (Math.abs(s0[0]) > 1e5 || Math.abs(s0[1]) > 1e5 || Math.abs(s1[0]) > 1e5 || Math.abs(s1[1]) > 1e5) return;
        g.line(s0[0], s0[1], s1[0], s1[1], Math.max(1, thickness), argb);
    }

    /** Point on segment (behind -> front) where forward == NEAR. */
    private static double[] clip(double[] behind, double[] front) {
        double t = (Projection.NEAR - behind[2]) / (front[2] - behind[2]);
        return new double[] {behind[0] + (front[0] - behind[0]) * t, behind[1] + (front[1] - behind[1]) * t, Projection.NEAR};
    }

    @Override public void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float thickness) {
        double[] a = new double[3], b = new double[3];
        p.toCamera(x1, y1, z1, a);
        p.toCamera(x2, y2, z2, b);
        segment(a, b, argb, thickness);
    }

    @Override public void lineFromCrosshair(double x, double y, double z, int argb, float thickness) {
        if (!p.project(x, y, z, s0)) return;
        g.line(p.width() / 2, p.height() / 2, s0[0], s0[1], Math.max(1, thickness), argb);
    }

    @Override public void label(String text, double x, double y, double z, int argb, float scale) {
        if (!p.project(x, y, z, s0)) return;
        double size = 11 * scale, tw = g.textWidth(text, size, Font.SANS, 0);
        double lx = s0[0] - tw / 2, ly = s0[1];
        g.fillRoundRect(lx - 4, ly - size, tw + 8, size + 5, 4, 0x99000000);
        g.text(text, lx, ly, size, argb, Font.SANS, 0);
    }
}
