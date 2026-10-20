package client.paint.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Owns every module: registration, lookup, keybinds (with conflict detection) and panic. */
public final class ModuleManager {
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private Consumer<String> errorSink = msg -> System.err.println("[module] " + msg);

    public void setErrorSink(Consumer<String> sink) {
        this.errorSink = sink;
        modules.values().forEach(m -> m.setErrorSink(sink));
    }

    public void register(Module m) {
        if (modules.putIfAbsent(m.name, m) != null) {
            throw new IllegalArgumentException("Duplicate module name: " + m.name);
        }
        m.setErrorSink(errorSink);
    }

    public Module get(String name) { return modules.get(name); }

    public List<Module> all() { return Collections.unmodifiableList(new ArrayList<>(modules.values())); }

    public List<Module> inCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules.values()) if (m.category == c) out.add(m);
        return out;
    }

    public int enabledCount(Category c) {
        int n = 0;
        for (Module m : inCategory(c)) if (m.isEnabled()) n++;
        return n;
    }

    /** Modules (other than {@code self}) already bound to {@code keyCode}. */
    public List<Module> keybindConflicts(Module self, int keyCode) {
        List<Module> out = new ArrayList<>();
        if (keyCode < 0) return out;
        for (Module m : modules.values()) if (m != self && m.getKeyCode() == keyCode) out.add(m);
        return out;
    }

    /**
     * Bind a key. Returns the conflicting modules; the bind is applied only if there are none, or
     * if {@code replaceConflicts} is true (conflicting modules are then unbound).
     */
    public List<Module> bind(Module m, int keyCode, boolean replaceConflicts) {
        List<Module> conflicts = keybindConflicts(m, keyCode);
        if (conflicts.isEmpty() || replaceConflicts) {
            conflicts.forEach(c -> c.setKeyCodeInternal(-1));
            m.setKeyCodeInternal(keyCode);
        }
        return conflicts;
    }

    public void unbind(Module m) { m.setKeyCodeInternal(-1); }

    /** Called from the key handler. @return true if a module was toggled. */
    public boolean handleKey(int keyCode) {
        if (keyCode < 0) return false;
        boolean toggled = false;
        for (Module m : modules.values()) {
            if (m.getKeyCode() == keyCode) { m.toggle(); toggled = true; }
        }
        return toggled;
    }

    /** Panic: disable every module that declares itself panic-safe. @return number disabled. */
    public int panic() {
        int n = 0;
        for (Module m : modules.values()) {
            if (m.isEnabled() && m.panicSafe()) { m.setEnabled(false); n++; }
        }
        return n;
    }
}
