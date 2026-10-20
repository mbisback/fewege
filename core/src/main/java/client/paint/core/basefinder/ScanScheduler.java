package client.paint.core.basefinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Incremental, time-budgeted chunk scanning around the player. Never reads an unloaded chunk, aborts a
 * job whose chunk unloads, rescans a chunk only after {@code rescanMs}, and keeps its bookkeeping bounded.
 */
public final class ScanScheduler {
    public interface Sink { void result(int chunkX, int chunkZ, ChunkEvidence evidence); }

    private final Map<Long, Long> scannedAt = new HashMap<>();
    private ChunkScanner job;
    public int radius = 6;
    public long rescanMs = 60_000;
    public int columnsPerSlice = 16;

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    public void clear() { scannedAt.clear(); job = null; }
    public int trackedChunks() { return scannedAt.size(); }
    public boolean busy() { return job != null; }

    /** Run scans for at most {@code budgetNanos} (checked between small slices). @return chunks completed. */
    public int step(BlockSource src, int playerChunkX, int playerChunkZ, long nowMs, long budgetNanos, Sink sink) {
        long start = System.nanoTime();
        int completed = 0;
        prune(playerChunkX, playerChunkZ);
        while (System.nanoTime() - start < budgetNanos) {
            if (job == null) {
                job = next(src, playerChunkX, playerChunkZ, nowMs);
                if (job == null) break;
            }
            if (!src.isChunkLoaded(job.chunkX(), job.chunkZ())) { job = null; continue; }
            if (job.step(columnsPerSlice)) {
                scannedAt.put(key(job.chunkX(), job.chunkZ()), nowMs);
                sink.result(job.chunkX(), job.chunkZ(), job.result());
                job = null;
                completed++;
            }
        }
        return completed;
    }

    private ChunkScanner next(BlockSource src, int pcx, int pcz, long now) {
        int bestD = Integer.MAX_VALUE, bx = 0, bz = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d = dx * dx + dz * dz;
                if (d > radius * radius || d >= bestD) continue;
                int x = pcx + dx, z = pcz + dz;
                if (!src.isChunkLoaded(x, z)) continue;
                Long t = scannedAt.get(key(x, z));
                if (t != null && now - t < rescanMs) continue;
                bestD = d; bx = x; bz = z;
            }
        }
        return bestD == Integer.MAX_VALUE ? null : new ChunkScanner(src, bx, bz);
    }

    private void prune(int pcx, int pcz) {
        if (scannedAt.size() < 4096) return;
        List<Long> drop = new ArrayList<>();
        int lim = radius * 3;
        for (Long k : scannedAt.keySet()) {
            int x = (int) (k >> 32), z = (int) (long) k;
            if (Math.abs(x - pcx) > lim || Math.abs(z - pcz) > lim) drop.add(k);
        }
        drop.forEach(scannedAt::remove);
    }
}
