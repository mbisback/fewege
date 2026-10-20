package client.paint.core.render;

/**
 * Perspective projection of world points to screen pixels, using Minecraft's camera conventions
 * (yaw 0 faces +Z, pitch positive looks down, vertical field of view). Lets ESP be drawn in the 2D HUD pass
 * instead of the world render pipeline.
 */
public final class Projection {
    public static final double NEAR = 0.05;

    private double cx, cy, cz, w = 1, h = 1, scale = 1;
    private double fx, fy, fz, rx, ry, rz, ux, uy, uz;

    public void set(double camX, double camY, double camZ, float yawDeg, float pitchDeg, double fovYDeg, double screenW, double screenH) {
        cx = camX; cy = camY; cz = camZ; w = Math.max(1, screenW); h = Math.max(1, screenH);
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch), sp = Math.sin(pitch), sy = Math.sin(yaw), cyw = Math.cos(yaw);
        fx = -sy * cp; fy = -sp; fz = cyw * cp;
        rx = -cyw; ry = 0; rz = -sy;
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;
        double fov = Math.max(1, Math.min(170, fovYDeg));
        scale = (h / 2) / Math.tan(Math.toRadians(fov) / 2);
    }

    public double camX() { return cx; }
    public double camY() { return cy; }
    public double camZ() { return cz; }
    public double width() { return w; }
    public double height() { return h; }

    /** Camera-space coordinates {right, up, forward}. */
    public void toCamera(double x, double y, double z, double[] out) {
        double vx = x - cx, vy = y - cy, vz = z - cz;
        out[0] = vx * rx + vy * ry + vz * rz;
        out[1] = vx * ux + vy * uy + vz * uz;
        out[2] = vx * fx + vy * fy + vz * fz;
    }

    /** Project a camera-space point that is in front of the near plane. out = {screenX, screenY}. */
    public void cameraToScreen(double right, double up, double forward, double[] out) {
        out[0] = w / 2 + right / forward * scale;
        out[1] = h / 2 - up / forward * scale;
    }

    /** @return true (and fills out[0..1]) if the point is in front of the camera. */
    public boolean project(double x, double y, double z, double[] out) {
        double[] c = new double[3];
        toCamera(x, y, z, c);
        if (c[2] < NEAR) return false;
        cameraToScreen(c[0], c[1], c[2], out);
        return true;
    }
}
