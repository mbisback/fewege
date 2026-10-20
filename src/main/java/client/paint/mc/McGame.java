package client.paint.mc;

import client.paint.core.game.GameAccess;
import client.paint.core.game.ProjectileLaunch;
import client.paint.core.hud.NotificationCenter;
import client.paint.core.render.Trajectory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import client.paint.core.game.WorldView;
import client.paint.core.basefinder.BlockSource;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Minecraft implementation of GameAccess/WorldView. Everything version-sensitive is marked VERIFY.
 *
 * Freecam works WITHOUT mixins: a client-only RemotePlayer (never added to the world) becomes the camera entity,
 * the real player's input is swapped for an empty ClientInput, and mouse look is read as the change in the player's
 * rotation (which is then reset). Nothing is sent to the server because the real player does not move.
 */
final class McGame implements GameAccess, WorldView {
    private final Minecraft mc = Minecraft.getInstance();
    private final McBlocks blocks = new McBlocks();
    private final List<EntityInfo> entityCache = new ArrayList<>();
    private final List<StorageInfo> storageCache = new ArrayList<>();

    private boolean suppressed;
    private ClientInput savedInput;
    private float savedYaw, savedPitch;

    private RemotePlayer camEntity;
    private CameraType savedCameraType;
    private boolean camActive;
    double camX, camY, camZ; float camYaw, camPitch;

    private boolean weAddedNightVision;
    double fovMultiplier = 1.0;
    private Integer savedFov;

    // ---------------------------------------------------------------- state
    @Override public boolean inWorld() { return mc.player != null && mc.level != null; }
    @Override public boolean playerDead() { return mc.player != null && !mc.player.isAlive(); }
    @Override public String dimension() {
        if (mc.level == null) return "overworld";
        String s = mc.level.dimension().toString();           // "ResourceKey[minecraft:dimension / minecraft:the_nether]"
        int i = s.lastIndexOf(':');
        int j = s.indexOf(']', i);
        return i >= 0 && j > i ? s.substring(i + 1, j) : "overworld";
    }
    @Override public String playerName() { return mc.player == null ? "" : mc.player.getName().getString(); }
    @Override public double eyeX() { return mc.player.getEyePosition().x; }
    @Override public double eyeY() { return mc.player.getEyePosition().y; }
    @Override public double eyeZ() { return mc.player.getEyePosition().z; }
    @Override public double posX() { return mc.player.getX(); }
    @Override public double posY() { return mc.player.getY(); }
    @Override public double posZ() { return mc.player.getZ(); }
    @Override public float yaw() { return mc.player.getYRot(); }
    @Override public float pitch() { return mc.player.getXRot(); }

    // ---------------------------------------------------------------- input
    @Override public float moveForward() { return (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0); }
    @Override public float moveStrafe() { return (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0); }
    @Override public boolean jumpHeld() { return mc.options.keyJump.isDown(); }
    @Override public boolean sneakHeld() { return mc.options.keyShift.isDown(); }

    /** Toggle Sneak: drives the vanilla sneak key mapping. VERIFY: KeyMapping#setDown. */
    @Override public void holdSneak(boolean on) { mc.options.keyShift.setDown(on); }

    @Override public double attackCooldown() { return mc.player == null ? 1.0 : mc.player.getAttackStrengthScale(0.0f); }      // VERIFY
    @Override public double fallDistance() { return mc.player == null ? 0 : mc.player.fallDistance; }                          // VERIFY: field vs getFallDistance()
    @Override public boolean onGround() { return mc.player == null || mc.player.onGround(); }                                  // VERIFY
    @Override public boolean thirdPerson() { return !camActive && !mc.options.getCameraType().isFirstPerson(); }

    private static String itemPath(ItemStack st) { return st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(); }   // VERIFY: Identifier#getPath

