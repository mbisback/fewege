package client.paint.core.basefinder;

/**
 * Block classification for loaded terrain. The adapter maps real block ids to {@link Kind}; the scanner
 * never touches Minecraft types. {@link #kind} must only be called for loaded chunks.
 */
public interface BlockSource {
    enum Kind {
        AIR, SOLID_NATURAL, AMETHYST, KELP, VINE, PLAYER_PLACED, WATER, OTHER,
        ORE_DIAMOND, ORE_DEBRIS, ORE_EMERALD, ORE_GOLD, ORE_IRON, ORE_OTHER, PORTAL;

        public boolean isOre() { return this == ORE_DIAMOND || this == ORE_DEBRIS || this == ORE_EMERALD || this == ORE_GOLD || this == ORE_IRON || this == ORE_OTHER; }
    }

    boolean isChunkLoaded(int chunkX, int chunkZ);
    int minY();
    int maxY();           // exclusive
    Kind kind(int x, int y, int z);
}
