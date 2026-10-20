package client.paint.core.game;

/**
 * Everything modules need from the game. Implemented once by the Minecraft adapter (src/); modules and
 * their tests only ever see this interface, so they are testable without Minecraft.
 */
public interface GameAccess {
    boolean inWorld();
    boolean playerDead();
    String dimension();          // e.g. "overworld", "the_nether", "the_end"
    String playerName();

    double eyeX(); double eyeY(); double eyeZ();
    double posX(); double posY(); double posZ();
    float yaw(); float pitch();  // Minecraft convention (yaw 0 = +Z, pitch + = down)

    // ----- input (raw key state; valid even while player input is suppressed)
    float moveForward();         // +1 W, -1 S
    float moveStrafe();          // +1 D, -1 A
    boolean jumpHeld();
    boolean sneakHeld();
    /** Mouse look since last call, degrees {dYaw, dPitch}. Only meaningful while input is suppressed. */
    float[] consumeLookDelta();
    /** While true the real player must not move, look, attack or interact (used by Freecam). */
    void suppressPlayerInput(boolean on);

    boolean sprintAllowed();     // hunger/state permits sprinting
    void setSprinting(boolean on);

    // ----- camera / view overrides (client-side only)
    void setCameraOverride(double x, double y, double z, float yaw, float pitch);
    void clearCameraOverride();
    void setFovMultiplier(double m);   // 1.0 = normal
    void setGammaOverride(double g);
    void clearGammaOverride();

    // ----- extras (defaults keep simple fakes working)
    default void holdSneak(boolean on) { }
    default double attackCooldown() { return 1.0; }      // 0..1, 1 = ready
    default double fallDistance() { return 0; }
    default boolean onGround() { return true; }
    default boolean thirdPerson() { return false; }
    /** What the player is currently holding/charging that launches a projectile, or null. */
    default ProjectileLaunch heldProjectile() { return null; }

    WorldView world();
    long nowMs();
    void notifyPlayer(String message);
}
