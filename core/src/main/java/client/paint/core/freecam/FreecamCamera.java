package client.paint.core.freecam;

import java.util.Optional;

/**
 * Freecam camera state and camera-relative movement (pure math, no Minecraft types).
 *
 * <p>Convention is Minecraft's: yaw 0 faces +Z (south), yaw -90 faces +X (east), yaw 90 faces -X;
 * pitch is positive looking down. Movement is horizontal-plane relative to yaw (pitch does not tilt
 * W/S) with separate vertical input, which is the usual predictable freecam feel.
 *
 * <p>The camera never touches the player: {@link #begin} snapshots the real position/rotation and
 * {@link #stop} returns that snapshot exactly once so the caller restores the view.
 */
public final class FreecamCamera {
    public enum StopReason { MODULE_DISABLED, DEATH, DISCONNECT, DIMENSION_CHANGE }

    public record Snapshot(double x, double y, double z, float yaw, float pitch) {}

    public double x, y, z;
    public float yaw, pitch;
    private Snapshot saved;

    public boolean isActive() { return saved != null; }

    public void begin(double px, double py, double pz, float pyaw, float ppitch) {
        if (saved != null) return; // already active: never overwrite the real snapshot
        saved = new Snapshot(px, py, pz, pyaw, ppitch);
        x = px; y = py; z = pz; yaw = pyaw; pitch = clampPitch(ppitch);
    }

    /** Look input (degrees). Pitch is clamped to +-90. */
    public void look(float dYaw, float dPitch) {
        if (saved == null) return;
        yaw = wrap(yaw + dYaw);
        pitch = clampPitch(pitch + dPitch);
    }

    /**
     * @param forward  +1 W, -1 S
     * @param strafe   +1 D (right), -1 A (left)
     * @param vertical +1 up, -1 down
     * @param speed    blocks per second
     * @param dt       seconds since last update
     */
    public void move(double forward, double strafe, double vertical, double speed, double dt) {
        if (saved == null || dt <= 0 || speed <= 0) return;
        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len > 1) { forward /= len; strafe /= len; } // no faster diagonals
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);     // forward
        double rx = -Math.cos(rad), rz = -Math.sin(rad);    // right
        double step = speed * dt;
        x += (fx * forward + rx * strafe) * step;
        z += (fz * forward + rz * strafe) * step;
        y += Math.max(-1, Math.min(1, vertical)) * step;
    }

    /** Ends freecam. Returns the original view exactly once; later calls return empty. */
    public Optional<Snapshot> stop(StopReason reason) {
        Snapshot s = saved;
        saved = null;
        return Optional.ofNullable(s);
    }

    private static float clampPitch(float p) { return Math.max(-90f, Math.min(90f, p)); }

    private static float wrap(float yaw) {
        float y = yaw % 360f;
        if (y >= 180f) y -= 360f;
        if (y < -180f) y += 360f;
        return y;
    }
}
