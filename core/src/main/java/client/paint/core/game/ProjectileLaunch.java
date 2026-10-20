package client.paint.core.game;

/** A projectile the player is about to launch. {@code charge} is 0..1 (bow draw); other kinds ignore it. */
public record ProjectileLaunch(client.paint.core.render.Trajectory.Kind kind, double charge) {}
