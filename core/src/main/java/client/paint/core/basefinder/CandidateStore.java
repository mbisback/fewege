package client.paint.core.basefinder;

import client.paint.core.basefinder.BaseFinderScorer.Label;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, de-duplicated candidate history with bookmarks, revisit flags and import/export. */
public final class CandidateStore {
    public record Candidate(String dimension, int chunkX, int chunkZ, double score, Label label,
                            String evidence, long detectedAtMs, boolean revisited, boolean bookmarked) {
        public String key() { return dimension + ":" + chunkX + ":" + chunkZ; }
    }

    public static final String HEADER = "#paint-candidates v1";
    public static final int MAX_DEFAULT = 5000;

    private final Map<String, Candidate> map = new LinkedHashMap<>();
    private final int max;

    public CandidateStore() { this(MAX_DEFAULT); }
    public CandidateStore(int max) { this.max = Math.max(1, max); }

    public int size() { return map.size(); }
    public Candidate get(String dim, int cx, int cz) { return map.get(dim + ":" + cx + ":" + cz); }

    /**
     * Insert or update. A re-detection keeps bookmark/revisit state and the original detection time;
     * score/label/evidence are replaced only if the new score is higher (so partial rescans don't erase a good hit).
     */
    public void upsert(String dim, int cx, int cz, double score, Label label, String evidence, long nowMs) {
        String ev = clean(evidence);
        String key = dim + ":" + cx + ":" + cz;
        Candidate old = map.get(key);
        if (old == null) {
            map.put(key, new Candidate(dim, cx, cz, score, label, ev, nowMs, false, false));
            evictIfNeeded();
        } else if (score > old.score()) {
            map.put(key, new Candidate(dim, cx, cz, score, label, ev, old.detectedAtMs(), old.revisited(), old.bookmarked()));
        }
    }

    public boolean setBookmarked(String dim, int cx, int cz, boolean on) {
        Candidate c = get(dim, cx, cz);
        if (c == null) return false;
        map.put(c.key(), new Candidate(c.dimension(), c.chunkX(), c.chunkZ(), c.score(), c.label(), c.evidence(),
                c.detectedAtMs(), c.revisited(), on));
        return true;
    }

    public boolean setRevisited(String dim, int cx, int cz, boolean on) {
        Candidate c = get(dim, cx, cz);
        if (c == null) return false;
        map.put(c.key(), new Candidate(c.dimension(), c.chunkX(), c.chunkZ(), c.score(), c.label(), c.evidence(),
                c.detectedAtMs(), on, c.bookmarked()));
        return true;
    }

    public List<Candidate> query(double minScore, Comparator<Candidate> order) {
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : map.values()) if (c.score() >= minScore) out.add(c);
        out.sort(order);
        return out;
    }

    public static final Comparator<Candidate> BY_SCORE_DESC = Comparator.comparingDouble(Candidate::score).reversed();

    // ---- export / import (tab separated, one candidate per line) ----

    public String export() {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Candidate c : map.values()) {
            sb.append(c.dimension()).append('\t').append(c.chunkX()).append('\t').append(c.chunkZ()).append('\t')
              .append(c.score()).append('\t').append(c.label().name()).append('\t').append(c.detectedAtMs()).append('\t')
              .append(c.revisited()).append('\t').append(c.bookmarked()).append('\t').append(c.evidence()).append('\n');
        }
        return sb.toString();
    }

    /** @return {added, merged, skipped}. Malformed lines are skipped; duplicates are merged, never doubled. */
    public int[] importFrom(String text) {
        int added = 0, merged = 0, skipped = 0;
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            Candidate in = parseLine(line);
            if (in == null) { skipped++; continue; }
            Candidate old = map.get(in.key());
            if (old == null) { map.put(in.key(), in); added++; evictIfNeeded(); }
            else {
                boolean better = in.score() > old.score();
                map.put(in.key(), new Candidate(old.dimension(), old.chunkX(), old.chunkZ(),
                        better ? in.score() : old.score(), better ? in.label() : old.label(),
                        better ? in.evidence() : old.evidence(), Math.min(old.detectedAtMs(), in.detectedAtMs()),
                        old.revisited() || in.revisited(), old.bookmarked() || in.bookmarked()));
                merged++;
            }
        }
        return new int[] {added, merged, skipped};
    }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, export(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** @return {added, merged, skipped}, or all zeros if the file is missing/unreadable. */
    public int[] load(Path file) {
        try { return importFrom(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (IOException | RuntimeException e) { return new int[] {0, 0, 0}; }
    }

    private static Candidate parseLine(String line) {
        String[] f = line.split("\t", -1);
        if (f.length != 9) return null;
        try {
            String dim = f[0];
            if (dim.isBlank() || dim.chars().anyMatch(Character::isWhitespace)) return null;
            double score = Double.parseDouble(f[3]);
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || score > 1000) return null;
            Label label = Label.valueOf(f[4]);
            long t = Long.parseLong(f[5]);
            if (!isBool(f[6]) || !isBool(f[7])) return null;
            return new Candidate(dim, Integer.parseInt(f[1]), Integer.parseInt(f[2]), score, label, clean(f[8]), t,
                    Boolean.parseBoolean(f[6]), Boolean.parseBoolean(f[7]));
        } catch (IllegalArgumentException e) { // NumberFormatException is a subclass
            return null;
        }
    }

    private static boolean isBool(String s) { return s.equals("true") || s.equals("false"); }
    private static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " "); }

    /** Evict the oldest non-bookmarked entry when over capacity. */
    private void evictIfNeeded() {
        while (map.size() > max) {
            Iterator<Candidate> it = map.values().iterator();
            boolean removed = false;
            while (it.hasNext()) {
                if (!it.next().bookmarked()) { it.remove(); removed = true; break; }
            }
            if (!removed) break; // everything is bookmarked; keep them all
        }
    }
}
