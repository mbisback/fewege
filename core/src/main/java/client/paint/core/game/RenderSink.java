package client.paint.core.game;

/** World-space drawing, implemented by the Minecraft adapter. Coordinates are world coordinates. */
public interface RenderSink {
    double camX(); double camY(); double camZ();
    /** Cheap frustum test for an axis-aligned box. */
    boolean inFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
             int outlineArgb, int fillArgb, float thickness, boolean throughWalls);
    void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float thickness);
    /** Line from the crosshair (screen centre) to a world point. */
    void lineFromCrosshair(double x, double y, double z, int argb, float thickness);
    void label(String text, double x, double y, double z, int argb, float scale);
}
