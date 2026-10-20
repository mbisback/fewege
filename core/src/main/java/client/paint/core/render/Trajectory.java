package client.paint.core.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Projectile flight prediction using Minecraft's per-tick rules: pos += vel; vel *= drag; vel.y -= gravity.
 * The prediction is client-side only: it does not model entity hits, wind charges, or server-side corrections.
 */
public final class Trajectory {
    private Trajectory() {}

    public enum Kind {
        ARROW(0.05, 0.99, 3.0, true), CROSSBOW(0.05, 0.99, 3.15, false), TRIDENT(0.05, 0.99, 2.5, false),
        PEARL(0.03, 0.99, 1.5, false), SNOWBALL(0.03, 0.99, 1.5, false), EGG(0.03, 0.99, 1.5, false), POTION(0.05, 0.99, 0.5, false);

        public final double gravity, drag, speed;
        public final boolean charged;
        Kind(double gravity, double drag, double speed, boolean charged) {
            this.gravity = gravity; this.drag = drag; this.speed = speed; this.charged = charged;
        }
    }

    @FunctionalInterface
    public interface Blocked { boolean between(double x1, double y1, double z1, double x2, double y2, double z2); }

    /** Bow draw power for a charge of 0..1 (ticks/20 passed through vanilla's curve). */
    public static double bowPower(double charge) {
        double f = Math.max(0, Math.min(1, charge));
        f = (f * f + f * 2.0) / 3.0;
        return Math.min(1.0, f);
    }

    /**
     * @return flight path points (including the start); the last point is the impact point if the path hit a block.
     *         A charged kind with ~0 power returns just the start point.
     */
    public static List<double[]> simulate(Kind kind, double charge, double x, double y, double z,
                                          float yawDeg, float pitchDeg, int maxSteps, Blocked blocked) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {x, y, z});
        double power = kind.charged ? bowPower(charge) : 1.0;
        if (power < 0.1) return pts;
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
        double speed = kind.speed * power;
        double vx = dx * speed, vy = dy * speed, vz = dz * speed;
        for (int i = 0; i < Math.max(1, maxSteps); i++) {
            double nx = x + vx, ny = y + vy, nz = z + vz;
            if (blocked != null && blocked.between(x, y, z, nx, ny, nz)) { pts.add(new double[] {nx, ny, nz}); break; }
            x = nx; y = ny; z = nz;
            vx *= kind.drag; vy *= kind.drag; vz *= kind.drag;
            vy -= kind.gravity;
            pts.add(new double[] {x, y, z});
            if (y < -128) break;
        }
        return pts;
    }
}
