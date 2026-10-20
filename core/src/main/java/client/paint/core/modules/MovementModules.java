package client.paint.core.modules;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.Risk;
import client.paint.core.Setting;
import client.paint.core.freecam.FreecamCamera;
import client.paint.core.game.EventBus.WorldEventType;
import client.paint.core.game.GameAccess;
import client.paint.core.game.ModuleContext;

/** Toggle Sprint, Freecam, Zoom, Fullbright. */
public final class MovementModules {
    private MovementModules() {}

    public static final class ToggleSprint extends Module {
        private final ModuleContext ctx;
        public final Setting.Bool sideways = add(new Setting.Bool("Sprint sideways", false));

        public ToggleSprint(ModuleContext ctx) {
            super("Toggle Sprint", "Keeps you sprinting while you move, without holding the sprint key. Client-side input only.",
                    Category.MOVEMENT, Risk.SAFE);
            this.ctx = ctx;
        }

        @Override protected void onEnable() {
            addCleanup(ctx.events.onTick(this::tick));
            addCleanup(() -> ctx.game.setSprinting(false));
        }

        private void tick() {
            GameAccess g = ctx.game;
            if (!g.inWorld() || g.playerDead() || g.sneakHeld() || !g.sprintAllowed()) return;
            boolean moving = g.moveForward() > 0 || (sideways.get() && (g.moveStrafe() != 0 || g.moveForward() < 0));
            if (moving) g.setSprinting(true);
        }
    }

    /**
     * Independent client-side camera. The player entity is never moved (input is suppressed), so there is
     * nothing server-side to undo; stopping just clears the camera override.
     */
    public static final class Freecam extends Module {
        private final ModuleContext ctx;
        private final FreecamCamera cam = new FreecamCamera();
        public final Setting.Num speed = add(new Setting.Num("Speed (blocks/s)", 12, 1, 60, 1));
        public final Setting.Num sensitivity = add(new Setting.Num("Look sensitivity", 1.0, 0.2, 3.0, 0.1));

        public Freecam(ModuleContext ctx) {
            super("Freecam", "Detaches the camera so you can look around freely while your character stays put. Ends on death, disconnect or dimension change.",
                    Category.RENDER, Risk.LOW);
            this.ctx = ctx;
        }

        public FreecamCamera camera() { return cam; }

        @Override protected void onEnable() {
            GameAccess g = ctx.game;
            if (!g.inWorld() || g.playerDead()) throw new IllegalStateException("not in a world");
            cam.begin(g.eyeX(), g.eyeY(), g.eyeZ(), g.yaw(), g.pitch());
            addCleanup(() -> {
                cam.stop(FreecamCamera.StopReason.MODULE_DISABLED);
                ctx.game.clearCameraOverride();
                ctx.game.suppressPlayerInput(false);
            });
            g.suppressPlayerInput(true);
            g.setCameraOverride(cam.x, cam.y, cam.z, cam.yaw, cam.pitch);
            addCleanup(ctx.events.onFrame(this::frame));
            addCleanup(ctx.events.onWorldEvent(this::worldEvent));
        }

        private void frame(double dt) {
            GameAccess g = ctx.game;
            float[] look = g.consumeLookDelta();
            double s = sensitivity.get();
            cam.look((float) (look[0] * s), (float) (look[1] * s));
            double vertical = (g.jumpHeld() ? 1 : 0) - (g.sneakHeld() ? 1 : 0);
            cam.move(g.moveForward(), g.moveStrafe(), vertical, speed.get(), Math.min(dt, 0.1));
            g.setCameraOverride(cam.x, cam.y, cam.z, cam.yaw, cam.pitch);
        }

        private void worldEvent(WorldEventType t) {
            if (t == WorldEventType.DEATH || t == WorldEventType.DISCONNECT || t == WorldEventType.DIMENSION_CHANGE) {
                setEnabled(false);
            }
        }
    }

    public static final class Zoom extends Module {
        private final ModuleContext ctx;
        public final Setting.Num factor = add(new Setting.Num("Zoom factor", 4.0, 1.5, 10.0, 0.5));

        public Zoom(ModuleContext ctx) {
            super("Zoom", "Narrows the field of view for looking at distant things. Purely visual.", Category.RENDER, Risk.SAFE);
            this.ctx = ctx;
        }

        @Override protected void onEnable() {
            addCleanup(() -> ctx.game.setFovMultiplier(1.0));
            addCleanup(ctx.events.onFrame(dt -> ctx.game.setFovMultiplier(1.0 / factor.get())));
        }
    }

    public static final class Fullbright extends Module {
        private final ModuleContext ctx;

        public Fullbright(ModuleContext ctx) {
            super("Fullbright", "Makes dark areas bright using a client-side night-vision effect. Purely visual.", Category.RENDER, Risk.LOW);
            this.ctx = ctx;
        }

        @Override protected void onEnable() {
            addCleanup(() -> ctx.game.clearGammaOverride());
            addCleanup(ctx.events.onFrame(dt -> ctx.game.setGammaOverride(1.0)));
        }
    }
}
