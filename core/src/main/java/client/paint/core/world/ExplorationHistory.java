package client.paint.core.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Bounded set of chunks the player has been in, per dimension, with persistence. */
public final class ExplorationHistory {
    private final Set<String> visited = new LinkedHashSet<>();
    private final Map<String, Integer> perDim = new HashMap<>();
    private final int max;
    private int newThisSession;

    public ExplorationHistory(int max) { this.max = Math.max(1, max); }

    /** @return true if this chunk had not been visited before. */
    public boolean visit(String dim, int cx, int cz) {
        String k = dim + " " + cx + " " + cz;
        if (visited.contains(k)) return false;
        visited.add(k);
        perDim.merge(dim, 1, Integer::sum);
        newThisSession++;
        while (visited.size() > max) {
            String oldest = visited.iterator().next();
            visited.remove(oldest);
            String d = oldest.substring(0, oldest.indexOf(' '));
            perDim.computeIfPresent(d, (kk, v) -> v <= 1 ? null : v - 1);
        }
        return true;
    }

    public int count() { return visited.size(); }
    public int count(String dim) { return perDim.getOrDefault(dim, 0); }
    public int newThisSession() { return newThisSession; }
    public boolean has(String dim, int cx, int cz) { return visited.contains(dim + " " + cx + " " + cz); }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder sb = new StringBuilder("#paint-explored v1\n");
        for (String k : visited) sb.append(k).append('\n');
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Loads (merging); malformed lines are skipped. Loaded chunks do not count as "new this session". */
    public void load(Path file) {
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] f = line.split(" ");
                if (f.length != 3) continue;
                try { Integer.parseInt(f[1]); Integer.parseInt(f[2]); } catch (NumberFormatException e) { continue; }
                int before = newThisSession;
                visit(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                newThisSession = before;
            }
        } catch (IOException | RuntimeException ignored) { /* start empty */ }
    }
}
