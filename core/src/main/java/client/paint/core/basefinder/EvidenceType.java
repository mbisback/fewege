package client.paint.core.basefinder;

/**
 * Indicators BaseFinder can score. {@code count} semantics are chunk-level counts produced by the
 * (future, in-game) scanner from blocks the client has actually received.
 *
 * <p>Natural clues are capped as a group (see {@link BaseFinderScorer.Config#naturalCap}) so they
 * can never rank as strongly as modification/construction evidence.
 */
public enum EvidenceType {
    //                       kind                      weight cap  minCount  naturally occurs?
    AMETHYST_LARGE      (Kind.NATURAL,   3.0,  6,  3,  "Yes: geodes generate amethyst clusters naturally"),
    TALL_KELP           (Kind.NATURAL,   0.5, 20,  6,  "Yes: kelp grows to height on its own"),
    LONG_VINES          (Kind.NATURAL,   0.5, 20,  6,  "Yes: vines grow long in jungles/swamps"),
    UNUSUAL_VEGETATION  (Kind.NATURAL,   1.0, 10,  4,  "Yes: biome-mismatched plants are only a weak hint"),
    EXCAVATION          (Kind.ACTIVITY,  1.5, 12,  3,  "Rarely: straight 1x2 tunnels through solid rock"),
    PLAYER_BLOCKS       (Kind.STRUCTURE, 0.5, 60,  8,  "No: blocks that do not generate naturally"),
    CONSTRUCTION_PATTERN(Kind.STRUCTURE, 15.0, 4,  1,  "Very rarely: flat floors / straight regular walls");

    public enum Kind { NATURAL, ACTIVITY, STRUCTURE }

    public final Kind kind;
    public final double weight;
    public final int cap;
    public final int minCount;
    public final String naturalNote;

    EvidenceType(Kind kind, double weight, int cap, int minCount, String naturalNote) {
        this.kind = kind; this.weight = weight; this.cap = cap; this.minCount = minCount;
        this.naturalNote = naturalNote;
    }
}
