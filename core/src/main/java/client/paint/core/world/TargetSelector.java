package client.paint.core.world;

import client.paint.core.game.WorldView.EntityInfo;
import client.paint.core.game.WorldView.EntityKind;

import java.util.List;

/** Picks the entity the player is aiming at: smallest angle to the view direction within a cone and a range. */
public final class TargetSelector {
    private TargetSelector() {}

    public record Pick(EntityInfo entity, double distance, double angleDeg) {}

    public static Pick pick(List<EntityInfo> entities, double cx, double cy, double cz, float yawDeg, float pitchDeg,
                            double maxDist, double maxAngleDeg, boolean playersOnly) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        Pick best = null;
        for (EntityInfo e : entities) {
            if (e.self()) continue;
            if (e.kind() == EntityKind.ITEM || e.kind() == EntityKind.PROJECTILE) continue;
            if (playersOnly && e.kind() != EntityKind.PLAYER) continue;
            double vx = e.x() - cx, vy = e.y() + e.height() / 2 - cy, vz = e.z() - cz;
            double d = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (d < 1e-6 || d > maxDist) continue;
            double dot = (vx * fx + vy * fy + vz * fz) / d;
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
            if (ang > maxAngleDeg) continue;
            if (best == null || ang < best.angleDeg()) best = new Pick(e, d, ang);
        }
        return best;
    }
}
