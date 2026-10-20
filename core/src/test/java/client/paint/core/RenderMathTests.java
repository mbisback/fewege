package client.paint.core;

import client.paint.core.gui.Gfx.Font;
import client.paint.core.gui.RasterGfx;
import client.paint.core.render.Projection;
import client.paint.core.render.ScreenSpaceSink;

/** Projection maths and screen-space ESP drawing. */
public class RenderMathTests {
    static int passed, failed;
    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS " + name); } else { failed++; System.out.println("FAIL " + name); }
    }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) < eps; }

    static final class Grid implements RasterGfx.Backend {
        final int w, h; final boolean[][] on; int texts;
        Grid(int w, int h) { this.w = w; this.h = h; on = new boolean[h][w]; }
        public void fill(int x, int y, int fw, int fh, int argb) {
            for (int j = y; j < y + fh; j++) for (int i = x; i < x + fw; i++) if (i >= 0 && j >= 0 && i < w && j < h) on[j][i] = true;
        }
        public void text(String s, float x, float y, float size, Font f, int argb) { texts++; }
        public float textWidth(String s, float size, Font f) { return s.length() * size * 0.55f; }
        boolean near(int x, int y, int r) {
            for (int j = y - r; j <= y + r; j++) for (int i = x - r; i <= x + r; i++) if (i >= 0 && j >= 0 && i < w && j < h && on[j][i]) return true;
            return false;
        }
        int count() { int n = 0; for (boolean[] row : on) for (boolean b : row) if (b) n++; return n; }
    }

    public static int[] runAll() {
        passed = 0; failed = 0;
        projection(); sink();
        return new int[] {passed, failed};
    }
    public static void main(String[] a) {
        int[] r = runAll();
        System.out.println("\n" + r[0] + " passed, " + r[1] + " failed");
        if (r[1] > 0) System.exit(1);
    }

    static void projection() {
        Projection p = new Projection();
        p.set(0, 64, 0, 0f, 0f, 70, 1000, 600);
        double[] o = new double[2];
        check("proj: a point straight ahead (+Z at yaw 0) lands at the screen centre", p.project(0, 64, 10, o) && near(o[0], 500, 1e-6) && near(o[1], 300, 1e-6));
        check("proj: a point to the player's right (-X at yaw 0) is right of centre", p.project(-3, 64, 10, o) && o[0] > 500);
        check("proj: a point to the left (+X) is left of centre", p.project(3, 64, 10, o) && o[0] < 500);
        check("proj: a point above is higher on screen (smaller y)", p.project(0, 67, 10, o) && o[1] < 300);
        check("proj: a point below is lower on screen", p.project(0, 61, 10, o) && o[1] > 300);
        check("proj: a point behind the camera is rejected", !p.project(0, 64, -5, o));
        p.project(0, 64 + 10 * Math.tan(Math.toRadians(35)), 10, o);
        check("proj: vertical FOV edge maps to the top of the screen", near(o[1], 0, 1e-6));

        p.set(0, 64, 0, -90f, 0f, 70, 1000, 600); // facing +X
        check("proj: yaw -90 faces +X", p.project(10, 64, 0, o) && near(o[0], 500, 1e-6));
        check("proj: facing +X, +Z is to the right", p.project(10, 64, 3, o) && o[0] > 500);
        p.set(0, 64, 0, 90f, 0f, 70, 1000, 600); // facing -X
        check("proj: yaw 90 faces -X", p.project(-10, 64, 0, o) && near(o[0], 500, 1e-6));
        p.set(0, 64, 0, 180f, 0f, 70, 1000, 600); // facing -Z
        check("proj: yaw 180 faces -Z", p.project(0, 64, -10, o) && near(o[0], 500, 1e-6));

        p.set(0, 64, 0, 0f, 45f, 70, 1000, 600); // looking down 45 degrees
        check("proj: pitch + looks down (a point 45 deg below is at the centre)", p.project(0, 64 - 10, 10, o) && near(o[0], 500, 1e-6) && near(o[1], 300, 1e-6));
        p.set(0, 64, 0, 0f, -45f, 70, 1000, 600);
        check("proj: pitch - looks up", p.project(0, 74, 10, o) && near(o[1], 300, 1e-6));

        p.set(5, 70, -3, 33f, 12f, 90, 1920, 1080);
        double[] c = new double[3];
        p.toCamera(5, 70, -3, c);
        check("proj: the camera position itself is the origin of camera space", near(c[0], 0, 1e-9) && near(c[1], 0, 1e-9) && near(c[2], 0, 1e-9));
        p.set(0, 0, 0, 40f, 20f, 70, 800, 600);
        double[] c2 = new double[3];
        p.toCamera(3, 1, 7, c2);
        check("proj: camera-space preserves distance", near(Math.sqrt(c2[0] * c2[0] + c2[1] * c2[1] + c2[2] * c2[2]), Math.sqrt(9 + 1 + 49), 1e-9));
    }

    static void sink() {
        Grid gr = new Grid(1000, 600);
        RasterGfx g = new RasterGfx(gr, 1000, 600);
        Projection p = new Projection();
        p.set(0, 64, 0, 0f, 0f, 70, 1000, 600);
        ScreenSpaceSink s = new ScreenSpaceSink(g, p);

        check("sink: a box ahead is in the frustum", s.inFrustum(-1, 63, 9, 1, 65, 11));
        check("sink: a box entirely behind the camera is culled", !s.inFrustum(-1, 63, -11, 1, 65, -9));
        check("sink: a box far off to the side is culled", !s.inFrustum(500, 63, 9, 502, 65, 11));
        check("sink: a box straddling the camera plane is kept (conservative)", s.inFrustum(-1, 63, -1, 1, 65, 1));
        check("sink: camera position is exposed to modules", s.camX() == 0 && s.camY() == 64 && s.camZ() == 0);

        s.box(-1, 63, 9, 1, 65, 11, 0xFFFF0000, 0x20FF0000, 2f, true);
        check("sink: a box draws pixels", gr.count() > 100);
        check("sink: the outline appears around the box centre region, not at the screen corner", gr.near(500, 300, 90) && !gr.near(5, 5, 3));

        Grid g2 = new Grid(1000, 600);
        ScreenSpaceSink s2 = new ScreenSpaceSink(new RasterGfx(g2, 1000, 600), p);
        s2.box(-1, 63, -11, 1, 65, -9, 0xFFFF0000, 0, 2f, true);
        check("sink: a box behind the camera draws nothing", g2.count() == 0);

        Grid g3 = new Grid(1000, 600);
        ScreenSpaceSink s3 = new ScreenSpaceSink(new RasterGfx(g3, 1000, 600), p);
        s3.box(-0.5, 60, -0.5, 0.5, 62, 20, 0xFFFF0000, 0, 2f, true); // a long pillar passing under/through the camera plane
        check("sink: a box that crosses the near plane is clipped and still draws", g3.count() > 50);

        Grid g4 = new Grid(1000, 600);
        ScreenSpaceSink s4 = new ScreenSpaceSink(new RasterGfx(g4, 1000, 600), p);
        s4.lineFromCrosshair(-3, 64, 10, 0xFFFFFFFF, 1.5f);
        check("sink: tracer starts at the screen centre and reaches the target", g4.near(500, 300, 3) && g4.near((int) (500 + 3.0 / 10 * (300 / Math.tan(Math.toRadians(35)))), 300, 6));
        Grid g5 = new Grid(1000, 600);
        new ScreenSpaceSink(new RasterGfx(g5, 1000, 600), p).lineFromCrosshair(0, 64, -10, 0xFFFFFFFF, 1.5f);
        check("sink: a tracer to a target behind you is skipped", g5.count() == 0);

        Grid g6 = new Grid(1000, 600);
        ScreenSpaceSink s6 = new ScreenSpaceSink(new RasterGfx(g6, 1000, 600), p);
        s6.label("Steve 10m", 0, 66, 10, 0xFFFFFFFF, 1f);
        check("sink: label draws text for visible points", g6.texts == 1);
        s6.label("Behind", 0, 66, -10, 0xFFFFFFFF, 1f);
        check("sink: label for a point behind the camera is skipped", g6.texts == 1);

        Grid g7 = new Grid(1000, 600);
        ScreenSpaceSink s7 = new ScreenSpaceSink(new RasterGfx(g7, 1000, 600), p);
        s7.line(1, 64, 5, 1, 64, 50, 0xFFFFFFFF, 1f);
        check("sink: world line draws", g7.count() > 0);
        s7.box(-1e7, 0, 1e7, 1e7, 1, 2e7, 0xFFFFFFFF, 0, 1f, true);
        check("sink: absurdly large geometry does not crash or hang", true);
    }
}
