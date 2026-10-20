package client.paint.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Waypoints, distance maths and compass bearings (Minecraft yaw convention: 0 = +Z, -90 = +X). */
public final class Waypoints {
    public record Waypoint(String name, String dimension, double x, double y, double z, String note) {
        public Waypoint(String name, String dimension, double x, double y, double z) { this(name, dimension, x, y, z, ""); }
    }

    private final Map<String, Waypoint> byKey = new LinkedHashMap<>();

    private static String key(String dim, String name) { return dim + "/" + name.toLowerCase(); }

    /** Adds or replaces (same dimension + case-insensitive name). @return false if name is blank. */
    public boolean put(Waypoint w) {
        if (w.name() == null || w.name().isBlank() || w.dimension() == null || w.dimension().isBlank()) return false;
        if (!Double.isFinite(w.x()) || !Double.isFinite(w.y()) || !Double.isFinite(w.z())) return false;
        byKey.put(key(w.dimension(), w.name()), w);
        return true;
    }

    public boolean remove(String dim, String name) { return byKey.remove(key(dim, name)) != null; }
    public int size() { return byKey.size(); }

    /** Waypoints in {@code dim}, nearest to (px,pz) first. */
    public List<Waypoint> nearest(String dim, double px, double pz) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : byKey.values()) if (w.dimension().equals(dim)) out.add(w);
        out.sort(Comparator.comparingDouble(w -> horizontalDistance(px, pz, w.x(), w.z())));
        return out;
    }

    public static double horizontalDistance(double x1, double z1, double x2, double z2) { return Math.hypot(x2 - x1, z2 - z1); }

    public static double distance3d(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
    }

    /** Minecraft yaw (degrees, [-180,180)) that faces from (px,pz) toward (tx,tz). */
    public static double yawTo(double px, double pz, double tx, double tz) {
        return wrap(Math.toDegrees(Math.atan2(-(tx - px), tz - pz)));
    }

    /** Degrees to turn: negative = turn left, positive = turn right, 0 = straight ahead. */
    public static double relativeBearing(double playerYaw, double px, double pz, double tx, double tz) {
        return wrap(yawTo(px, pz, tx, tz) - playerYaw);
    }

    /** Overworld -> Nether horizontal coordinate (8:1). */
    public static double overworldToNether(double v) { return v / 8.0; }
    public static double netherToOverworld(double v) { return v * 8.0; }

    /** Cardinal direction a yaw faces: "south (+Z)", "west (-X)", "north (-Z)", "east (+X)". */
    public static String facing(double yaw) {
        double y = wrap(yaw);
        if (y >= -45 && y < 45) return "south (+Z)";
        if (y >= 45 && y < 135) return "west (-X)";
        if (y >= -135 && y < -45) return "east (+X)";
        return "north (-Z)";
    }

    /** "842 m" below 1000, "1.2 km" above. */
    public static String formatDistance(double blocks) {
        return blocks < 1000 ? Math.round(blocks) + " m" : String.format("%.1f km", blocks / 1000.0);
    }

    private static double wrap(double d) {
        double y = d % 360.0;
        if (y >= 180) y -= 360;
        if (y < -180) y += 360;
        return y;
    }
}
