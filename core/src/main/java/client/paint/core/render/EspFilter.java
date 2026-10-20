package client.paint.core.render;

/** Cheap per-target decision made before any drawing: distance, visibility mode and opacity. */
public final class EspFilter {
    public enum Visibility { ALWAYS, ONLY_WHEN_OBSTRUCTED, ONLY_WHEN_VISIBLE }

    public double maxDistance = 128;
    public Visibility visibility = Visibility.ALWAYS;
    public double minOpacity = 0.25; // opacity at max distance (fade-out)

    /** Squared distance avoids a sqrt per entity per frame. */
    public boolean shouldRender(double distSq, boolean lineOfSight) {
        if (distSq > maxDistance * maxDistance) return false;
        return switch (visibility) {
            case ALWAYS -> true;
            case ONLY_WHEN_OBSTRUCTED -> !lineOfSight;
            case ONLY_WHEN_VISIBLE -> lineOfSight;
        };
    }

    /** 1.0 up close, fading linearly to {@link #minOpacity} at {@link #maxDistance}. */
    public double opacity(double distSq) {
        double t = Math.min(1.0, Math.sqrt(distSq) / maxDistance);
        return 1.0 - t * (1.0 - minOpacity);
    }
}
