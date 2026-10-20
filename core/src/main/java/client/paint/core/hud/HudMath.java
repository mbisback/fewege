package client.paint.core.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/** Small, side-effect-free helpers behind the HUD widgets (CPS, session timer, durability). */
public final class HudMath {
    private HudMath() {}

    /** Rolling clicks-per-second counter. Bounded: old clicks are discarded. */
    public static final class CpsCounter {
        private final Deque<Long> clicks = new ArrayDeque<>();
        private static final int MAX_TRACKED = 200;

        public void click(long nowMs) {
            clicks.addLast(nowMs);
            while (clicks.size() > MAX_TRACKED) clicks.removeFirst();
        }

        public int cps(long nowMs) {
            while (!clicks.isEmpty() && nowMs - clicks.peekFirst() >= 1000) clicks.removeFirst();
            return clicks.size();
        }

        public void clear() { clicks.clear(); }
    }

    public enum DurabilityColor { GREEN, YELLOW, RED }

    /** @return remaining durability percent (0..100), or -1 for unbreakable items (maxDamage <= 0). */
    public static int durabilityPercent(int damage, int maxDamage) {
        if (maxDamage <= 0) return -1;
        int d = Math.max(0, Math.min(maxDamage, damage));
        return (int) Math.round((maxDamage - d) * 100.0 / maxDamage);
    }

    public static DurabilityColor durabilityColor(int percent) {
        if (percent > 60) return DurabilityColor.GREEN;
        if (percent > 30) return DurabilityColor.YELLOW;
        return DurabilityColor.RED;
    }

    /** "H:MM:SS" (hours grow without limit). Negative input is treated as zero. */
    public static String formatDuration(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    /** Effect timer "M:SS" from remaining ticks (20 ticks = 1 s). */
    public static String formatEffectTicks(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    /** Horizontal speed in blocks/second from two positions and elapsed ms. */
    public static double speedBps(double dx, double dz, long elapsedMs) {
        if (elapsedMs <= 0) return 0;
        return Math.hypot(dx, dz) / (elapsedMs / 1000.0);
    }
}
