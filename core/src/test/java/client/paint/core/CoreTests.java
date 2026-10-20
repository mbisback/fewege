package client.paint.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free test runner (JUnit could not be downloaded in the build sandbox). */
public class CoreTests {
    static int passed, failed;

    enum Mode { SMOOTH, SNAP }

    static class Probe extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 3.6, 1.0, 6.0, 0.1));
        final Setting.Bool flag = add(new Setting.Bool("Flag", false));
        final Setting.Choice<Mode> mode = add(new Setting.Choice<>("Mode", Mode.class, Mode.SMOOTH));
        int enables, disables, cleanupRuns;
        boolean failOnEnable;

        Probe(String name, Category c) { super(name, "probe", c, Risk.LOW); }

        @Override protected void onEnable() {
            enables++;
            addCleanup(() -> cleanupRuns++);
            if (failOnEnable) throw new IllegalStateException("boom");
        }
        @Override protected void onDisable() { disables++; }
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS " + name); }
        else { failed++; System.out.println("FAIL " + name); }
    }

    static ModuleManager manager(Probe... ps) {
        ModuleManager mm = new ModuleManager();
        List<String> errors = new ArrayList<>();
        mm.setErrorSink(errors::add);
        for (Probe p : ps) mm.register(p);
        return mm;
    }

    static void runAll() throws Exception {
        lifecycle();
        settings();
        keybinds();
        panic();
        config();
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void lifecycle() {
        Probe p = new Probe("A", Category.COMBAT);
        manager(p);
        p.setEnabled(true);
        check("enable runs onEnable once", p.isEnabled() && p.enables == 1);
        p.setEnabled(true);
        check("double enable is a no-op", p.enables == 1);
        p.setEnabled(false);
        check("disable runs cleanups and onDisable", !p.isEnabled() && p.cleanupRuns == 1 && p.disables == 1);
        p.setEnabled(false);
        check("double disable is a no-op", p.disables == 1);

        Probe bad = new Probe("Bad", Category.MISC);
        bad.failOnEnable = true;
        manager(bad);
        bad.setEnabled(true);
        check("throwing onEnable rolls back to disabled", !bad.isEnabled());
        check("rollback releases resources registered before the throw", bad.cleanupRuns == 1);

        p.toggle(); p.toggle(); p.toggle(); p.toggle();
        check("repeated toggling never leaks cleanups", p.cleanupRuns == p.enables);
    }

    static void settings() {
        Probe p = new Probe("S", Category.RENDER);
        check("in-range number accepted", p.range.set(5.0) && p.range.get() == 5.0);
        check("out-of-range number rejected", !p.range.set(99.0) && p.range.get() == 5.0);
        check("NaN rejected", !p.range.set(Double.NaN) && p.range.get() == 5.0);
        p.range.setClamped(99.0);
        check("setClamped clamps to max", p.range.get() == 6.0);
        check("parse garbage rejected", !p.range.parse("abc") && p.range.get() == 6.0);
        check("enum parse works", p.mode.parse("SNAP") && p.mode.get() == Mode.SNAP);
        check("enum parse invalid rejected", !p.mode.parse("NOPE") && p.mode.get() == Mode.SNAP);
        p.resetSettings();
        check("resetSettings restores defaults",
                p.range.get() == 3.6 && !p.flag.get() && p.mode.get() == Mode.SMOOTH);
        boolean threw = false;
        try { new Setting.Num("x", 10, 0, 5, 1); } catch (IllegalArgumentException e) { threw = true; }
        check("invalid default rejected at construction", threw);
    }

    static void keybinds() {
        Probe a = new Probe("A", Category.COMBAT), b = new Probe("B", Category.MOVEMENT);
        ModuleManager mm = manager(a, b);
        check("first bind has no conflicts", mm.bind(a, 82, false).isEmpty() && a.getKeyCode() == 82);
        List<Module> c = mm.bind(b, 82, false);
        check("conflict detected and bind refused", c.size() == 1 && c.get(0) == a && b.getKeyCode() == -1);
        mm.bind(b, 82, true);
        check("replace-conflicts rebinds and unbinds the old owner", b.getKeyCode() == 82 && a.getKeyCode() == -1);
        check("rebinding a module to its own key is not a conflict", mm.bind(b, 82, false).isEmpty());
        check("unbound (-1) never conflicts", mm.keybindConflicts(a, -1).isEmpty());
        check("handleKey toggles bound module", mm.handleKey(82) && b.isEnabled());
        check("handleKey ignores unbound keys", !mm.handleKey(999));
        boolean threw = false;
        try { mm.register(new Probe("A", Category.MISC)); } catch (IllegalArgumentException e) { threw = true; }
        check("duplicate module name rejected", threw);
    }

    static void panic() {
        Probe a = new Probe("A", Category.COMBAT), b = new Probe("B", Category.COMBAT);
        Probe keep = new Probe("Keep", Category.MISC) { @Override public boolean panicSafe() { return false; } };
        ModuleManager mm = manager(a, b, keep);
        a.setEnabled(true); b.setEnabled(true); keep.setEnabled(true);
        check("enabledCount per category", mm.enabledCount(Category.COMBAT) == 2);
        int n = mm.panic();
        check("panic disables panic-safe modules only", n == 2 && !a.isEnabled() && !b.isEnabled() && keep.isEnabled());
        check("panic runs cleanups", a.cleanupRuns == 1 && b.cleanupRuns == 1);
    }

    static void config() throws Exception {
        Path dir = Files.createTempDirectory("paintcfg");
        Path file = dir.resolve("config.properties");

        Probe a = new Probe("Kill Aura", Category.COMBAT);
        ModuleManager mm = manager(a);
        a.range.set(4.5); a.flag.set(true); a.mode.set(Mode.SNAP);
        mm.bind(a, 82, false);
        a.setEnabled(true);
        ConfigStore.save(mm, file);

        Probe a2 = new Probe("Kill Aura", Category.COMBAT);
        ModuleManager mm2 = manager(a2);
        ConfigStore.LoadResult r = ConfigStore.load(mm2, file);
        check("roundtrip restores values, key and enabled state",
                r.fileFound && !r.fileCorrupt && r.fileVersion == ConfigStore.CURRENT_VERSION
                        && a2.range.get() == 4.5 && a2.flag.get() && a2.mode.get() == Mode.SNAP
                        && a2.getKeyCode() == 82 && a2.isEnabled());
        check("no leftover temp file after save", !Files.exists(dir.resolve("config.properties.tmp")));

        // One bad value must not discard the rest.
        Files.writeString(file, "configVersion=1\n"
                + "module.Kill\\ Aura.setting.Range=999\n"
                + "module.Kill\\ Aura.setting.Flag=true\n"
                + "module.Kill\\ Aura.key=notanumber\n", StandardCharsets.UTF_8);
        Probe a3 = new Probe("Kill Aura", Category.COMBAT);
        ConfigStore.LoadResult r3 = ConfigStore.load(manager(a3), file);
        check("invalid values skipped, valid ones kept",
                r3.rejected == 2 && a3.range.get() == 3.6 && a3.flag.get() && a3.getKeyCode() == -1);

        // Corrupt file: malformed unicode escape makes Properties.load throw.
        Files.writeString(file, "configVersion=1\nbad=\\uZZZZ\n", StandardCharsets.UTF_8);
        Probe a4 = new Probe("Kill Aura", Category.COMBAT);
        ConfigStore.LoadResult r4 = ConfigStore.load(manager(a4), file);
        check("corrupt file is quarantined, defaults kept, no exception",
                r4.fileCorrupt && !Files.exists(file)
                        && Files.exists(dir.resolve("config.properties.corrupt")) && a4.range.get() == 3.6);

        ConfigStore.LoadResult r5 = ConfigStore.load(manager(new Probe("Kill Aura", Category.COMBAT)), file);
        check("missing file reported, not an error", !r5.fileFound && !r5.fileCorrupt);

        // Config from a newer version still loads the keys we understand.
        Files.writeString(file, "configVersion=99\nmodule.Kill\\ Aura.setting.Flag=true\n", StandardCharsets.UTF_8);
        Probe a6 = new Probe("Kill Aura", Category.COMBAT);
        ConfigStore.LoadResult r6 = ConfigStore.load(manager(a6), file);
        check("newer-version config loads recognised keys", r6.fileVersion == 99 && a6.flag.get());
    }
}
