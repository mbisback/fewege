package client.paint.core.game;

import client.paint.core.basefinder.BlockSource;

import java.util.List;

/** Read-only snapshot access to what the client has received. Lists may be reused between calls. */
public interface WorldView {
    enum EntityKind { PLAYER, HOSTILE, PASSIVE, ITEM, PROJECTILE, OTHER }
    enum StorageKind { CHEST, TRAPPED_CHEST, ENDER_CHEST, SHULKER, BARREL, SPAWNER }

    record EntityInfo(int id, EntityKind kind, String name, boolean self,
                      double x, double y, double z, double width, double height, double health, double maxHealth) {
        public EntityInfo(int id, EntityKind kind, String name, boolean self, double x, double y, double z, double width, double height) {
            this(id, kind, name, self, x, y, z, width, height, -1, -1);
        }
    }
    record StorageInfo(StorageKind kind, int x, int y, int z) {}

    List<EntityInfo> entities();
    List<StorageInfo> storage();
    BlockSource blocks();
    /** True if there is a clear line from the camera to the point (client-side raycast). */
    boolean lineOfSight(double x, double y, double z);
    /** True if a solid block lies between the two points (used to stop projectile trajectories). */
    default boolean segmentBlocked(double x1, double y1, double z1, double x2, double y2, double z2) { return false; }
}
