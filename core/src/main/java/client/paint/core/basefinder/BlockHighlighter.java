package client.paint.core.basefinder;

import client.paint.core.basefinder.BlockSource.Kind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Incremental, time-budgeted scan of loaded chunks for individual blocks of interest (ores, portals).
 * Memory is bounded twice: at most {@code maxHitsPerChunk} hits per chunk and at most {@code maxChunks}
 * chunks kept (least recently scanned dropped). Chunks are rescanned after {@code rescanMs} because blocks change.
 */
public final class BlockHighlighter {
    public record Hit(int x, int y, int z, Kind kind) {}

    private static final class Entry {
        final List<Hit> hits = new ArrayList<>();
        long scannedAt;
    }

    private static final int COLS_PER_SLICE = 16;
    public int radius = 6, maxHitsPerChunk = 512, maxChunks = 400;
    public long rescanMs = 20_000;

    private final Map<Long, Entry> done = new LinkedHashMap<>(16, 0.75f, true);
    private long jobKey = Long.MIN_VALUE;
    private int jobCx, jobCz, jobCol;
    private Entry job;

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    public void clear() { done.clear(); job = null; jobKey = Long.MIN_VALUE; }
    public int chunkCount() { return done.size(); }

    public int hitCount() { int n = 0; for (Entry e : done.values()) n += e.hits.size(); return n; }

    /** Hits of one scanned chunk (empty if unknown). Do not modify. */
    public List<Hit> hits(int cx, int cz) {
        Entry e = done.get(key(cx, cz));
        return e == null ? List.of() : e.hits;
    }

    /** Drop the hit lists of chunks the client has unloaded. */
    public void prune(BlockSource src) {
        done.keySet().removeIf(k -> !src.isChunkLoaded((int) (k >> 32), (int) (long) (int) (k & 0xFFFFFFFFL)));
    }

    /** Work until {@code budgetNanos} is used. @return chunks completed in this call. */
    public int step(BlockSource src, int pcx, int pcz, long nowMs, long budgetNanos, Predicate<Kind> wanted) {
        long end = System.nanoTime() + Math.max(0, budgetNanos);
        int completed = 0;
        do {
            if (job != null && !src.isChunkLoaded(jobCx, jobCz)) { job = null; jobKey = Long.MIN_VALUE; }
            if (job == null && !pickJob(src, pcx, pcz, nowMs)) break;
            scanSlice(src, wanted);
            if (jobCol >= 256) {
                job.scannedAt = nowMs;
                done.put(jobKey, job);
                job = null; jobKey = Long.MIN_VALUE;
                completed++;
                while (done.size() > Math.max(1, maxChunks)) {
                    Long oldest = done.keySet().iterator().next();
                    done.remove(oldest);
                }
            }
        } while (System.nanoTime() < end);
        return completed;
    }

    private boolean pickJob(BlockSource src, int pcx, int pcz, long nowMs) {
        int best = Integer.MAX_VALUE, bx = 0, bz = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = pcx + dx, cz = pcz + dz;
                if (!src.isChunkLoaded(cx, cz)) continue;
                Entry e = done.get(key(cx, cz));
                if (e != null && nowMs - e.scannedAt < rescanMs) continue;
                int d = dx * dx + dz * dz;
                if (e != null) d += 10_000; // fresh chunks before rescans
                if (d < best) { best = d; bx = cx; bz = cz; }
            }
        }
        if (best == Integer.MAX_VALUE) return false;
        job = new Entry(); jobCx = bx; jobCz = bz; jobKey = key(bx, bz); jobCol = 0;
        return true;
    }

    private void scanSlice(BlockSource src, Predicate<Kind> wanted) {
        int endCol = Math.min(256, jobCol + COLS_PER_SLICE);
        for (; jobCol < endCol; jobCol++) {
            int x = jobCx * 16 + (jobCol >> 4), z = jobCz * 16 + (jobCol & 15);
            for (int y = src.minY(); y < src.maxY(); y++) {
                Kind k = src.kind(x, y, z);
                if (k == Kind.AIR || k == Kind.SOLID_NATURAL) continue;
                if (!wanted.test(k)) continue;
                if (job.hits.size() < maxHitsPerChunk) job.hits.add(new Hit(x, y, z, k));
            }
        }
    }
}
