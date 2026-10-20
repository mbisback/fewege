package client.paint.core.basefinder;

import client.paint.core.basefinder.BlockSource.Kind;

/**
 * Resumable scan of one loaded chunk into {@link ChunkEvidence}. Work is split by column so the
 * scheduler can stop at its time budget and continue next tick.
 *
 * <p>Indicators measured (all from blocks the client has received; unloaded neighbours are treated
 * as "unknown", never as empty or solid):
 * <ul>
 *   <li>AMETHYST_LARGE - amethyst blocks.</li>
 *   <li>TALL_KELP - vertical kelp runs >= {@value #KELP_MIN_RUN}.</li>
 *   <li>LONG_VINES - vertical vine runs >= {@value #VINE_MIN_RUN}.</li>
 *   <li>PLAYER_BLOCKS - blocks of kinds that do not generate naturally.</li>
 *   <li>CONSTRUCTION_PATTERN - aligned 4x4 flat patches made only of player-placed blocks.</li>
 *   <li>EXCAVATION - cells in straight 1x2 corridors of at least {@value #CORRIDOR_MIN_RUN} cells,
 *       walled and roofed by natural solid blocks.</li>
 * </ul>
 * UNUSUAL_VEGETATION needs biome data and is not measured (always 0).
 */
public final class ChunkScanner {
    public static final int KELP_MIN_RUN = 10, VINE_MIN_RUN = 8, CORRIDOR_MIN_RUN = 6;

    private final BlockSource src;
    private final int cx, cz, minY, maxY, height;
    private int column;              // 0..255
    private int amethyst, kelpColumns, vineColumns, playerBlocks, patches;
    private final boolean[] corrX, corrZ;

    public ChunkScanner(BlockSource src, int chunkX, int chunkZ) {
        this.src = src; this.cx = chunkX; this.cz = chunkZ;
        this.minY = src.minY(); this.maxY = src.maxY(); this.height = maxY - minY;
        corrX = new boolean[height * 256];
        corrZ = new boolean[height * 256];
    }

    public int chunkX() { return cx; }
    public int chunkZ() { return cz; }
    public boolean isDone() { return column >= 256; }

    /** Scan up to {@code columns} columns. @return true when the whole chunk has been scanned. */
    public boolean step(int columns) {
        int end = Math.min(256, column + columns);
        for (; column < end; column++) scanColumn(column >> 4, column & 15);
        return isDone();
    }

    private void scanColumn(int lx, int lz) {
        int x = cx * 16 + lx, z = cz * 16 + lz;
        int kelpRun = 0, vineRun = 0;
        boolean align = (lx & 3) == 0 && (lz & 3) == 0;
        for (int y = minY; y < maxY; y++) {
            Kind k = src.kind(x, y, z);
            switch (k) {
                case AMETHYST -> amethyst++;
                case PLAYER_PLACED -> {
                    playerBlocks++;
                    if (align && lx <= 12 && lz <= 12 && flatPatch(x, y, z)) patches++;
                }
                default -> { }
            }
            if (k == Kind.KELP) kelpRun++; else { if (kelpRun >= KELP_MIN_RUN) kelpColumns++; kelpRun = 0; }
            if (k == Kind.VINE) vineRun++; else { if (vineRun >= VINE_MIN_RUN) vineColumns++; vineRun = 0; }
            if (k == Kind.AIR && y + 2 < maxY && y > minY) corridorFlags(x, y, z, lx, lz);
        }
        if (kelpRun >= KELP_MIN_RUN) kelpColumns++;
        if (vineRun >= VINE_MIN_RUN) vineColumns++;
    }

    private boolean flatPatch(int x, int y, int z) {
        for (int dx = 0; dx < 4; dx++) for (int dz = 0; dz < 4; dz++) {
            if (src.kind(x + dx, y, z + dz) != Kind.PLAYER_PLACED) return false;
        }
        return true;
    }

    private boolean naturalSolid(int x, int y, int z) {
        if (!src.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return false;
        Kind k = src.kind(x, y, z);
        return k == Kind.SOLID_NATURAL || k.isOre();
    }

    private void corridorFlags(int x, int y, int z, int lx, int lz) {
        if (src.kind(x, y + 1, z) != Kind.AIR) return;
        if (!naturalSolid(x, y - 1, z) || !naturalSolid(x, y + 2, z)) return;
        int i = (y - minY) * 256 + lx * 16 + lz;
        if (naturalSolid(x - 1, y, z) && naturalSolid(x + 1, y, z) && naturalSolid(x - 1, y + 1, z) && naturalSolid(x + 1, y + 1, z)) {
            corrZ[i] = true; // walls on the X sides: the corridor runs along Z
        }
        if (naturalSolid(x, y, z - 1) && naturalSolid(x, y, z + 1) && naturalSolid(x, y + 1, z - 1) && naturalSolid(x, y + 1, z + 1)) {
            corrX[i] = true; // walls on the Z sides: the corridor runs along X
        }
    }

    /** Valid once {@link #isDone()}. */
    public ChunkEvidence result() {
        int excavation = 0;
        for (int yi = 0; yi < height; yi++) {
            for (int a = 0; a < 16; a++) {
                int runX = 0, runZ = 0;
                for (int b = 0; b <= 16; b++) {
                    boolean fx = b < 16 && corrX[yi * 256 + b * 16 + a];   // runs along X: vary lx=b, fixed lz=a
                    boolean fz = b < 16 && corrZ[yi * 256 + a * 16 + b];   // runs along Z: fixed lx=a, vary lz=b
                    if (fx) runX++; else { if (runX >= CORRIDOR_MIN_RUN) excavation += runX; runX = 0; }
                    if (fz) runZ++; else { if (runZ >= CORRIDOR_MIN_RUN) excavation += runZ; runZ = 0; }
                }
            }
        }
        int loaded = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && src.isChunkLoaded(cx + dx, cz + dz)) loaded++;
        }
        return new ChunkEvidence()
                .put(EvidenceType.AMETHYST_LARGE, amethyst)
                .put(EvidenceType.TALL_KELP, kelpColumns)
                .put(EvidenceType.LONG_VINES, vineColumns)
                .put(EvidenceType.PLAYER_BLOCKS, playerBlocks)
                .put(EvidenceType.CONSTRUCTION_PATTERN, patches)
                .put(EvidenceType.EXCAVATION, excavation)
                .completeness((1 + loaded) / 9.0);
    }
}
