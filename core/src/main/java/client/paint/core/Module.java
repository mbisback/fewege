package client.paint.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared module lifecycle.
 *
 * <ul>
 *   <li>{@link #onEnable()} runs on enable. If it throws, the module is rolled back to disabled
 *       and every cleanup registered so far runs.</li>
 *   <li>Anything that must be undone (event listeners, caches, background tasks) is registered
 *       with {@link #addCleanup(Runnable)} and is released in reverse order on disable.</li>
 *   <li>{@link #onDisable()} runs after cleanups. Errors in either are caught and reported to the
 *       error sink so one faulty module cannot crash the game or block the others.</li>
 * </ul>
 */
public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    public final Risk risk;

    private boolean enabled;
    private int keyCode = -1; // -1 = unbound
    private final List<Setting<?>> settings = new ArrayList<>();
    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private Consumer<String> errorSink = msg -> System.err.println("[module] " + msg);

    protected Module(String name, String description, Category category, Risk risk) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.risk = risk;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /** Register something to undo when this module is disabled. */
    protected void addCleanup(Runnable r) { cleanups.push(r); }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Modules that cannot be safely stopped by the panic button should override this. */
    public boolean panicSafe() { return true; }

    public final boolean isEnabled() { return enabled; }
    public final int getKeyCode() { return keyCode; }
    final void setKeyCodeInternal(int k) { keyCode = k; }
    public final List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }
    final void setErrorSink(Consumer<String> sink) { this.errorSink = sink; }

    public final void setEnabled(boolean target) {
        if (target == enabled) return;
        if (target) {
            enabled = true;
            try {
                onEnable();
            } catch (Throwable t) {
                errorSink.accept(name + " failed to enable: " + t);
                runDisable();
                enabled = false;
            }
        } else {
            runDisable();
            enabled = false;
        }
    }

    public final void toggle() { setEnabled(!enabled); }

    public final void resetSettings() { settings.forEach(Setting::reset); }

    private void runDisable() {
        while (!cleanups.isEmpty()) {
            try { cleanups.pop().run(); }
            catch (Throwable t) { errorSink.accept(name + " cleanup failed: " + t); }
        }
        try { onDisable(); }
        catch (Throwable t) { errorSink.accept(name + " failed to disable cleanly: " + t); }
    }
}
