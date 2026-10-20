package client.paint.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal event hub. Every subscribe returns the matching unsubscribe Runnable; modules pass it to
 * {@code addCleanup} so a disabled module can never leave a listener behind. Dispatch iterates a
 * snapshot, so listeners may unsubscribe (e.g. disable their module) while an event is being delivered.
 * A throwing listener is reported and does not stop the others.
 */
public final class EventBus {
    public enum WorldEventType { DEATH, DISCONNECT, DIMENSION_CHANGE, WORLD_LOAD }

    private final List<Runnable> tick = new ArrayList<>();
    private final List<Consumer<Double>> frame = new ArrayList<>();
    private final List<Consumer<WorldEventType>> world = new ArrayList<>();
    private final List<RenderListener> render = new ArrayList<>();
    /** When true, render listeners are skipped (Screenshot Mode hides every world overlay). */
    public volatile boolean renderSuppressed;
    private Consumer<String> errors = m -> System.err.println("[events] " + m);

    public interface RenderListener { void render(RenderSink sink, double dtSeconds); }

    public void setErrorSink(Consumer<String> e) { errors = e; }

    public Runnable onTick(Runnable r) { return add(tick, r); }
    public Runnable onFrame(Consumer<Double> r) { return add(frame, r); }
    public Runnable onWorldEvent(Consumer<WorldEventType> r) { return add(world, r); }
    public Runnable onRender(RenderListener r) { return add(render, r); }

    private <T> Runnable add(List<T> list, T item) {
        list.add(item);
        return () -> list.remove(item);
    }

    public int listenerCount() { return tick.size() + frame.size() + world.size() + render.size(); }

    public void fireTick() { for (Runnable r : new ArrayList<>(tick)) safe(r::run); }
    public void fireFrame(double dt) { for (Consumer<Double> r : new ArrayList<>(frame)) safe(() -> r.accept(dt)); }
    public void fireWorld(WorldEventType t) { for (Consumer<WorldEventType> r : new ArrayList<>(world)) safe(() -> r.accept(t)); }
    public void fireRender(RenderSink sink, double dt) { if (renderSuppressed) return; for (RenderListener r : new ArrayList<>(render)) safe(() -> r.render(sink, dt)); }

    private void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) { errors.accept("listener failed: " + t); }
    }
}
