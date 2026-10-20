package client.paint.core.gui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pure-Java rasteriser implementing {@link Gfx} on top of a tiny {@link Backend}
 * (fill an integer rectangle with ARGB, draw a string). Shapes are emitted as row spans with
 * fractional edge coverage, so a Minecraft DrawContext only has to provide {@code fill} and {@code text}.
 */
public final class RasterGfx implements Gfx {
    public interface Backend {
        /** Fill a device-pixel rectangle. Colour is straight (non-premultiplied) ARGB; backend blends. */
        void fill(int x, int y, int w, int h, int argb);
        void text(String s, float x, float baselineY, float size, Font font, int argb);
        float textWidth(String s, float size, Font font);
    }

    private interface SpanFn { boolean span(double top, double bot, double[] out); }

    private final Backend b;
    private final int devW, devH;
    private double s = 1, tx = 0, ty = 0;
    private final Deque<double[]> xfStack = new ArrayDeque<>();
    private final Deque<int[]> clipStack = new ArrayDeque<>();
    private int[] clip;

    public RasterGfx(Backend backend, int deviceWidth, int deviceHeight) {
        this.b = backend; this.devW = deviceWidth; this.devH = deviceHeight;
        this.clip = new int[] {0, 0, deviceWidth, deviceHeight};
    }

    public void setViewport(Viewport v) { s = v.scale; tx = v.offX; ty = v.offY; xfStack.clear(); }

    // ---------------------------------------------------------------- primitives

    @Override public void fillRect(double x, double y, double w, double h, int argb) {
        if (w <= 0 || h <= 0) return;
        double x0 = X(x), x1 = X(x + w);
        rows(Y(y), Y(y + h), (t, bt, o) -> { o[0] = x0; o[1] = x1; return true; }, argb);
    }

    @Override public void fillRoundRect(double x, double y, double w, double h, double r, int argb) {
        if (w <= 0 || h <= 0) return;
        roundRectDev(X(x), Y(y), X(x + w), Y(y + h), Math.min(r * s, Math.min(w, h) * s / 2), argb);
    }

    private void roundRectDev(double x0, double y0, double x1, double y1, double r, int argb) {
        rows(y0, y1, (top, bot, o) -> {
            double mid = (top + bot) / 2, dy = 0;
            if (mid < y0 + r) dy = y0 + r - mid; else if (mid > y1 - r) dy = mid - (y1 - r);
            double inset = r <= 0 ? 0 : r - Math.sqrt(Math.max(0, r * r - dy * dy));
            o[0] = x0 + inset; o[1] = x1 - inset;
            return o[1] > o[0];
        }, argb);
    }

    @Override public void fillCircle(double cx, double cy, double r, int argb) {
        fillRoundRect(cx - r, cy - r, 2 * r, 2 * r, r, argb);
    }

