package client.paint.core.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/** Session statistics: elapsed time, horizontal speed (1 s window), jumps, distance travelled, biggest fall. */
public final class StatsTracker {
    private long startMs = -1;
    private double lastX, lastZ, lastY;
    private boolean hasLast, wasOnGround = true;
    private double distance, maxFall;
    private int jumps;
    private final Deque<double[]> window = new ArrayDeque<>(); // {timeMs, x, z}

    public void reset(long nowMs) { startMs = nowMs; hasLast = false; distance = 0; maxFall = 0; jumps = 0; window.clear(); wasOnGround = true; }

    /** Call every tick while in a world. Teleports (> 100 blocks in one tick) restart the baseline instead of counting. */
    public void update(long nowMs, double x, double y, double z, boolean onGround, double fallDistance) {
        if (startMs < 0) startMs = nowMs;
        if (hasLast) {
            double d = Math.hypot(x - lastX, z - lastZ);
            if (d > 100) window.clear(); else distance += d;
            if (wasOnGround && !onGround && y - lastY > 0.2) jumps++;
        }
        if (fallDistance > maxFall) maxFall = fallDistance;
        lastX = x; lastZ = z; lastY = y; hasLast = true; wasOnGround = onGround;
        window.addLast(new double[] {nowMs, x, z});
        while (window.size() > 1 && nowMs - window.peekFirst()[0] > 1000) window.removeFirst();
    }

    public long sessionMs(long nowMs) { return startMs < 0 ? 0 : Math.max(0, nowMs - startMs); }
    public double distance() { return distance; }
    public int jumps() { return jumps; }
    public double maxFall() { return maxFall; }

    public double speedBps() {
        if (window.size() < 2) return 0;
        double[] a = window.peekFirst(), b = window.peekLast();
        double dt = (b[0] - a[0]) / 1000.0;
        return dt <= 0 ? 0 : Math.hypot(b[1] - a[1], b[2] - a[2]) / dt;
    }
}
