package client.paint.mc;

import client.paint.core.Module;
import client.paint.core.ModuleManager;
import client.paint.core.gui.HudData;
import client.paint.core.gui.Theme;
import client.paint.core.hud.HudMath;
import client.paint.core.modules.UtilityModules;
import client.paint.core.modules.WorldModules;
import client.paint.core.world.TargetSelector;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** HUD data from the live game. VERIFY (26.3): getFps(), PlayerInfo#getLatency(), MobEffect#getColor/getDisplayName, MapColor#col. */
final class McHudData implements HudData {
    private final Minecraft mc = Minecraft.getInstance();
    private final McGame game;
    private final ModuleManager mm;
    final HudMath.CpsCounter left = new HudMath.CpsCounter(), right = new HudMath.CpsCounter();
    private boolean wasAttack, wasUse;
    private final int[] minimap = new int[17 * 17];
    private int minimapAge = 99;

    McHudData(McGame game, ModuleManager mm) { this.game = game; this.mm = mm; }

    /** Per client tick: CPS edge detection and minimap refresh. */
    void tick() {
        long now = System.currentTimeMillis();
        boolean a = mc.options.keyAttack.isDown(), u = mc.options.keyUse.isDown();
        if (a && !wasAttack) left.click(now);
        if (u && !wasUse) right.click(now);
        wasAttack = a; wasUse = u;
        if (++minimapAge >= 5) { minimapAge = 0; refreshMinimap(); }
    }

    private void refreshMinimap() {
        if (mc.level == null || mc.player == null) return;
        int px = (int) Math.floor(mc.player.getX()), pz = (int) Math.floor(mc.player.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = -8; dz <= 8; dz++) {
            for (int dx = -8; dx <= 8; dx++) {
                int x = px + dx, z = pz + dz, idx = (dz + 8) * 17 + (dx + 8);
                if (!mc.level.hasChunk(x >> 4, z >> 4)) { minimap[idx] = 0; continue; }
                int y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                pos.set(x, y, z);
                var state = mc.level.getBlockState(pos);
                minimap[idx] = 0xFF000000 | state.getMapColor(mc.level, pos).col;      // VERIFY: MapColor#col
            }
        }
    }

    @Override public int fps() { return mc.getFps(); }
    @Override public int ping() {
        if (mc.getConnection() == null || mc.player == null) return 0;
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }
    private double camXv() { return game.cameraActive() ? game.camX : mc.player.getX(); }
    private double camYv() { return game.cameraActive() ? game.camY : mc.player.getY(); }
    private double camZv() { return game.cameraActive() ? game.camZ : mc.player.getZ(); }
    @Override public double x() { return mc.player == null ? 0 : camXv(); }
    @Override public double y() { return mc.player == null ? 0 : camYv(); }
    @Override public double z() { return mc.player == null ? 0 : camZv(); }
    @Override public float yaw() { return game.cameraActive() ? game.camYaw : (mc.player == null ? 0 : mc.player.getYRot()); }
    @Override public boolean inNether() { return game.dimension().contains("nether"); }

    private static String roman(int n) { return switch (n) { case 0 -> " I"; case 1 -> " II"; case 2 -> " III"; case 3 -> " IV"; case 4 -> " V"; default -> " " + (n + 1); }; }

    @Override public List<Effect> effects() {
        List<Effect> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            var eff = e.getEffect().value();
            out.add(new Effect(eff.getDisplayName().getString() + roman(e.getAmplifier()), eff.getColor() & 0xFFFFFF, e.getDuration()));
        }
        return out;
    }

    @Override public List<ArmourPiece> armour() {
        List<ArmourPiece> out = new ArrayList<>();
        if (mc.player == null) return out;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] names = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (int i = 0; i < 4; i++) {
            ItemStack s = mc.player.getItemBySlot(slots[i]);
            if (s.isEmpty()) continue;
            int pct = HudMath.durabilityPercent(s.getDamageValue(), s.getMaxDamage());
            out.add(new ArmourPiece(names[i], 0x7CCBFF, pct < 0 ? 100 : pct));
        }
        return out;
    }

    @Override public List<ModuleDot> activeModules() {
        List<ModuleDot> out = new ArrayList<>();
        for (Module m : mm.all()) if (m.isEnabled()) out.add(new ModuleDot(m.name, Theme.categoryColor(m.category) & 0xFFFFFF));
        return out;
    }

    @Override public boolean keyDown(Key k) {
        var o = mc.options;
        return switch (k) { case W -> o.keyUp.isDown(); case A -> o.keyLeft.isDown(); case S -> o.keyDown.isDown(); case D -> o.keyRight.isDown();
            case SPACE -> o.keyJump.isDown(); case LMB -> o.keyAttack.isDown(); case RMB -> o.keyUse.isDown(); };
    }
    @Override public int cpsLeft() { return left.cps(System.currentTimeMillis()); }
    @Override public int cpsRight() { return right.cps(System.currentTimeMillis()); }
    @Override public int minimapColor(int dx, int dz) {
        if (dx < -8 || dx > 8 || dz < -8 || dz > 8) return 0;
        return minimap[(dz + 8) * 17 + (dx + 8)];
    }

    // ---------------------------------------------------------------- optional widgets
    @Override public Target target() {
        if (mc.player == null || mc.level == null) return null;
        var eye = mc.player.getEyePosition();
        TargetSelector.Pick p = TargetSelector.pick(game.entities(), eye.x, eye.y, eye.z, mc.player.getYRot(), mc.player.getXRot(), 64, 8, false);
        if (p == null) return null;
        return new Target(p.entity().name(), p.entity().health(), p.entity().maxHealth(), p.distance());
    }

    private int count(String... ids) {
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {                         // VERIFY: Inventory#getContainerSize/getItem
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();      // VERIFY: Identifier#getPath
            for (String w : ids) if (id.equals(w)) { n += s.getCount(); break; }
        }
        return n;
    }

    @Override public List<Counter> counters() {
        List<Counter> out = new ArrayList<>();
        if (mc.player == null) return out;
        out.add(new Counter("Totems", count("totem_of_undying")));
        out.add(new Counter("Golden apples", count("golden_apple", "enchanted_golden_apple")));
        out.add(new Counter("Ender pearls", count("ender_pearl")));
        out.add(new Counter("Arrows", count("arrow", "tipped_arrow", "spectral_arrow")));
        out.add(new Counter("End crystals", count("end_crystal")));
        int free = 0, slots = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int i = 0; i < slots; i++) if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        out.add(new Counter("Free slots", free));
        return out;
    }

    @Override public List<StatLine> stats() {
        if (!(mm.get("Session Stats") instanceof UtilityModules.SessionStats ss) || !ss.isEnabled()) {
            return List.of(new StatLine("Session Stats", "module off"));
        }
        long now = System.currentTimeMillis();
        return List.of(
                new StatLine("Session", HudMath.formatDuration(ss.stats().sessionMs(now))),
                new StatLine("Speed", String.format(Locale.US, "%.1f b/s", ss.stats().speedBps())),
                new StatLine("Distance", String.format(Locale.US, "%,d m", Math.round(ss.stats().distance()))),
                new StatLine("Jumps", String.valueOf(ss.stats().jumps())),
                new StatLine("Frame ms", String.format(Locale.US, "%.1f / %.1f", ss.perf().avgMs(), ss.perf().maxMs())));
    }

    @Override public WaypointInfo nearestWaypoint() {
        return mm.get("Waypoints") instanceof WorldModules.WaypointsModule w ? w.nearestInfo() : null;
    }
}