    @Override public void strokeRoundRect(double x, double y, double w, double h, double r, double t, int argb) {
        double x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h);
        double rr = Math.min(r * s, Math.min(x1 - x0, y1 - y0) / 2), tt = Math.max(0.5, t * s);
        double ri = Math.max(0, rr - tt);
        // left part: outer-left .. inner-left (or the whole outer span where there is no inner shape)
        rows(y0, y1, (top, bot, o) -> {
            double mid = (top + bot) / 2;
            double[] outer = shapeSpan(mid, x0, y0, x1, y1, rr);
            if (outer == null) return false;
            if (mid < y0 + tt || mid > y1 - tt) { o[0] = outer[0]; o[1] = outer[1]; return true; }
            double[] inner = shapeSpan(mid, x0 + tt, y0 + tt, x1 - tt, y1 - tt, ri);
            o[0] = outer[0]; o[1] = inner == null ? outer[1] : inner[0];
            return o[1] > o[0];
        }, argb);
        rows(y0, y1, (top, bot, o) -> {
            double mid = (top + bot) / 2;
            if (mid < y0 + tt || mid > y1 - tt) return false;
            double[] outer = shapeSpan(mid, x0, y0, x1, y1, rr);
            double[] inner = shapeSpan(mid, x0 + tt, y0 + tt, x1 - tt, y1 - tt, ri);
            if (outer == null || inner == null) return false;
            o[0] = inner[1]; o[1] = outer[1];
            return o[1] > o[0];
        }, argb);
    }

    private static double[] shapeSpan(double mid, double x0, double y0, double x1, double y1, double r) {
        if (mid < y0 || mid > y1 || x1 <= x0) return null;
        double dy = 0;
        if (mid < y0 + r) dy = y0 + r - mid; else if (mid > y1 - r) dy = mid - (y1 - r);
        double inset = r <= 0 ? 0 : r - Math.sqrt(Math.max(0, r * r - dy * dy));
        double a = x0 + inset, c = x1 - inset;
        return c > a ? new double[] {a, c} : null;
    }

    @Override public void dashedRoundRect(double x, double y, double w, double h, double r, double t,
                                          double dash, double gap, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        double sx = w - 2 * r, sy = h - 2 * r, arc = Math.PI * r / 2;
        double period = dash + gap;
        // Walk the perimeter as a parametric path and emit each dash as a short quad.
        double total = 2 * sx + 2 * sy + 4 * arc;
        double d = 0;
        while (d < total) {
            double e = Math.min(d + dash, total);
            double[] p0 = perimeterPoint(x, y, w, h, r, sx, sy, arc, d);
            double[] p1 = perimeterPoint(x, y, w, h, r, sx, sy, arc, e);
            double[] pm = perimeterPoint(x, y, w, h, r, sx, sy, arc, (d + e) / 2);
            line(p0[0], p0[1], pm[0], pm[1], t, argb);
            line(pm[0], pm[1], p1[0], p1[1], t, argb);
            d += period;
        }
    }

    /** Point at distance d along the rounded-rect outline, starting at the top edge going clockwise. */
    private static double[] perimeterPoint(double x, double y, double w, double h, double r,
                                           double sx, double sy, double arc, double d) {
        double[] seg = {sx, arc, sy, arc, sx, arc, sy, arc};
        int i = 0;
        while (i < 7 && d > seg[i]) { d -= seg[i]; i++; }
        double f = seg[i] <= 0 ? 0 : Math.min(1, d / seg[i]);
        switch (i) {
            case 0: return new double[] {x + r + sx * f, y};
            case 1: return arcPoint(x + w - r, y + r, r, -90 + 90 * f);
            case 2: return new double[] {x + w, y + r + sy * f};
            case 3: return arcPoint(x + w - r, y + h - r, r, 90 * f);
            case 4: return new double[] {x + w - r - sx * f, y + h};
            case 5: return arcPoint(x + r, y + h - r, r, 90 + 90 * f);
            case 6: return new double[] {x, y + h - r - sy * f};
            default: return arcPoint(x + r, y + r, r, 180 + 90 * f);
        }
    }

    private static double[] arcPoint(double cx, double cy, double r, double deg) {
        double a = Math.toRadians(deg);
        return new double[] {cx + r * Math.cos(a), cy + r * Math.sin(a)};
    }

    @Override public void fillPolygon(double[] xs, double[] ys, int argb) {
        int n = xs.length;
        double[] dx = new double[n], dy = new double[n];
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            dx[i] = X(xs[i]); dy[i] = Y(ys[i]);
            minY = Math.min(minY, dy[i]); maxY = Math.max(maxY, dy[i]);
        }
        rows(minY, maxY, (top, bot, o) -> {
            double mid = (top + bot) / 2, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double ya = dy[i], yb = dy[j];
                if ((ya <= mid && yb > mid) || (yb <= mid && ya > mid)) {
                    double x = dx[i] + (mid - ya) / (yb - ya) * (dx[j] - dx[i]);
                    lo = Math.min(lo, x); hi = Math.max(hi, x);
                }
            }
            if (hi <= lo) return false;
            o[0] = lo; o[1] = hi;
            return true;
        }, argb);
    }

    @Override public void line(double x1, double y1, double x2, double y2, double t, int argb) {
        double dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy);
        if (len < 1e-9) return;
        double nx = -dy / len * t / 2, ny = dx / len * t / 2;
        fillPolygon(new double[] {x1 + nx, x2 + nx, x2 - nx, x1 - nx},
                    new double[] {y1 + ny, y2 + ny, y2 - ny, y1 - ny}, argb);
    }

    @Override public void radialGlow(double cx, double cy, double rx, double ry, int argb) {
        final int rings = 64;
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return;
        double perRing = 1 - Math.pow(1 - a / 255.0, 1.0 / rings);
        int ringArgb = ((int) Math.round(perRing * 255) << 24) | (argb & 0xFFFFFF);
        double dcx = X(cx), dcy = Y(cy), drx = rx * s, dry = ry * s;
        int step = 2;
        for (int k = 0; k < rings; k++) {
            double f = 1.0 - (double) k / rings;
            double ex = drx * f, ey = dry * f;
            int yStart = (int) Math.max(Math.floor(dcy - ey), clip[1]);
            int yEnd = (int) Math.min(Math.ceil(dcy + ey), clip[3]);
            for (int row = yStart; row < yEnd; row += step) {
                int hh = Math.min(step, yEnd - row);
                double dyy = row + hh / 2.0 - dcy;
                double q = 1 - (dyy / ey) * (dyy / ey);
                if (q <= 0) continue;
                double half = ex * Math.sqrt(q);
                fillDev((int) Math.round(dcx - half), row, (int) Math.round(2 * half), hh, ringArgb);
            }
        }
    }

    @Override public void verticalGradient(double x, double y, double w, double h, int top, int bottom) {
        double x0 = X(x), x1 = X(x + w), y0 = Y(y), y1 = Y(y + h);
        int step = 2;
        int ys = (int) Math.max(Math.floor(y0), clip[1]), ye = (int) Math.min(Math.ceil(y1), clip[3]);
        for (int row = ys; row < ye; row += step) {
            int hh = Math.min(step, ye - row);
            double f = Math.max(0, Math.min(1, (row + hh / 2.0 - y0) / (y1 - y0)));
            blockCols(x0, x1, row, row + hh, lerpArgb(top, bottom, f));
        }
    }

    // ---------------------------------------------------------------- text

    @Override public void text(String str, double x, double baselineY, double size, int argb, Font font, double spacing) {
        if (str == null || str.isEmpty() || ((argb >>> 24) & 0xFF) == 0) return;
        float dsz = (float) (size * s);
        double dy = Y(baselineY);
        if (dy - dsz > clip[3] || dy + dsz * 0.4 < clip[1]) return;
        if (spacing == 0) {
            b.text(str, (float) X(x), (float) dy, dsz, font, argb);
            return;
        }
        double cx = X(x);
        for (int i = 0; i < str.length(); i++) {
            String ch = String.valueOf(str.charAt(i));
            b.text(ch, (float) cx, (float) dy, dsz, font, argb);
            cx += b.textWidth(ch, dsz, font) + spacing * s;
        }
    }

    @Override public double textWidth(String str, double size, Font font, double spacing) {
        if (str == null || str.isEmpty()) return 0;
        if (spacing == 0) return b.textWidth(str, (float) (size * s), font) / s;
        double w = 0;
        for (int i = 0; i < str.length(); i++) w += b.textWidth(String.valueOf(str.charAt(i)), (float) (size * s), font) / s + spacing;
        return w - spacing;
    }

    // ---------------------------------------------------------------- clip / transform

    @Override public void pushClip(double x, double y, double w, double h) {
        clipStack.push(clip);
        int[] c = {(int) Math.floor(X(x)), (int) Math.floor(Y(y)), (int) Math.ceil(X(x + w)), (int) Math.ceil(Y(y + h))};
        clip = new int[] {Math.max(c[0], clip[0]), Math.max(c[1], clip[1]), Math.min(c[2], clip[2]), Math.min(c[3], clip[3])};
    }

    @Override public void popClip() { if (!clipStack.isEmpty()) clip = clipStack.pop(); }

    @Override public void pushTransform(double ttx, double tty, double sc) {
        xfStack.push(new double[] {s, tx, ty});
        tx = tx + s * ttx; ty = ty + s * tty; s = s * sc;
    }

    @Override public void popTransform() {
        if (xfStack.isEmpty()) return;
        double[] p = xfStack.pop();
        s = p[0]; tx = p[1]; ty = p[2];
    }

    @Override public double[] visibleBounds() {
        return new double[] {(0 - tx) / s, (0 - ty) / s, (devW - tx) / s, (devH - ty) / s};
    }

    // ---------------------------------------------------------------- span machinery

    private double X(double x) { return x * s + tx; }
    private double Y(double y) { return y * s + ty; }

    /** Emit a shape given by per-row spans; consecutive identical full rows are merged into one fill. */
    private void rows(double y0, double y1, SpanFn fn, int argb) {
        if (((argb >>> 24) & 0xFF) == 0 || y1 <= y0) return;
        int iy0 = Math.max((int) Math.floor(y0), clip[1]), iy1 = Math.min((int) Math.ceil(y1), clip[3]);
        double[] o = new double[2];
        int runStart = -1;
        double rl = 0, rr = 0;
        for (int row = iy0; row < iy1; row++) {
            double top = Math.max(row, y0), bot = Math.min(row + 1, y1), cov = bot - top;
            boolean has = cov > 0 && fn.span(top, bot, o);
            boolean full = has && cov >= 0.999;
            if (full && runStart >= 0 && Math.abs(o[0] - rl) < 1e-6 && Math.abs(o[1] - rr) < 1e-6) continue;
            if (runStart >= 0) { blockCols(rl, rr, runStart, row, argb); runStart = -1; }
            if (has) {
                if (full) { runStart = row; rl = o[0]; rr = o[1]; }
                else blockCols(o[0], o[1], row, row + 1, mulAlpha(argb, cov));
            }
        }
        if (runStart >= 0) blockCols(rl, rr, runStart, iy1, argb);
    }

    /** Fill columns [x0,x1) (fractional ends antialiased) over device rows [ry0,ry1). */
    private void blockCols(double x0, double x1, int ry0, int ry1, int argb) {
        if (x1 <= x0 || ry1 <= ry0) return;
        int ix0 = (int) Math.floor(x0), ix1 = (int) Math.ceil(x1);
        if (ix1 - ix0 == 1) { fillDev(ix0, ry0, 1, ry1 - ry0, mulAlpha(argb, x1 - x0)); return; }
        int fs = ix0, fe = ix1;
        double lc = ix0 + 1 - x0, rc = x1 - (ix1 - 1);
        if (lc < 0.999) { fillDev(ix0, ry0, 1, ry1 - ry0, mulAlpha(argb, lc)); fs++; }
        if (rc < 0.999) { fillDev(ix1 - 1, ry0, 1, ry1 - ry0, mulAlpha(argb, rc)); fe--; }
        if (fe > fs) fillDev(fs, ry0, fe - fs, ry1 - ry0, argb);
    }

    private void fillDev(int x, int y, int w, int h, int argb) {
        int x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
        int x1 = Math.min(x + w, clip[2]), y1 = Math.min(y + h, clip[3]);
        if (x1 <= x0 || y1 <= y0 || ((argb >>> 24) & 0xFF) == 0) return;
        b.fill(x0, y0, x1 - x0, y1 - y0, argb);
    }

    static int mulAlpha(int argb, double f) {
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    static int lerpArgb(int a, int b, double t) {
        int out = 0;
        for (int sh = 24; sh >= 0; sh -= 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
        }
        return out;
    }
}
