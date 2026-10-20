package client.paint.core.basefinder;

import java.util.EnumMap;
import java.util.Map;

/**
 * Raw per-chunk findings. {@code completeness} is the fraction (0..1) of the chunk's sections and
 * neighbouring chunks the client had loaded when scanning; partial data scales the score down.
 */
public final class ChunkEvidence {
    private final Map<EvidenceType, Integer> counts = new EnumMap<>(EvidenceType.class);
    private double completeness = 1.0;

    public ChunkEvidence put(EvidenceType t, int count) {
        counts.put(t, Math.max(0, count));
        return this;
    }

    public ChunkEvidence completeness(double c) {
        completeness = Double.isNaN(c) ? 0 : Math.max(0, Math.min(1, c));
        return this;
    }

    public int count(EvidenceType t) { return counts.getOrDefault(t, 0); }
    public double completeness() { return completeness; }
}
