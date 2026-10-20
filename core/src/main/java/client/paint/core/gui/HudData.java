package client.paint.core.gui;

import java.util.List;

/** Everything the HUD widgets display. The Minecraft layer implements it; previews/tests use a fake. */
public interface HudData {
    enum Key { W, A, S, D, SPACE, LMB, RMB }

    record Effect(String name, int rgb, int ticksLeft) {}
    record ArmourPiece(String name, int rgb, int percent) {}
    record ModuleDot(String name, int rgb) {}
    /** The entity being aimed at. health/maxHealth < 0 means unknown. */
    record Target(String name, double health, double maxHealth, double distance) {}
    record Counter(String label, int value) {}
    record StatLine(String label, String value) {}
    /** Nearest waypoint: bearing is degrees to turn (negative = left). */
    record WaypointInfo(String name, double distance, double bearingDeg) {}

    int fps();
    int ping();
    double x();
    double y();
    double z();
    float yaw();
    boolean inNether();
    List<Effect> effects();
    List<ArmourPiece> armour();
    List<ModuleDot> activeModules();
    boolean keyDown(Key k);
    int cpsLeft();
    int cpsRight();

    /** Minimap cell colour (ARGB, 0 = unknown) at an offset of (dx,dz) blocks-cells from the player. */
    int minimapColor(int dx, int dz);

    // ----- optional widgets (defaults keep simple fakes working)
    default Target target() { return null; }
    default List<Counter> counters() { return List.of(); }
    default List<StatLine> stats() { return List.of(); }
    default WaypointInfo nearestWaypoint() { return null; }
}
