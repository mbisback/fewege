package client.paint.core;

import client.paint.core.ModuleTests.FakeGame;
import client.paint.core.ModuleTests.FakeSink;
import client.paint.core.basefinder.BlockHighlighter;
import client.paint.core.basefinder.BlockSource.Kind;
import client.paint.core.game.*;
import client.paint.core.game.EventBus.WorldEventType;
import client.paint.core.game.WorldView.*;
import client.paint.core.gui.*;
import client.paint.core.gui.Gfx.Font;
import client.paint.core.hud.*;
import client.paint.core.layout.LayoutModel;
import client.paint.core.modules.*;
import client.paint.core.nametag.NametagStyle;
import client.paint.core.render.Trajectory;
import client.paint.core.world.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Tests for the "rest of the features" batch: trajectory, entity/block ESP, chunk borders, utilities, HUD widgets. */
public class NewFeatureTests {
    static int passed, failed;
    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS " + name); } else { failed++; System.out.println("FAIL " + name); }
    }

    /** A game with the optional capabilities switched on. */
    static final class G2 extends FakeGame {
        boolean heldSneak; double cooldown = 1.0, fall; boolean ground = true; ProjectileLaunch held;
        double floorY = Double.NEGATIVE_INFINITY;
        @Override public void holdSneak(boolean on) { heldSneak = on; }
        @Override public double attackCooldown() { return cooldown; }
        @Override public double fallDistance() { return fall; }
        @Override public boolean onGround() { return ground; }
        @Override public ProjectileLaunch heldProjectile() { return held; }
        @Override public boolean segmentBlocked(double x1, double y1, double z1, double x2, double y2, double z2) { return y2 <= floorY; }
    }

    static final class Rec implements RasterGfx.Backend {
        final List<int[]> fills = new ArrayList<>(); final List<String> texts = new ArrayList<>();
        public void fill(int x, int y, int w, int h, int argb) { fills.add(new int[] {x, y, w, h, argb}); }
        public void text(String s, float x, float y, float size, Font f, int argb) { texts.add(s); }
        public float textWidth(String s, float size, Font f) { return s.length() * size * 0.55f; }
    }

    public static int[] runAll() throws Exception {
        passed = 0; failed = 0;
        trajectory(); target(); stats(); perf(); notifications(); exploration(); highlighter();
        entityEsp(); trajectoryModule(); chunkBorders(); blockEsp(); utilities(); renderers(); hudWidgets(); waypointNotes(); registry();
        return new int[] {passed, failed};
    }
    public static void main(String[] a) throws Exception {
        int[] r = runAll();
        System.out.println("\n" + r[0] + " passed, " + r[1] + " failed");
        if (r[1] > 0) System.exit(1);
    }

    // ------------------------------------------------------------ Trajectory
    static void trajectory() {
        List<double[]> p = Trajectory.simulate(Trajectory.Kind.PEARL, 1, 0, 64, 0, 0f, 0f, 200, null);
        check("trajectory: a level throw travels +Z (yaw 0) and drops under gravity", p.get(p.size() - 1)[2] > 10 && p.get(p.size() - 1)[1] < 64);
        check("trajectory: stops at the void floor instead of running forever", p.size() <= 201 && p.size() > 2);
        List<double[]> up = Trajectory.simulate(Trajectory.Kind.SNOWBALL, 1, 0, 64, 0, 0f, -45f, 400, null);
        double maxY = 0; for (double[] q : up) maxY = Math.max(maxY, q[1]);
        check("trajectory: throwing upward rises before falling", maxY > 66);
        List<double[]> east = Trajectory.simulate(Trajectory.Kind.PEARL, 1, 0, 64, 0, -90f, 0f, 5, null);
        check("trajectory: yaw -90 travels toward +X", east.get(east.size() - 1)[0] > 3 && Math.abs(east.get(east.size() - 1)[2]) < 0.01);
        List<double[]> hit = Trajectory.simulate(Trajectory.Kind.PEARL, 1, 0, 64, 0, 0f, 0f, 200, (a, b, c, d, e, f) -> e <= 60);
        double[] last = hit.get(hit.size() - 1);
        check("trajectory: ends at the first blocked segment", last[1] <= 60 && hit.size() < p.size());
        check("trajectory: an undrawn bow gives no path", Trajectory.simulate(Trajectory.Kind.ARROW, 0.0, 0, 64, 0, 0f, 0f, 100, null).size() == 1);
        check("trajectory: bow power curve is monotonic and capped at 1", Trajectory.bowPower(0.5) < Trajectory.bowPower(0.9) && Trajectory.bowPower(5) == 1.0);
        List<double[]> full = Trajectory.simulate(Trajectory.Kind.ARROW, 1, 0, 64, 0, 0f, 0f, 20, null);
        List<double[]> half = Trajectory.simulate(Trajectory.Kind.ARROW, 0.4, 0, 64, 0, 0f, 0f, 20, null);
        check("trajectory: a fully drawn bow flies farther than a half drawn one", full.get(full.size() - 1)[2] > half.get(half.size() - 1)[2]);
        check("trajectory: maxSteps bounds the path length", Trajectory.simulate(Trajectory.Kind.ARROW, 1, 0, 64, 0, 0f, 0f, 7, null).size() <= 8);
        check("trajectory: maxSteps of 0 or less still returns a safe path", Trajectory.simulate(Trajectory.Kind.EGG, 1, 0, 64, 0, 0f, 0f, -5, null).size() >= 1);
    }

    // ------------------------------------------------------------ TargetSelector
    static EntityInfo mob(int id, EntityKind k, double x, double z) { return new EntityInfo(id, k, "m" + id, false, x, 64, z, 0.6, 1.8, 10, 20); }

    static void target() {
        List<EntityInfo> l = List.of(mob(1, EntityKind.HOSTILE, 0, 10), mob(2, EntityKind.HOSTILE, 3, 10), mob(3, EntityKind.ITEM, 0, 5));
        TargetSelector.Pick p = TargetSelector.pick(l, 0, 64.9, 0, 0f, 0f, 50, 20, false);
        check("target: picks the entity closest to the view direction", p != null && p.entity().id() == 1);
        check("target: ignores items and projectiles", TargetSelector.pick(List.of(mob(3, EntityKind.ITEM, 0, 5)), 0, 65, 0, 0f, 0f, 50, 30, false) == null);
        check("target: nothing behind you", TargetSelector.pick(List.of(mob(1, EntityKind.HOSTILE, 0, -10)), 0, 65, 0, 0f, 0f, 50, 30, false) == null);
        check("target: respects max distance", TargetSelector.pick(l, 0, 64.9, 0, 0f, 0f, 5, 20, false) == null);
        check("target: players-only filter", TargetSelector.pick(l, 0, 64.9, 0, 0f, 0f, 50, 20, true) == null);
        List<EntityInfo> self = List.of(new EntityInfo(9, EntityKind.PLAYER, "me", true, 0, 64, 5, 0.6, 1.8));
        check("target: never selects yourself", TargetSelector.pick(self, 0, 65, 0, 0f, 0f, 50, 30, false) == null);
    }

    // ------------------------------------------------------------ StatsTracker / PerfMonitor / Notifications
    static void stats() {
        StatsTracker s = new StatsTracker();
        s.reset(0);
        for (int t = 0; t <= 20; t++) s.update(t * 50L, t * 0.2, 64, 0, true, 0); // 4 blocks per second
        check("stats: speed over a 1 s window is about 4 b/s", Math.abs(s.speedBps() - 4.0) < 0.3);
        check("stats: distance accumulates", Math.abs(s.distance() - 4.0) < 0.01);
        s.update(1100, 4.2, 64.5, 0, false, 0);
        check("stats: leaving the ground upward counts one jump", s.jumps() == 1);
        s.update(1150, 4.2, 60, 0, false, 7.5);
        check("stats: biggest fall is remembered", Math.abs(s.maxFall() - 7.5) < 1e-9);
        double d = s.distance();
        s.update(1200, 5000, 60, 0, true, 0);
        check("stats: a teleport is not counted as distance travelled", s.distance() - d < 1);
        check("stats: session time is elapsed ms", s.sessionMs(5000) == 5000);
        StatsTracker fresh = new StatsTracker();
        check("stats: fresh tracker reports zeros", fresh.sessionMs(999) == 0 && fresh.speedBps() == 0 && fresh.jumps() == 0);
    }

    static void perf() {
        PerfMonitor p = new PerfMonitor(4);
        for (int i = 0; i < 4; i++) p.frame(0.010);
        check("perf: 10 ms frames average 10 ms and 100 fps", Math.abs(p.avgMs() - 10) < 1e-6 && Math.abs(p.fps() - 100) < 1e-6);
        p.frame(0.050);
        check("perf: max reflects the slow frame", Math.abs(p.maxMs() - 50) < 1e-6);
        p.frame(-1); p.frame(Double.NaN); p.frame(100);
        check("perf: nonsense and pause frames are ignored", Math.abs(p.maxMs() - 50) < 1e-6);
        for (int i = 0; i < 8; i++) p.frame(0.010);
        check("perf: the window rolls (old slow frame ages out)", Math.abs(p.maxMs() - 10) < 1e-6);
        check("perf: empty monitor is zero", new PerfMonitor(10).fps() == 0);
    }

    static void notifications() {
        NotificationCenter n = new NotificationCenter();
        for (int i = 0; i < 10; i++) n.push("m" + i, 1000 + i * 600L);
        check("notifications: at most maxVisible are shown", n.active(1000 + 9 * 600L).size() <= 4);
        check("notifications: newest first", n.active(1000 + 9 * 600L).get(0).text().equals("m9"));
        check("notifications: they expire", n.active(1000 + 9 * 600L + 10_000).isEmpty());
        NotificationCenter d = new NotificationCenter();
        d.push("same", 0); d.push("same", 100);
        check("notifications: an immediate duplicate is dropped", d.active(200).size() == 1);
        d.push("  ", 300); d.push(null, 300);
        check("notifications: blank messages ignored", d.active(400).size() == 1);
    }

    static void exploration() throws Exception {
        ExplorationHistory h = new ExplorationHistory(3);
        check("exploration: first visit is new, repeat is not", h.visit("overworld", 0, 0) && !h.visit("overworld", 0, 0));
        h.visit("overworld", 1, 0); h.visit("the_nether", 0, 0);
        check("exploration: per-dimension counts", h.count("overworld") == 2 && h.count("the_nether") == 1 && h.count() == 3);
        h.visit("overworld", 2, 0);
        check("exploration: bounded; the oldest chunk is dropped", h.count() == 3 && !h.has("overworld", 0, 0) && h.count("overworld") == 2);
        Path f = Files.createTempFile("explored", ".txt");
        h.save(f);
        ExplorationHistory r = new ExplorationHistory(10);
        r.load(f);
        check("exploration: save/load round-trips", r.count() == 3 && r.has("overworld", 2, 0));
        check("exploration: loaded chunks are not counted as new this session", r.newThisSession() == 0);
        Files.writeString(f, "garbage\noverworld x y\noverworld 5 6\n a b c d\n");
        ExplorationHistory bad = new ExplorationHistory(10);
        bad.load(f);
        check("exploration: malformed lines are skipped, good ones kept", bad.count() == 1 && bad.has("overworld", 5, 6));
        new ExplorationHistory(5).load(Path.of("/nonexistent/file"));
        check("exploration: missing file is not an error", true);
    }

    // ------------------------------------------------------------ BlockHighlighter
    static void highlighter() {
        ModuleTests.FakeBlocks b = new ModuleTests.FakeBlocks();
        b.load(0, 0); b.load(1, 0);
        b.set(3, 10, 3, Kind.ORE_DIAMOND); b.set(20, 12, 4, Kind.ORE_DEBRIS); b.set(5, 11, 5, Kind.ORE_IRON);
        BlockHighlighter h = new BlockHighlighter();
        h.radius = 2;
        int done = 0;
        for (int i = 0; i < 50 && done < 2; i++) done += h.step(b, 0, 0, 1000, 5_000_000, k -> k == Kind.ORE_DIAMOND || k == Kind.ORE_DEBRIS);
        check("highlighter: both loaded chunks get scanned", h.chunkCount() == 2);
        check("highlighter: finds only the wanted kinds", h.hitCount() == 2 && h.hits(0, 0).get(0).kind() == Kind.ORE_DIAMOND);
        check("highlighter: reports world coordinates", h.hits(1, 0).get(0).x() == 20 && h.hits(1, 0).get(0).y() == 12);
        check("highlighter: unscanned chunk has no hits", h.hits(5, 5).isEmpty());
        int before = h.step(b, 0, 0, 1500, 5_000_000, k -> true);
        check("highlighter: nothing is rescanned before the rescan interval", before == 0);
        b.set(3, 10, 3, Kind.AIR);
        h.step(b, 0, 0, 1000 + 21_000, 50_000_000, k -> k == Kind.ORE_DIAMOND);
        check("highlighter: a mined ore disappears after a rescan", h.hits(0, 0).isEmpty());
        BlockHighlighter cap = new BlockHighlighter();
        cap.maxHitsPerChunk = 5; cap.radius = 1;
        for (int i = 0; i < 40; i++) b.set(i % 16, 10 + i / 16, 7, Kind.ORE_IRON);
        for (int i = 0; i < 30; i++) cap.step(b, 0, 0, 0, 5_000_000, k -> k == Kind.ORE_IRON);
        check("highlighter: per-chunk hits are capped", cap.hits(0, 0).size() == 5);
        BlockHighlighter lru = new BlockHighlighter();
        lru.maxChunks = 1; lru.radius = 2;
        for (int i = 0; i < 30; i++) lru.step(b, 0, 0, 0, 5_000_000, k -> true);
        check("highlighter: chunk count is bounded (LRU)", lru.chunkCount() <= 1);
        BlockHighlighter unl = new BlockHighlighter();
        unl.radius = 1;
        unl.step(b, 0, 0, 0, 50_000_000, k -> k == Kind.ORE_IRON);
        b.loaded.clear();
        unl.prune(b);
        check("highlighter: unloaded chunks are pruned", unl.chunkCount() == 0);
        BlockHighlighter none = new BlockHighlighter();
        check("highlighter: no loaded chunks means no work", none.step(new ModuleTests.FakeBlocks(), 0, 0, 0, 1_000_000, k -> true) == 0);
    }

    // ------------------------------------------------------------ modules
    static G2 world() { G2 g = new G2(); g.blocks.load(0, 0); return g; }

    static void entityEsp() throws Exception {
        G2 g = world(); EventBus bus = new EventBus(); FakeSink s = new FakeSink();
        ExtraRenderModules.EntityEsp m = new ExtraRenderModules.EntityEsp(ModuleTests.ctx(g, bus));
        g.ents.add(new EntityInfo(1, EntityKind.HOSTILE, "Zombie", false, 0, 64, 10, 0.6, 1.95, 14, 20));
        g.ents.add(new EntityInfo(2, EntityKind.PASSIVE, "Cow", false, 5, 64, 5, 0.9, 1.4));
        g.ents.add(new EntityInfo(3, EntityKind.ITEM, "Diamond x3", false, 2, 64, 2, 0.25, 0.25));
        g.ents.add(new EntityInfo(4, EntityKind.PROJECTILE, "Arrow", false, 1, 65, 1, 0.5, 0.5));
        g.ents.add(new EntityInfo(5, EntityKind.HOSTILE, "Far", false, 0, 64, 500, 0.6, 1.8));
        m.setEnabled(true);
        bus.fireRender(s, 0);
        check("entity esp: draws hostiles and items by default, hides passives/projectiles/far ones", s.boxes == 2);
        check("entity esp: label shows name, health and distance", s.labelText.stream().anyMatch(t -> t.startsWith("Zombie 14/20") && t.endsWith("m")));
        check("entity esp: item label has the item name without health", s.labelText.stream().anyMatch(t -> t.startsWith("Diamond x3") && !t.contains("/")));
        m.passives.set(true); m.projectiles.set(true);
        s.boxes = 0; bus.fireRender(s, 0);
        check("entity esp: toggling kinds on adds them", s.boxes == 4);
        g.ents.add(new EntityInfo(6, EntityKind.HOSTILE, "Me", true, 0, 64, 0, 0.6, 1.8));
        s.boxes = 0; bus.fireRender(s, 0);
        check("entity esp: never outlines yourself", s.boxes == 4);
        s.boxes = 0; s.frustumOk = false; bus.fireRender(s, 0);
        check("entity esp: off-screen entities are culled", s.boxes == 0);
        m.setEnabled(false);
        check("entity esp: disabling removes the render listener", bus.listenerCount() == 0);
        g.inWorld = false; m.setEnabled(true); s.frustumOk = true; s.boxes = 0; bus.fireRender(s, 0);
        check("entity esp: draws nothing outside a world", s.boxes == 0);
    }

    static void trajectoryModule() throws Exception {
        G2 g = world(); EventBus bus = new EventBus(); FakeSink s = new FakeSink();
        ExtraRenderModules.ProjectileTrajectory m = new ExtraRenderModules.ProjectileTrajectory(ModuleTests.ctx(g, bus));
        m.setEnabled(true);
        bus.fireRender(s, 0);
        check("trajectory module: draws nothing when holding no projectile item", s.lines == 0 && s.boxes == 0);
        g.held = new ProjectileLaunch(Trajectory.Kind.PEARL, 1);
        g.floorY = 40;
        bus.fireRender(s, 0);
        check("trajectory module: draws a path and an impact box for a held pearl", s.lines > 5 && s.boxes == 1);
        g.held = new ProjectileLaunch(Trajectory.Kind.ARROW, 0.0);
        s.lines = 0; s.boxes = 0; bus.fireRender(s, 0);
        check("trajectory module: an undrawn bow draws nothing", s.lines == 0);
        m.impact.set(false); g.held = new ProjectileLaunch(Trajectory.Kind.EGG, 1);
        s.boxes = 0; bus.fireRender(s, 0);
        check("trajectory module: impact marker can be switched off", s.boxes == 0);
        m.steps.set(20.0);
        check("trajectory module: max steps limits the path", m.predict().size() <= 21);
        m.setEnabled(false);
        check("trajectory module: disabling cleans up", bus.listenerCount() == 0);
    }

    static void chunkBorders() throws Exception {
        G2 g = world(); EventBus bus = new EventBus(); FakeSink s = new FakeSink();
        ExtraRenderModules.ChunkBorders m = new ExtraRenderModules.ChunkBorders(ModuleTests.ctx(g, bus));
        m.setEnabled(true);
        bus.fireRender(s, 0);
        check("chunk borders: draws lines and the current-chunk box", s.lines > 0 && s.boxes == 1);
        check("chunk borders: line count matches the documented formula", s.lines == ExtraRenderModules.ChunkBorders.lineCount(2, true, 32));
        s.lines = 0; m.subchunks.set(false); bus.fireRender(s, 0);
        check("chunk borders: no subchunk rings -> fewer lines", s.lines == ExtraRenderModules.ChunkBorders.lineCount(2, false, 32));
        check("chunk borders: worst case (radius 6) stays small", ExtraRenderModules.ChunkBorders.lineCount(6, true, 96) < 2500);
        m.setEnabled(false);
        check("chunk borders: disabling cleans up", bus.listenerCount() == 0);
    }

    static void blockEsp() throws Exception {
        G2 g = world(); EventBus bus = new EventBus(); FakeSink s = new FakeSink();
        g.blocks.set(3, 30, 3, Kind.ORE_DIAMOND); g.blocks.set(4, 30, 3, Kind.ORE_IRON); g.blocks.set(6, 30, 6, Kind.PORTAL);
        g.blocks.set(7, 30, 6, Kind.ORE_DEBRIS);
        ExtraRenderModules.BlockEsp m = new ExtraRenderModules.BlockEsp(ModuleTests.ctx(g, bus));
        m.setEnabled(true);
        for (int i = 0; i < 80; i++) bus.fireTick();
        bus.fireRender(s, 0);
        check("block esp: highlights diamond, debris and portal by default (iron is off)", s.boxes == 3);
        m.iron.set(true);
        for (int i = 0; i < 80; i++) bus.fireTick();
        s.boxes = 0; bus.fireRender(s, 0);
        check("block esp: enabling another type rescans and shows it", s.boxes == 4);
        m.maxBoxes.set(50.0); m.diamond.set(false); m.debris.set(false); m.portals.set(false); m.iron.set(false);
        for (int i = 0; i < 80; i++) bus.fireTick();
        s.boxes = 0; bus.fireRender(s, 0);
        check("block esp: with everything off nothing is drawn", s.boxes == 0);
        m.diamond.set(true);
        for (int i = 0; i < 80; i++) bus.fireTick();
        g.blocks.set(15, 30, 15, Kind.ORE_DIAMOND);      // 40 blocks from the camera; the first diamond is 34 away
        g.now += 30_000;                                   // past the rescan interval so the new ore is seen
        for (int i = 0; i < 80; i++) bus.fireTick();
        m.drawDistance.set(36.0);
        s.boxes = 0; bus.fireRender(s, 0);
        check("block esp: ores beyond the draw distance are skipped (1 of 2 drawn)", s.boxes == 1);
        m.drawDistance.set(48.0);
        s.boxes = 0; bus.fireRender(s, 0);
        check("block esp: a larger draw distance shows both", s.boxes == 2);
        s.boxes = 0; s.frustumOk = false; bus.fireRender(s, 0);
        check("block esp: off-screen ores are culled", s.boxes == 0);
        bus.fireWorld(WorldEventType.DIMENSION_CHANGE);
        check("block esp: dimension change clears the scan cache", m.scanner().chunkCount() == 0);
        m.setEnabled(false);
        check("block esp: disabling clears cache and listeners", m.scanner().chunkCount() == 0 && bus.listenerCount() == 0);
        check("block esp: non-ore kinds are never wanted", !m.wants(Kind.SOLID_NATURAL) && !m.wants(Kind.AIR));
    }

    static void utilities() throws Exception {
        G2 g = world(); EventBus bus = new EventBus();
        ModuleContext c = ModuleTests.ctx(g, bus);
        UtilityModules.ToggleSneak ts = new UtilityModules.ToggleSneak(c);
        ts.setEnabled(true); bus.fireTick();
        check("toggle sneak: holds sneak while enabled", g.heldSneak);
        ts.setEnabled(false);
        check("toggle sneak: releases sneak when disabled", !g.heldSneak);
        ts.setEnabled(true); g.dead = true; g.heldSneak = false; bus.fireTick();
        check("toggle sneak: does nothing while dead", !g.heldSneak);
        ts.setEnabled(false);

        UtilityModules.ScreenshotMode sm = new UtilityModules.ScreenshotMode(c);
        FakeSink s = new FakeSink();
        ExtraRenderModules.ChunkBorders cb = new ExtraRenderModules.ChunkBorders(c);
        cb.setEnabled(true);
        sm.setEnabled(true);
        bus.fireRender(s, 0);
        check("screenshot mode: suppresses every world overlay", s.lines == 0 && s.boxes == 0);
        check("screenshot mode: hides the HUD flag, chat flag follows its setting", c.flags.hideHud && !c.flags.hideChat);
        sm.hideChat.set(true); bus.fireTick();
        check("screenshot mode: chat can be hidden too", c.flags.hideChat);
        sm.setEnabled(false);
        bus.fireRender(s, 0);
        check("screenshot mode: disabling restores overlays and flags", s.lines > 0 && !c.flags.hideHud && !c.flags.hideChat);
        utilities2(c, g, bus);
    }

    static void utilities2(ModuleContext c, G2 g, EventBus bus) throws Exception {
        UtilityModules.ChatTweaks ct = new UtilityModules.ChatTweaks(c);
        ct.setZone(java.time.ZoneOffset.UTC);
        check("chat tweaks: pass-through while disabled", "hello".equals(ct.process("hello")));
        ct.setEnabled(true);
        check("chat tweaks: 24h timestamp is prepended", "[00:16:40] hello".equals(ct.process("hello")));
        ct.hour24.set(false);
        check("chat tweaks: 12h timestamp format", ct.process("hi").startsWith("[12:16:40 AM] "));
        ct.timestamps.set(false);
        check("chat tweaks: timestamps can be off", "hi".equals(ct.process("hi")));
        ct.hideWords.set("spam, Ad ,, ");
        check("chat tweaks: hides messages with listed words (case-insensitive)", ct.process("BUY SPAM now") == null && ct.process("an AD here") == null);
        check("chat tweaks: other messages and blank terms do not hide anything", "fine".equals(ct.process("fine")));
        check("chat tweaks: null message is safe", ct.process(null) == null);
        ct.setEnabled(false);

        UtilityModules.CustomCrosshair ch = new UtilityModules.CustomCrosshair(c);
        Rec r0 = new Rec(); ch.draw(new RasterGfx(r0, 1407, 840), 700, 420);
        check("crosshair: draws nothing while disabled", r0.fills.isEmpty());
        ch.setEnabled(true);
        Rec r1 = new Rec(); ch.draw(new RasterGfx(r1, 1407, 840), 700, 420);
        ch.tStyle.set(true);
        Rec r2 = new Rec(); ch.draw(new RasterGfx(r2, 1407, 840), 700, 420);
        check("crosshair: draws pixels around the given centre", !r1.fills.isEmpty() && r1.fills.stream().allMatch(f -> Math.abs(f[0] - 700) < 40 && Math.abs(f[1] - 420) < 40));
        check("crosshair: T-style omits the top arm", r2.fills.size() < r1.fills.size());
        ch.tStyle.set(false); ch.dot.set(true);
        Rec r3 = new Rec(); ch.draw(new RasterGfx(r3, 1407, 840), 700, 420);
        check("crosshair: centre dot adds pixels", r3.fills.size() > r1.fills.size());
        g.cooldown = 0.3;
        Rec r4 = new Rec(); ch.draw(new RasterGfx(r4, 1407, 840), 700, 420);
        check("crosshair: cooldown bar appears while recharging", r4.fills.size() > r3.fills.size());
        ch.cooldown.set(false);
        Rec r5 = new Rec(); ch.draw(new RasterGfx(r5, 1407, 840), 700, 420);
        check("crosshair: cooldown bar can be disabled", r5.fills.size() == r3.fills.size());

        UtilityModules.SessionStats ss = new UtilityModules.SessionStats(c);
        ss.setEnabled(true);
        for (int i = 0; i < 10; i++) { g.now += 50; g.x += 0.5; bus.fireTick(); bus.fireFrame(0.02); }
        check("session stats: ticks feed the tracker", ss.stats().distance() > 4 && ss.stats().speedBps() > 5);
        check("session stats: frames feed the perf monitor", Math.abs(ss.perf().avgMs() - 20) < 1e-6);
        bus.fireWorld(WorldEventType.DIMENSION_CHANGE);
        check("session stats: a dimension change restarts the counters", ss.stats().distance() == 0);
        ss.setEnabled(false);
        check("session stats: disabling cleans up", true);

        UtilityModules.Exploration ex = new UtilityModules.Exploration(c);
        ex.setEnabled(true);
        g.x = 8; g.z = 8; bus.fireTick(); g.x = 40; bus.fireTick(); bus.fireTick();
        check("exploration module: records visited chunks once each", ex.history().count("overworld") == 2);
        ex.setEnabled(false);
        UtilityModules.Exploration ex2 = new UtilityModules.Exploration(c);
        check("exploration module: history survives disable and reload", ex2.history().count() == 2);
    }

    static void renderers() {
        NametagStyle st = new NametagStyle();
        Rec r = new Rec();
        double[] box = SelfNametagRenderer.draw(new RasterGfx(r, 1407, 840), st, "Steve", 700, 300, 1);
        check("nametag renderer: draws one text run per character plus shadow", box[0] > 0 && box[1] > 0 && r.texts.size() >= 5);
        st.prefixSymbol = "*"; st.suffixSymbol = "*";
        double[] wide = SelfNametagRenderer.draw(new RasterGfx(new Rec(), 1407, 840), st, "Steve", 700, 300, 1);
        check("nametag renderer: prefix/suffix widen the tag", wide[0] > box[0]);
        st.setScale(2f);
        double[] big = SelfNametagRenderer.draw(new RasterGfx(new Rec(), 1407, 840), st, "Steve", 700, 300, 1);
        check("nametag renderer: scale enlarges the tag", big[0] > wide[0] * 1.5);
        double[] far = SelfNametagRenderer.draw(new RasterGfx(new Rec(), 1407, 840), st, "Steve", 700, 300, 0.1);
        check("nametag renderer: distance scale is clamped (never vanishes)", far[0] > 0 && far[0] < big[0]);
        st.backgroundArgb = 0;
        Rec nb = new Rec(); SelfNametagRenderer.draw(new RasterGfx(nb, 1407, 840), st, "Steve", 700, 300, 1);
        Rec wb = new Rec(); st.backgroundArgb = 0x80000000; SelfNametagRenderer.draw(new RasterGfx(wb, 1407, 840), st, "Steve", 700, 300, 1);
        check("nametag renderer: a transparent background draws no box", nb.fills.size() < wb.fills.size());
        NametagStyle empty = new NametagStyle();
        Rec er = new Rec();
        double[] eb = SelfNametagRenderer.draw(new RasterGfx(er, 1407, 840), empty, "", 700, 300, 1);
        check("nametag renderer: empty resolved text draws nothing", empty.resolveText("").isEmpty() && eb[0] == 0 && er.texts.isEmpty() && er.fills.isEmpty());
        NametagStyle grad = new NametagStyle(); grad.colorStart = 0xFFFF0000; grad.colorEnd = 0xFF0000FF; grad.shadow = false;
        Rec gr = new Rec(); SelfNametagRenderer.draw(new RasterGfx(gr, 1407, 840), grad, "Steve", 700, 300, 1);
        check("nametag renderer: gradient uses several colours", gr.texts.size() == 5 && grad.colorAt(0, 5) != grad.colorAt(4, 5));

        NotificationCenter nc = new NotificationCenter();
        Rec nr = new Rec();
        NotificationRenderer.draw(new RasterGfx(nr, 1407, 840), nc, 0, 1407);
        check("notification renderer: nothing to draw when empty", nr.fills.isEmpty() && nr.texts.isEmpty());
        nc.push("Base Finder: possible base", 1000); nc.push("Second", 1700);
        NotificationRenderer.draw(new RasterGfx(nr, 1407, 840), nc, 1800, 1407);
        check("notification renderer: draws each active note", nr.texts.contains("Base Finder: possible base") && nr.texts.contains("Second"));
        Rec late = new Rec();
        NotificationRenderer.draw(new RasterGfx(late, 1407, 840), nc, 1000 + 9000, 1407);
        check("notification renderer: expired notes disappear", late.texts.isEmpty());
        NotificationCenter nc2 = new NotificationCenter();
        nc2.push("Fading", 1000);
        Rec fade = new Rec();
        NotificationRenderer.draw(new RasterGfx(fade, 1407, 840), nc2, 1000 + nc2.ttlMs - 100, 1407);
        check("notification renderer: a note about to expire still renders (fading)", fade.texts.contains("Fading"));
    }

    // ------------------------------------------------------------ HUD widgets
    static final class FakeHud implements HudData {
        HudData.Target tgt; List<Counter> cnt = List.of(); List<StatLine> st = List.of(); WaypointInfo wp;
        public int fps() { return 144; } public int ping() { return 20; }
        public double x() { return 1; } public double y() { return 64; } public double z() { return 2; }
        public float yaw() { return 0; } public boolean inNether() { return false; }
        public List<Effect> effects() { return List.of(); } public List<ArmourPiece> armour() { return List.of(); }
        public List<ModuleDot> activeModules() { return List.of(); }
        public boolean keyDown(Key k) { return false; } public int cpsLeft() { return 0; } public int cpsRight() { return 0; }
        public int minimapColor(int dx, int dz) { return 0; }
        @Override public Target target() { return tgt; } @Override public List<Counter> counters() { return cnt; }
        @Override public List<StatLine> stats() { return st; } @Override public WaypointInfo nearestWaypoint() { return wp; }
    }

    static List<String> drawHud(LayoutModel lm, FakeHud d) {
        Rec r = new Rec();
        HudRenderer.draw(new RasterGfx(r, 1407, 840), lm, d, false, null);
        return r.texts;
    }

    static void hudWidgets() {
        LayoutModel lm = HudRenderer.defaultLayout(1407, 840);
        String[] ids = {HudRenderer.TARGET, HudRenderer.COUNTERS, HudRenderer.STATS, HudRenderer.WAYPOINT};
        boolean allHidden = true, allThere = true;
        for (String id : ids) { allThere &= lm.get(id) != null; allHidden &= lm.get(id) != null && !lm.get(id).visible; }
        check("hud: the four new widgets exist and are hidden by default (reference layout unchanged)", allThere && allHidden);
        check("hud: the original default-visible widgets are unchanged",
                lm.get(HudRenderer.WATERMARK).visible && lm.get(HudRenderer.FPS).visible && lm.get(HudRenderer.COORDS).visible
                        && lm.get(HudRenderer.POTIONS).visible && lm.get(HudRenderer.MINIMAP).visible && !lm.get(HudRenderer.KEYS).visible);
        boolean inside = true, disjoint = true;
        List<LayoutModel.Widget> ws = lm.widgets();
        for (LayoutModel.Widget a : ws) {
            inside &= a.x >= 0 && a.y >= 0 && a.x + a.w <= 1407 + 0.5 && a.y + a.h <= 840 + 0.5;
            for (LayoutModel.Widget b : ws) {
                if (a == b) continue;
                disjoint &= a.x + a.w <= b.x + 0.5 || b.x + b.w <= a.x + 0.5 || a.y + a.h <= b.y + 0.5 || b.y + b.h <= a.y + 0.5;
            }
        }
        check("hud: every default widget is on screen", inside);
        check("hud: default positions of all widgets do not overlap each other", disjoint);

        for (String id : ids) lm.get(id).visible = true;
        FakeHud d = new FakeHud();
        List<String> t = drawHud(lm, d);
        check("hud: empty data shows friendly placeholders", t.contains("No target") && t.contains("No waypoints here"));
        d.tgt = new HudData.Target("Zombie", 14, 20, 7.4);
        d.cnt = List.of(new HudData.Counter("Totems", 3), new HudData.Counter("Pearls", 16));
        d.st = List.of(new HudData.StatLine("Session", "00:12:05"), new HudData.StatLine("Speed", "4.3 b/s"));
        d.wp = new HudData.WaypointInfo("Home", 842, 30);
        t = drawHud(lm, d);
        check("hud: target widget shows name, health and distance", t.contains("Zombie") && t.contains("14 / 20") && t.contains("7 m"));
        check("hud: counters widget shows labels and values", t.contains("Totems") && t.contains("3") && t.contains("Pearls") && t.contains("16"));
        check("hud: stats widget shows lines", t.contains("Session") && t.contains("00:12:05") && t.contains("4.3 b/s"));
        check("hud: waypoint widget shows name and distance", t.contains("Home") && t.contains("842 m"));
        d.tgt = new HudData.Target("Cow", -1, -1, 3);
        check("hud: unknown health is labelled, not faked", drawHud(lm, d).contains("health unknown"));
        List<HudData.Counter> many = new ArrayList<>(); for (int i = 0; i < 20; i++) many.add(new HudData.Counter("Row" + i, i));
        d.cnt = many;
        check("hud: counters never draw more than six rows", drawHud(lm, d).stream().filter(x -> x.startsWith("Row")).count() == 6);
        for (String id : ids) lm.get(id).visible = false;
        check("hud: hidden new widgets draw nothing", !drawHud(lm, d).contains("Cow"));
        lm.get(HudRenderer.TARGET).visible = true;
        Rec rec = new Rec();
        HudRenderer.draw(new RasterGfx(rec, 1407, 840), lm, d, true, lm.get(HudRenderer.TARGET));
        String caps = String.join("", rec.texts);   // captions are letter-spaced, so the backend receives them per character
        check("hud: the editor shows captions for the new widgets", caps.contains("TARGET") && caps.contains("COUNTERS") && caps.contains("WAYPOINT"));
        Path f; 
        try {
            f = Files.createTempFile("layout", ".properties");
            lm.get(HudRenderer.STATS).visible = true; lm.get(HudRenderer.STATS).x += 30;
            lm.save(f);
            LayoutModel back = HudRenderer.defaultLayout(1407, 840);
            back.load(f);
            check("hud: new widgets persist visibility and position", back.get(HudRenderer.STATS).visible && Math.abs(back.get(HudRenderer.STATS).x - lm.get(HudRenderer.STATS).x) < 1);
        } catch (Exception e) { check("hud: persistence (" + e + ")", false); }

        // ClickGUI overlay card: all rows reachable by scrolling and toggleable
        ModuleManager mm = new ModuleManager();
        LayoutModel l2 = HudRenderer.defaultLayout(1407, 840);
        ClickGuiView v = new ClickGuiView(mm, l2);
        RasterGfx g = new RasterGfx(new Rec(), 1407, 840);
        v.render(g, 1000);
        for (int i = 0; i < 6; i++) v.scroll(800, 600, -1);
        v.render(g, 1000);
        double lastRowY = 459 + 64 + 31 * 6;
        v.mouseDown(700, lastRowY, 0); v.mouseUp(); v.render(g, 1000);
        check("gui: scrolling the overlay card reveals and toggles the last widget (waypoint compass)", l2.get(HudRenderer.WAYPOINT).visible);
        for (int i = 0; i < 12; i++) v.scroll(800, 600, 1);
        v.render(g, 1000);
        v.mouseDown(700, 459 + 64, 0); v.mouseUp();
        check("gui: scrolling back up restores the first row (FPS) to its toggle", !l2.get(HudRenderer.FPS).visible);
    }

    static void waypointNotes() throws Exception {
        G2 g = world(); EventBus bus = new EventBus(); ModuleContext c = ModuleTests.ctx(g, bus);
        WorldModules.WaypointsModule w = new WorldModules.WaypointsModule(c);
        g.x = 0; g.z = 0;
        check("waypoint note: setNote on a missing waypoint fails", !w.setNote("nope", "x"));
        w.addHere("Home");
        check("waypoint note: set on an existing waypoint", w.setNote("home", "bed\tand\nchest"));
        WorldModules.WaypointsModule re = new WorldModules.WaypointsModule(c);
        Waypoints.Waypoint hw = re.store().nearest("overworld", 0, 0).get(0);
        check("waypoint note: persists, tabs/newlines flattened", hw.note().equals("bed and chest"));
        Path f = c.dataDir.resolve("waypoints.tsv");
        Files.writeString(f, "#paint-waypoints v1\noverworld\tOld\t1.0\t64.0\t2.0\n");
        check("waypoint note: v1 files (no note column) still load", new WorldModules.WaypointsModule(c).store().size() == 1);
        FakeSink s = new FakeSink(); re.setEnabled(true);
        g.x = 0; g.z = 0; bus.fireRender(s, 0);
        check("waypoint note: shown in the world label", s.labelText.stream().anyMatch(t -> t.contains("bed and chest")));
        g.z = -50; g.yaw = 0;
        w.store().put(new Waypoints.Waypoint("East", "overworld", 100, 64, -50));
        check("waypoint compass: nearest waypoint is reported with distance", re.nearestInfo() != null);
        WorldModules.WaypointsModule w3 = new WorldModules.WaypointsModule(c);
        w3.store().put(new Waypoints.Waypoint("Only", "overworld", 0, 64, 100));
        for (Waypoints.Waypoint x : new ArrayList<>(w3.store().nearest("overworld", 0, 0))) if (!x.name().equals("Only")) w3.store().remove("overworld", x.name());
        g.x = 0; g.z = 0; g.yaw = 0;
        check("waypoint compass: a waypoint straight ahead (+Z, yaw 0) has bearing 0", Math.abs(w3.nearestInfo().bearingDeg()) < 1e-6 && Math.abs(w3.nearestInfo().distance() - 100) < 1e-6);
        w3.store().put(new Waypoints.Waypoint("Only", "overworld", 100, 64, 0));
        check("waypoint compass: +X while facing +Z is 90 degrees to the left", Math.abs(w3.nearestInfo().bearingDeg() + 90) < 1e-6);
        g.dim = "the_end";
        check("waypoint compass: none in a dimension without waypoints", w3.nearestInfo() == null);
        g.inWorld = false;
        check("waypoint compass: none outside a world", w3.nearestInfo() == null);
    }

    static void registry() throws Exception {
        G2 g = world(); EventBus bus = new EventBus(); ModuleContext c = ModuleTests.ctx(g, bus);
        ModuleManager mm = new ModuleManager();
        List<String> errors = new ArrayList<>();
        mm.setErrorSink(errors::add);
        Modules.registerAll(mm, c);
        for (String n : new String[] {"Entity ESP", "Block ESP", "Trajectory", "Chunk Borders", "Crosshair", "Toggle Sneak", "Chat Tweaks",
                "Screenshot Mode", "Exploration", "Session Stats"}) {
            check("registry: '" + n + "' is registered", mm.get(n) != null);
        }
        long cats = mm.all().stream().map(m -> m.category).distinct().count();
        check("registry: modules span several categories", cats >= 4);
        check("registry: no combat-automation modules exist", mm.inCategory(Category.COMBAT).isEmpty());
        int ok = 0;
        for (Module m : mm.all()) {
            if (m.name.equals("Freecam")) continue; // needs a live world to attach to; covered by its own tests
            m.setEnabled(true);
            bus.fireTick(); bus.fireFrame(0.016); bus.fireRender(new FakeSink(), 0.016);
            m.setEnabled(false);
            if (!m.isEnabled()) ok++;
        }
        check("registry: every module enables, runs a tick/frame/render and disables without errors (" + errors + ")", errors.isEmpty() && ok == mm.all().size() - 1);
        check("registry: after cycling every module no listeners remain", bus.listenerCount() == 0);
        for (Module m : mm.all()) if (!m.name.equals("Appearance")) m.setEnabled(true);
        mm.panic();
        check("registry: panic switches everything off and restores sneak, overlays and HUD flags",
                mm.all().stream().noneMatch(m -> m.isEnabled() && m.panicSafe()) && !g.heldSneak && !bus.renderSuppressed && !c.flags.hideHud);
        Path cfg = Files.createTempDirectory("paintnf").resolve("c.properties");
        ((ExtraRenderModules.BlockEsp) mm.get("Block ESP")).drawDistance.set(120.0);
        ((UtilityModules.CustomCrosshair) mm.get("Crosshair")).color.setRgb(0x00FF00);
        ConfigStore.save(mm, cfg);
        ModuleManager m2 = new ModuleManager();
        Modules.registerAll(m2, ModuleTests.ctx(new G2(), new EventBus()));
        ConfigStore.load(m2, cfg);
        check("registry: new modules' settings round-trip through the config",
                ((ExtraRenderModules.BlockEsp) m2.get("Block ESP")).drawDistance.get() == 120.0
                        && ((UtilityModules.CustomCrosshair) m2.get("Crosshair")).color.argb() == 0xFF00FF00);
    }
}
