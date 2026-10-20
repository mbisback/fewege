package client.paint.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** Named config profiles (copies of the config file) with import/export. Names are restricted to a safe charset. */
public final class ProfileManager {
    private final Path dir;

    public ProfileManager(Path dir) { this.dir = dir; }

    public static boolean validName(String n) { return n != null && n.matches("[A-Za-z0-9_-]{1,32}"); }

    private Path file(String name) { return dir.resolve(name + ".properties"); }

    public boolean save(String name, ModuleManager mm) {
        if (!validName(name)) return false;
        try { ConfigStore.save(mm, file(name)); return true; } catch (IOException e) { return false; }
    }

    /** @return null if the profile does not exist or the name is invalid. */
    public ConfigStore.LoadResult load(String name, ModuleManager mm) {
        if (!validName(name) || !Files.exists(file(name))) return null;
        // A profile replaces the current state: start from defaults so unset values do not linger.
        for (Module m : mm.all()) { m.setEnabled(false); m.resetSettings(); mm.unbind(m); }
        return ConfigStore.load(mm, file(name));
    }

    public List<String> list() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".properties"))
             .map(n -> n.substring(0, n.length() - ".properties".length())).filter(ProfileManager::validName).forEach(out::add);
        } catch (IOException ignored) { /* empty list */ }
        Collections.sort(out);
        return out;
    }

    public boolean delete(String name) {
        if (!validName(name)) return false;
        try { return Files.deleteIfExists(file(name)); } catch (IOException e) { return false; }
    }

    /** Copy a profile out to any path (export). */
    public boolean exportTo(String name, Path target) {
        if (!validName(name) || !Files.exists(file(name))) return false;
        try { Files.copy(file(name), target, StandardCopyOption.REPLACE_EXISTING); return true; } catch (IOException e) { return false; }
    }

    /** Import a config file as a profile. The file must parse (corrupt files are rejected). */
    public boolean importFrom(Path source, String name) {
        if (!validName(name) || !Files.exists(source)) return false;
        try {
            java.util.Properties p = new java.util.Properties();
            try (var r = Files.newBufferedReader(source, java.nio.charset.StandardCharsets.UTF_8)) { p.load(r); }
            Files.createDirectories(dir);
            Files.copy(source, file(name), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) { return false; }
    }

    /** Reset: disable everything, restore default settings, clear keybinds. */
    public static void resetAll(ModuleManager mm) {
        for (Module m : mm.all()) { m.setEnabled(false); m.resetSettings(); mm.unbind(m); }
    }
}
