package client.paint.core.basefinder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted chunk scoring. Principles:
 * <ol>
 *   <li>An indicator below its {@code minCount} contributes nothing (suppresses noise / false positives).</li>
 *   <li>Each indicator is capped, so one huge natural formation cannot dominate.</li>
 *   <li>All NATURAL indicators together are capped at {@link Config#naturalCap}; natural-only
 *       chunks are always labelled "Natural Indicator" with LOW confidence, never a base.</li>
 *   <li>Incomplete data scales the score by {@code completeness} and limits confidence to LOW.</li>
 *   <li>Nothing is ever "confirmed": labels are Natural Indicator / Possible Activity / Possible Structure.</li>
 * </ol>
 */
public final class BaseFinderScorer {
    public enum Label {
        NATURAL_INDICATOR("Natural Indicator"), POSSIBLE_ACTIVITY("Possible Activity"), POSSIBLE_STRUCTURE("Possible Structure");
        public final String title;
        Label(String title) { this.title = title; }
    }

    public enum Confidence { LOW, MEDIUM, HIGH }

    public static final class Config {
        public final Map<EvidenceType, Double> weights = new EnumMap<>(EvidenceType.class);
        public double naturalCap = 30;
        public double activityThreshold = 10;   // ACTIVITY bucket needed for "Possible Activity"
        public double structureThreshold = 15;  // STRUCTURE bucket needed for "Possible Structure"
        public double mediumScore = 25, highScore = 60;
        public double minCompleteness = 0.6;
        public double minScoreToReport = 10;

        public Config() { for (EvidenceType t : EvidenceType.values()) weights.put(t, t.weight); }
    }

    public static final class Result {
        public final double score;
        public final Label label;
        public final Confidence confidence;
        public final boolean incomplete;
        public final boolean reportable;
        public final String evidenceSummary;

        Result(double score, Label label, Confidence confidence, boolean incomplete, boolean reportable, String summary) {
            this.score = score; this.label = label; this.confidence = confidence;
            this.incomplete = incomplete; this.reportable = reportable; this.evidenceSummary = summary;
        }
    }

    private final Config cfg;

    public BaseFinderScorer(Config cfg) { this.cfg = cfg; }

    public Result score(ChunkEvidence e) {
        double natural = 0, activity = 0, structure = 0;
        List<String> used = new ArrayList<>();
        for (EvidenceType t : EvidenceType.values()) {
            int c = e.count(t);
            if (c < t.minCount) continue;
            double contrib = Math.min(c, t.cap) * cfg.weights.get(t);
            switch (t.kind) {
                case NATURAL -> natural += contrib;
                case ACTIVITY -> activity += contrib;
                case STRUCTURE -> structure += contrib;
            }
            used.add(t.name() + " x" + c);
        }
        natural = Math.min(natural, cfg.naturalCap);

        double f = e.completeness();
        double total = (natural + activity + structure) * f;

        Label label = structure >= cfg.structureThreshold ? Label.POSSIBLE_STRUCTURE
                : activity >= cfg.activityThreshold ? Label.POSSIBLE_ACTIVITY
                : Label.NATURAL_INDICATOR;

        boolean incomplete = f < cfg.minCompleteness;
        Confidence conf = Confidence.LOW;
        if (label != Label.NATURAL_INDICATOR && !incomplete) {
            if (total >= cfg.highScore) conf = Confidence.HIGH;
            else if (total >= cfg.mediumScore) conf = Confidence.MEDIUM;
        }
        return new Result(total, label, conf, incomplete, total >= cfg.minScoreToReport,
                String.join(", ", used));
    }
}
