package client.paint.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Versioned, crash-safe config persistence (UTF-8 key=value text).
 *
 * <p>Writes go to a temp file and are moved into place, so a crash mid-save cannot truncate the
 * existing config. On load, an unreadable file is renamed to {@code *.corrupt} and defaults are
 * kept; an individual invalid value is skipped without affecting any other setting.
 */
public final class ConfigStore {
    public static final int CURRENT_VERSION = 1;

    public static final class LoadResult {
        public final boolean fileFound;
        public final boolean fileCorrupt;
        public final int fileVersion;
        public final int applied;
        public final int rejected;

        LoadResult(boolean found, boolean corrupt, int version, int applied, int rejected) {
            this.fileFound = found; this.fileCorrupt = corrupt; this.fileVersion = version;
            this.applied = applied; this.rejected = rejected;
        }
    }

    private ConfigStore() {}

    public static void save(ModuleManager mm, Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("configVersion", Integer.toString(CURRENT_VERSION));
        for (Module m : mm.all()) {
            String base = "module." + m.name + ".";
            p.setProperty(base + "enabled", Boolean.toString(m.isEnabled()));
            p.setProperty(base + "key", Integer.toString(m.getKeyCode()));
            for (Setting<?> s : m.settings()) p.setProperty(base + "setting." + s.name, s.serialize());
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "Paint Client config");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static LoadResult load(ModuleManager mm, Path file) {
        if (!Files.exists(file)) return new LoadResult(false, false, 0, 0, 0);
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | RuntimeException e) { // malformed escapes throw IllegalArgumentException
            quarantine(file);
            return new LoadResult(true, true, 0, 0, 0);
        }
        int version = parseInt(p.getProperty("configVersion"), 0);
        int applied = 0, rejected = 0;
        for (Module m : mm.all()) {
            String base = "module." + m.name + ".";
            String key = p.getProperty(base + "key");
            if (key != null) {
                int k = parseInt(key, Integer.MIN_VALUE);
                if (k == Integer.MIN_VALUE || k < -1) rejected++;
                else { m.setKeyCodeInternal(k); applied++; }
            }
            for (Setting<?> s : m.settings()) {
                String v = p.getProperty(base + "setting." + s.name);
                if (v == null) continue;
                if (s.parse(v)) applied++; else rejected++;
            }
            String en = p.getProperty(base + "enabled");
            if (en != null) {
                if ("true".equalsIgnoreCase(en)) { m.setEnabled(true); applied++; }
                else if ("false".equalsIgnoreCase(en)) { m.setEnabled(false); applied++; }
                else rejected++;
            }
        }
        return new LoadResult(true, false, version, applied, rejected);
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) { /* keep running with defaults */ }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
