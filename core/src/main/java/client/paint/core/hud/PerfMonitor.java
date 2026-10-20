package client.paint.core.hud;

/** Rolling frame-time statistics over the last {@code N} frames. */
public final class PerfMonitor {
    private final double[] ring;
    private int n, idx;

    public PerfMonitor(int frames) { ring = new double[Math.max(2, frames)]; }

    public void frame(double dtSeconds) {
        if (!(dtSeconds > 0) || dtSeconds > 5) return; // ignore pauses and nonsense
        ring[idx] = dtSeconds * 1000.0;
        idx = (idx + 1) % ring.length;
        if (n < ring.length) n++;
    }

    public double avgMs() { if (n == 0) return 0; double s = 0; for (int i = 0; i < n; i++) s += ring[i]; return s / n; }
    public double maxMs() { double m = 0; for (int i = 0; i < n; i++) m = Math.max(m, ring[i]); return m; }
    public double fps() { double a = avgMs(); return a <= 0 ? 0 : 1000.0 / a; }
}