    /** What the player is holding or drawing that launches a projectile (item registry names, so few Minecraft symbols are needed). */
    @Override public ProjectileLaunch heldProjectile() {
        Player p = mc.player;
        if (p == null) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack st = p.getItemInHand(hand);
            String id = itemPath(st);
            boolean using = p.isUsingItem() && p.getUsedItemHand() == hand;
            switch (id) {
                case "bow": if (using) return new ProjectileLaunch(Trajectory.Kind.ARROW, p.getTicksUsingItem() / 20.0); break;       // VERIFY: getTicksUsingItem
                case "crossbow": if (st.has(DataComponents.CHARGED_PROJECTILES) && !st.get(DataComponents.CHARGED_PROJECTILES).isEmpty())
                    return new ProjectileLaunch(Trajectory.Kind.CROSSBOW, 1.0); break;                                                   // VERIFY: ChargedProjectiles#isEmpty
                case "trident": if (using && p.getTicksUsingItem() >= 10) return new ProjectileLaunch(Trajectory.Kind.TRIDENT, 1.0); break;
                case "ender_pearl": return new ProjectileLaunch(Trajectory.Kind.PEARL, 1.0);
                case "snowball": return new ProjectileLaunch(Trajectory.Kind.SNOWBALL, 1.0);
                case "egg": return new ProjectileLaunch(Trajectory.Kind.EGG, 1.0);
                case "splash_potion": case "lingering_potion": return new ProjectileLaunch(Trajectory.Kind.POTION, 1.0);
                default: break;
            }
        }
        return null;
    }

    @Override public boolean segmentBlocked(double x1, double y1, double z1, double x2, double y2, double z2) {
        if (mc.level == null || mc.player == null) return false;
        return mc.level.clip(new ClipContext(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                .getType() != HitResult.Type.MISS;
    }

    // ---- camera pose for the self nametag (third person). VERIFY: Camera accessor names changed across versions.
    double[] cameraPose() {
        var cam = mc.gameRenderer.mainCamera();
        Vec3 pos = cam.position();                      // VERIFY: getPosition()/position()
        return new double[] {pos.x, pos.y, pos.z, cam.yRot(), cam.xRot()};   // VERIFY: getYRot()/yRot()
    }

    private NotificationCenter toasts;
    void setNotifications(NotificationCenter n) { toasts = n; }

    @Override public float[] consumeLookDelta() {
        if (!suppressed || mc.player == null) return new float[] {0, 0};
        float dy = Mth.wrapDegrees(mc.player.getYRot() - savedYaw), dp = mc.player.getXRot() - savedPitch;
        mc.player.setYRot(savedYaw); mc.player.setXRot(savedPitch);
        mc.player.yRotO = savedYaw; mc.player.xRotO = savedPitch;      // VERIFY: public fields yRotO/xRotO
        return new float[] {dy, dp};
    }

    @Override public void suppressPlayerInput(boolean on) {
        if (mc.player == null || on == suppressed) { if (!on) suppressed = false; return; }
        if (on) {
            savedInput = mc.player.input;                               // VERIFY: LocalPlayer#input (public field)
            mc.player.input = new ClientInput();                        // VERIFY: ClientInput has a public no-arg constructor
            savedYaw = mc.player.getYRot(); savedPitch = mc.player.getXRot();
            mc.player.setSprinting(false);
            suppressed = true;
        } else {
            if (savedInput != null) mc.player.input = savedInput;
            savedInput = null;
            suppressed = false;
        }
    }
    boolean isSuppressed() { return suppressed; }

    /** Called at the start of every client tick while input is suppressed: swallow attack/use/pick clicks. */
    void swallowClicks() {
        for (var k : new net.minecraft.client.KeyMapping[] {mc.options.keyAttack, mc.options.keyUse, mc.options.keyPickItem}) {
            while (k.consumeClick()) { /* discard */ }
            k.setDown(false);
        }
    }

    @Override public boolean sprintAllowed() {
        Player p = mc.player;
        return p != null && (p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly) && !p.isUsingItem();
    }
    @Override public void setSprinting(boolean on) { if (mc.player != null) mc.player.setSprinting(on); }

    // ---------------------------------------------------------------- camera / view
    @Override public void setCameraOverride(double x, double y, double z, float yaw, float pitch) {
        if (mc.level == null || mc.player == null) return;
        if (camEntity == null) {
            camEntity = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "paint-freecam"));   // VERIFY: constructor
            camEntity.setInvisible(true);
            savedCameraType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(camEntity);
        }
        camActive = true;
        camX = x; camY = y; camZ = z; camYaw = yaw; camPitch = pitch;
        camEntity.setPos(x, y - camEntity.getEyeHeight(), z);
        camEntity.setYRot(yaw); camEntity.setXRot(pitch); camEntity.yHeadRot = yaw;
        camEntity.setOldPosAndRot();                                                                         // VERIFY: exists
    }

    @Override public void clearCameraOverride() {
        camActive = false;
        if (camEntity != null) {
            if (mc.player != null) mc.setCameraEntity(mc.player);
            if (savedCameraType != null) mc.options.setCameraType(savedCameraType);
            camEntity = null; savedCameraType = null;
        }
    }
    boolean cameraActive() { return camActive; }

    @Override public void setFovMultiplier(double m) {
        fovMultiplier = m;
        var opt = mc.options.fov();
        if (m == 1.0) {
            if (savedFov != null) { opt.set(savedFov); savedFov = null; }
            return;
        }
        if (savedFov == null) savedFov = opt.get();
        opt.set(Mth.clamp((int) Math.round(savedFov * m), 30, 110));   // vanilla accepts 30..110 only, so zoom is limited
    }

    @Override public void setGammaOverride(double g) {
        if (mc.player == null || weAddedNightVision || mc.player.hasEffect(MobEffects.NIGHT_VISION)) return;
        mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        weAddedNightVision = true;
    }
    @Override public void clearGammaOverride() {
        if (weAddedNightVision && mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
        weAddedNightVision = false;
    }

    // ---------------------------------------------------------------- world view
    @Override public WorldView world() { return this; }
    @Override public BlockSource blocks() { return blocks; }
    @Override public long nowMs() { return System.currentTimeMillis(); }
    @Override public void notifyPlayer(String message) {
        if (toasts != null) toasts.push(message, System.currentTimeMillis());
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Paint] " + message));
    }

    /** Rebuild per-frame entity data once; modules read it several times per frame. */
    void beginFrame(float partial) {
        entityCache.clear();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {                      // VERIFY name
            if (e == camEntity) continue;
            EntityKind kind = e instanceof Player ? EntityKind.PLAYER : e instanceof Enemy ? EntityKind.HOSTILE
                    : e instanceof ItemEntity ? EntityKind.ITEM : e instanceof Projectile ? EntityKind.PROJECTILE
                    : e instanceof net.minecraft.world.entity.LivingEntity ? EntityKind.PASSIVE : EntityKind.OTHER;
            Vec3 p = e.getPosition(partial);
            String name = e instanceof ItemEntity ie ? ie.getItem().getHoverName().getString() + " x" + ie.getItem().getCount() : e.getName().getString();
            double hp = -1, maxHp = -1;
            if (e instanceof LivingEntity le) { hp = le.getHealth(); maxHp = le.getMaxHealth(); }
            entityCache.add(new EntityInfo(e.getId(), kind, name, e == mc.player, p.x, p.y, p.z, e.getBbWidth(), e.getBbHeight(), hp, maxHp));
        }
    }

    /** Refresh the loaded storage list (call every ~10 ticks). */
    void refreshStorage(int radiusChunks) {
        storageCache.clear();
        if (mc.level == null || mc.player == null) return;
        ChunkPos c = mc.player.chunkPosition();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                ChunkAccess ch = mc.level.getChunk(c.x() + dx, c.z() + dz, ChunkStatus.FULL, false);   // VERIFY: c.x() vs c.x field
                if (ch == null) continue;
                for (BlockPos pos : ch.getBlockEntitiesPos()) {
                    BlockEntity be = ch.getBlockEntity(pos);
                    if (be == null) continue;
                    StorageKind k = be instanceof TrappedChestBlockEntity ? StorageKind.TRAPPED_CHEST
                            : be instanceof ChestBlockEntity ? StorageKind.CHEST
                            : be instanceof EnderChestBlockEntity ? StorageKind.ENDER_CHEST
                            : be instanceof ShulkerBoxBlockEntity ? StorageKind.SHULKER
                            : be instanceof BarrelBlockEntity ? StorageKind.BARREL
                            : be instanceof SpawnerBlockEntity ? StorageKind.SPAWNER : null;
                    if (k != null) storageCache.add(new StorageInfo(k, be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ()));
                }
            }
        }
    }

    @Override public List<EntityInfo> entities() { return entityCache; }
    @Override public List<StorageInfo> storage() { return storageCache; }

    @Override public boolean lineOfSight(double x, double y, double z) {
        if (mc.level == null || mc.player == null) return true;
        Vec3 from = camActive ? new Vec3(camX, camY, camZ) : mc.player.getEyePosition(), to = new Vec3(x, y, z);
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }
}
