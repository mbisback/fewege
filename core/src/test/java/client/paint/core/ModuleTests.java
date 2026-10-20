package client.paint.core;

import client.paint.core.basefinder.*;
import client.paint.core.basefinder.BlockSource.Kind;
import client.paint.core.game.*;
import client.paint.core.game.EventBus.WorldEventType;
import client.paint.core.game.WorldView.*;
import client.paint.core.modules.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Module, scanner and registry tests using a fake game, fake world and fake render sink. */
public class ModuleTests {
    static int passed, failed;
    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS " + name); } else { failed++; System.out.println("FAIL " + name); }
    }

    // ---------------------------------------------------------------- fakes
    static final class FakeBlocks implements BlockSource {
        final Map<String, Kind> blocks = new HashMap<>();
        final Set<Long> loaded = new HashSet<>();
        int unloadAfterReads = -1, reads;
        static long k(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
        void load(int cx, int cz) { loaded.add(k(cx, cz)); }
        void set(int x, int y, int z, Kind kind) { blocks.put(x + "," + y + "," + z, kind); }
        public boolean isChunkLoaded(int cx, int cz) { return loaded.contains(k(cx, cz)); }
        public int minY() { return 0; }
        public int maxY() { return 64; }
        public Kind kind(int x, int y, int z) {
            if (unloadAfterReads >= 0 && ++reads > unloadAfterReads) loaded.clear();
            Kind o = blocks.get(x + "," + y + "," + z);
            return o != null ? o : (y < 40 ? Kind.SOLID_NATURAL : Kind.AIR);
        }
    }

    static class FakeGame implements GameAccess, WorldView {
        boolean inWorld = true, dead;
        String dim = "overworld";
        double x, y = 64, z; float yaw, pitch;
        float fwd, strafe; boolean jump, sneak, sprintOk = true;
        boolean suppressed, sprinting; double fov = 1, gamma = -1;
        double[] camOverride; float[] look = {0, 0};
        long now = 1_000_000;
        final List<String> notes = new ArrayList<>();
        final List<EntityInfo> ents = new ArrayList<>();
        final List<StorageInfo> stor = new ArrayList<>();
        FakeBlocks blocks = new FakeBlocks();
        boolean los = true;

        public boolean inWorld() { return inWorld; } public boolean playerDead() { return dead; }
        public String dimension() { return dim; } public String playerName() { return "Steve"; }
        public double eyeX() { return x; } public double eyeY() { return y + 1.62; } public double eyeZ() { return z; }
        public double posX() { return x; } public double posY() { return y; } public double posZ() { return z; }
        public float yaw() { return yaw; } public float pitch() { return pitch; }
        public float moveForward() { return fwd; } public float moveStrafe() { return strafe; }
        public boolean jumpHeld() { return jump; } public boolean sneakHeld() { return sneak; }
        public float[] consumeLookDelta() { float[] r = look; look = new float[] {0, 0}; return r; }
        public void suppressPlayerInput(boolean on) { suppressed = on; }
        public boolean sprintAllowed() { return sprintOk; } public void setSprinting(boolean on) { sprinting = on; }
        public void setCameraOverride(double a, double b, double c, float yw, float pt) { camOverride = new double[] {a, b, c, yw, pt}; }
        public void clearCameraOverride() { camOverride = null; }
        public void setFovMultiplier(double m) { fov = m; }
        public void setGammaOverride(double g) { gamma = g; } public void clearGammaOverride() { gamma = -1; }
        public WorldView world() { return this; } public long nowMs() { return now; }
        public void notifyPlayer(String m) { notes.add(m); }
        public List<EntityInfo> entities() { return ents; } public List<StorageInfo> storage() { return stor; }
        public BlockSource blocks() { return blocks; } public boolean lineOfSight(double a, double b, double c) { return los; }
    }

    static final class FakeSink implements RenderSink {
        int boxes, lines, tracers, labels; boolean frustumOk = true;
        final List<String> labelText = new ArrayList<>();
        public double camX() { return 0; } public double camY() { return 64; } public double camZ() { return 0; }
        public boolean inFrustum(double a, double b, double c, double d, double e, double f) { return frustumOk; }
        public void box(double a, double b, double c, double d, double e, double f, int o, int fl, float t, boolean tw) { boxes++; }
        public void line(double a, double b, double c, double d, double e, double f, int col, float t) { lines++; }
        public void lineFromCrosshair(double a, double b, double c, int col, float t) { tracers++; }
        public void label(String s, double a, double b, double c, int col, float sc) { labels++; labelText.add(s); }
    }

    static ModuleContext ctx(FakeGame g, EventBus bus) throws Exception {
        return new ModuleContext(g, bus, Files.createTempDirectory("paintmod"));
    }
    static EntityInfo player(int id, String n, double x, double z, boolean self) {
        return new EntityInfo(id, EntityKind.PLAYER, n, self, x, 64, z, 0.6, 1.8);
    }

    public static int[] runAll() throws Exception {
        passed = 0; failed = 0;
        scanner(); scheduler(); movement(); esp(); nametag(); baseFinder(); waypoints(); registry();
        return new int[] {passed, failed};
    }
    public static void main(String[] a) throws Exception {
        int[] r = runAll();
        System.out.println("\n" + r[0] + " passed, " + r[1] + " failed");
        if (r[1] > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- scanner
    static ChunkEvidence scan(FakeBlocks b, int cx, int cz) {
        ChunkScanner s = new ChunkScanner(b, cx, cz);
        while (!s.step(64)) { }
        return s.result();
    }

    static void scanner() {
        FakeBlocks b = new FakeBlocks();
        b.load(0, 0);
        ChunkEvidence empty = scan(b, 0, 0);
        check("scanner: untouched natural terrain has no evidence",
                empty.count(EvidenceType.PLAYER_BLOCKS) == 0 && empty.count(EvidenceType.EXCAVATION) == 0
                        && empty.count(EvidenceType.TALL_KELP) == 0 && empty.count(EvidenceType.AMETHYST_LARGE) == 0);
        check("scanner: lone chunk reports low completeness (1/9)", Math.abs(empty.completeness() - 1.0 / 9) < 1e-9);

        for (int y = 41; y <= 52; y++) b.set(5, y, 5, Kind.KELP);
        for (int y = 41; y <= 44; y++) b.set(9, y, 9, Kind.KELP); // too short
        for (int i = 0; i < 5; i++) b.set(i, 45, 12, Kind.AMETHYST);
        for (int y = 41; y <= 50; y++) b.set(2, y, 2, Kind.VINE);
        ChunkEvidence nat = scan(b, 0, 0);
        check("scanner: counts only tall kelp columns (12 yes, 4 no)", nat.count(EvidenceType.TALL_KELP) == 1);
        check("scanner: counts amethyst blocks", nat.count(EvidenceType.AMETHYST_LARGE) == 5);
        check("scanner: counts long vine columns", nat.count(EvidenceType.LONG_VINES) == 1);

        for (int x = 4; x < 8; x++) for (int z = 4; z < 8; z++) b.set(x, 41, z, Kind.PLAYER_PLACED);
        ChunkEvidence built = scan(b, 0, 0);
        check("scanner: aligned 4x4 player floor is one construction patch", built.count(EvidenceType.CONSTRUCTION_PATTERN) == 1);
        check("scanner: player blocks counted", built.count(EvidenceType.PLAYER_BLOCKS) == 16);
        FakeBlocks scatter = new FakeBlocks(); scatter.load(0, 0);
        for (int i = 0; i < 16; i++) scatter.set(i, 41, (i * 5) % 16, Kind.PLAYER_PLACED);
        check("scanner: scattered player blocks are not a construction patch", scan(scatter, 0, 0).count(EvidenceType.CONSTRUCTION_PATTERN) == 0);

        FakeBlocks tun = new FakeBlocks(); tun.load(0, 0);
        for (int x = 2; x <= 11; x++) { tun.set(x, 20, 8, Kind.AIR); tun.set(x, 21, 8, Kind.AIR); }
        check("scanner: 10-cell straight 1x2 corridor counted as excavation", scan(tun, 0, 0).count(EvidenceType.EXCAVATION) == 10);
        FakeBlocks shortTun = new FakeBlocks(); shortTun.load(0, 0);
        for (int x = 2; x <= 5; x++) { shortTun.set(x, 20, 8, Kind.AIR); shortTun.set(x, 21, 8, Kind.AIR); }
        check("scanner: short corridors (< 6 cells) are ignored", scan(shortTun, 0, 0).count(EvidenceType.EXCAVATION) == 0);
        FakeBlocks cave = new FakeBlocks(); cave.load(0, 0);
        for (int x = 2; x <= 11; x++) for (int z = 6; z <= 10; z++) { cave.set(x, 20, z, Kind.AIR); cave.set(x, 21, z, Kind.AIR); }
        check("scanner: a wide open cave is not a corridor", scan(cave, 0, 0).count(EvidenceType.EXCAVATION) == 0);

        FakeBlocks full = new FakeBlocks();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) full.load(dx, dz);
        check("scanner: fully surrounded chunk has completeness 1.0", Math.abs(scan(full, 0, 0).completeness() - 1.0) < 1e-9);

        FakeBlocks edge = new FakeBlocks(); edge.load(0, 0);
        for (int z = 0; z < 16; z++) { edge.set(5, 20, z, Kind.AIR); edge.set(5, 21, z, Kind.AIR); }
        check("scanner: unloaded neighbour is unknown, never counted as solid wall (no crash, no phantom corridor)",
                scan(edge, 0, 0).count(EvidenceType.EXCAVATION) == 16);
    }

    // ---------------------------------------------------------------- scheduler
    static void scheduler() {
        FakeBlocks b = new FakeBlocks(); b.load(0, 0); b.load(1, 0);
        ScanScheduler s = new ScanScheduler();
        List<String> done = new ArrayList<>();
        long t = 0;
        s.step(b, 0, 0, t, 5_000_000_000L, (cx, cz, e) -> done.add(cx + "," + cz));
        check("scheduler: scans every loaded chunk in radius, nearest first", done.equals(List.of("0,0", "1,0")));
        done.clear();
        s.step(b, 0, 0, t + 1000, 5_000_000_000L, (cx, cz, e) -> done.add(cx + "," + cz));
        check("scheduler: does not rescan before the rescan interval", done.isEmpty());
        s.step(b, 0, 0, t + s.rescanMs + 1, 5_000_000_000L, (cx, cz, e) -> done.add(cx + "," + cz));
        check("scheduler: rescans after the interval", done.size() == 2);

        FakeBlocks far = new FakeBlocks(); far.load(50, 50);
        ScanScheduler s2 = new ScanScheduler(); s2.radius = 4;
        List<String> d2 = new ArrayList<>();
        s2.step(far, 0, 0, 0, 5_000_000_000L, (cx, cz, e) -> d2.add("x"));
        check("scheduler: chunks outside the radius are ignored", d2.isEmpty());
        check("scheduler: unloaded chunks are never scanned", new ScanScheduler().step(new FakeBlocks(), 0, 0, 0, 5_000_000_000L, (a, c, e) -> d2.add("y")) == 0 && d2.isEmpty());

        FakeBlocks vanishing = new FakeBlocks(); vanishing.load(0, 0); vanishing.unloadAfterReads = 500;
        ScanScheduler s3 = new ScanScheduler(); s3.columnsPerSlice = 4;
        List<String> d3 = new ArrayList<>();
        s3.step(vanishing, 0, 0, 0, 5_000_000_000L, (cx, cz, e) -> d3.add("r"));
        check("scheduler: chunk unloading mid-scan aborts the job without a result", d3.isEmpty() && !s3.busy());

        ScanScheduler s4 = new ScanScheduler();
        FakeBlocks b4 = new FakeBlocks(); b4.load(0, 0);
        int n = s4.step(b4, 0, 0, 0, 1, (cx, cz, e) -> { });
        check("scheduler: a ~zero time budget does no work", n == 0);
    }

    // ---------------------------------------------------------------- movement modules
    static void movement() throws Exception {
        FakeGame g = new FakeGame(); EventBus bus = new EventBus(); ModuleContext c = ctx(g, bus);

        MovementModules.ToggleSprint ts = new MovementModules.ToggleSprint(c);
        ts.setEnabled(true);
        g.fwd = 1; bus.fireTick();
        check("sprint: moving forward starts sprinting", g.sprinting);
        g.sprinting = false; g.sneak = true; bus.fireTick();
        check("sprint: never while sneaking", !g.sprinting);
        g.sneak = false; g.sprintOk = false; bus.fireTick();
        check("sprint: respects 'sprint not allowed' (hunger)", !g.sprinting);
        g.sprintOk = true; g.fwd = 0; g.strafe = 1; bus.fireTick();
        check("sprint: sideways ignored unless enabled", !g.sprinting);
        ts.sideways.set(true); bus.fireTick();
        check("sprint: sideways when enabled", g.sprinting);
        ts.setEnabled(false);
        check("sprint: disable removes its listeners and stops sprinting", bus.listenerCount() == 0 && !g.sprinting);

        MovementModules.Freecam fc = new MovementModules.Freecam(c);
        g.x = 100; g.z = 100; g.yaw = 0; g.fwd = 1; g.strafe = 0;
        fc.setEnabled(true);
        check("freecam: enabling suppresses player input and sets an override", fc.isEnabled() && g.suppressed && g.camOverride != null);
        bus.fireFrame(1.0);
        check("freecam: camera moves forward (+Z at yaw 0) but the player does not", g.camOverride[2] > 100 && g.z == 100 && g.x == 100);
        g.fwd = 0; g.strafe = 1; double zBefore = g.camOverride[2]; bus.fireFrame(0.5);
        check("freecam: D strafes to the right (-X at yaw 0)", g.camOverride[0] < 100 && Math.abs(g.camOverride[2] - zBefore) < 1e-6);
        g.strafe = 0; g.jump = true; double yBefore = g.camOverride[1]; bus.fireFrame(0.5);
        check("freecam: jump key moves up", g.camOverride[1] > yBefore);
        g.jump = false; g.look = new float[] {90, 0}; bus.fireFrame(0.016);
        check("freecam: mouse look turns the camera", Math.abs(g.camOverride[3] - 90) < 1e-3);
        double[] pre = g.camOverride.clone();
        g.fwd = 1; g.strafe = 0; bus.fireFrame(10.0);
        double moved = Math.sqrt(Math.pow(g.camOverride[0] - pre[0], 2) + Math.pow(g.camOverride[2] - pre[2], 2));
        check("freecam: huge frame time is clamped to 0.1s (moved " + Math.round(moved * 100) / 100.0 + " blocks at 12 b/s)", moved <= 12 * 0.1 + 1e-6);
        bus.fireWorld(WorldEventType.DEATH);
        check("freecam: death ends it and restores normal control", !fc.isEnabled() && !g.suppressed && g.camOverride == null && bus.listenerCount() == 0);
        fc.setEnabled(true); bus.fireWorld(WorldEventType.DIMENSION_CHANGE);
        check("freecam: dimension change ends it", !fc.isEnabled() && g.camOverride == null);
        fc.setEnabled(true); bus.fireWorld(WorldEventType.DISCONNECT);
        check("freecam: disconnect ends it", !fc.isEnabled() && !g.suppressed);
        fc.setEnabled(true); fc.setEnabled(false);
        check("freecam: manual disable restores state", g.camOverride == null && !g.suppressed && !fc.camera().isActive());
        g.inWorld = false; fc.setEnabled(true);
        check("freecam: refuses to start outside a world (rolled back cleanly)", !fc.isEnabled() && !g.suppressed && g.camOverride == null && bus.listenerCount() == 0);
        g.inWorld = true;

        MovementModules.Zoom z = new MovementModules.Zoom(c);
        z.setEnabled(true); bus.fireFrame(0.016);
        check("zoom: narrows FOV by 1/factor", Math.abs(g.fov - 0.25) < 1e-9);
        z.setEnabled(false);
        check("zoom: disable restores FOV and removes listeners", g.fov == 1.0 && bus.listenerCount() == 0);

        MovementModules.Fullbright f = new MovementModules.Fullbright(c);
        f.setEnabled(true); bus.fireFrame(0.016);
        check("fullbright: applies brightness override", g.gamma == 1.0);
        f.setEnabled(false);
        check("fullbright: disable clears gamma", g.gamma == -1 && bus.listenerCount() == 0);
    }

    // ---------------------------------------------------------------- ESP modules
    static void esp() throws Exception {
        FakeGame g = new FakeGame(); EventBus bus = new EventBus(); ModuleContext c = ctx(g, bus); FakeSink sink = new FakeSink();
        g.ents.add(player(1, "Me", 0, 0, true));
        g.ents.add(player(2, "Near", 10, 0, false));
        g.ents.add(player(3, "Far", 500, 0, false));
        g.ents.add(new EntityInfo(4, EntityKind.HOSTILE, "Zombie", false, 5, 64, 5, 0.6, 1.8));

        RenderModules.PlayerEsp pe = new RenderModules.PlayerEsp(c);
        bus.fireRender(sink, 0.016);
        check("esp: disabled module draws nothing", sink.boxes == 0);
        pe.setEnabled(true); bus.fireRender(sink, 0.016);
        check("esp: draws only other players within range (not self, far or mobs)", sink.boxes == 1);
        check("esp: label shows name and distance", sink.labelText.get(0).startsWith("Near "));
        sink.boxes = 0; sink.frustumOk = false; bus.fireRender(sink, 0.016);
        check("esp: frustum-culled targets are skipped", sink.boxes == 0);
        sink.frustumOk = true; pe.visibility.set(RenderModules.Vis.OBSTRUCTED); g.los = true; bus.fireRender(sink, 0.016);
        check("esp: 'only behind blocks' hides visible players", sink.boxes == 0);
        g.los = false; bus.fireRender(sink, 0.016);
        check("esp: 'only behind blocks' shows hidden players", sink.boxes == 1);
        pe.visibility.set(RenderModules.Vis.VISIBLE); sink.boxes = 0; bus.fireRender(sink, 0.016);
        check("esp: 'only in sight' hides blocked players", sink.boxes == 0);
        pe.setEnabled(false);
        check("esp: disable removes the render listener", bus.listenerCount() == 0);

        g.stor.add(new StorageInfo(StorageKind.CHEST, 5, 64, 5));
        g.stor.add(new StorageInfo(StorageKind.SHULKER, 6, 64, 5));
        g.stor.add(new StorageInfo(StorageKind.SPAWNER, 400, 64, 5));
        RenderModules.StorageEsp se = new RenderModules.StorageEsp(c); se.setEnabled(true);
        sink = new FakeSink(); bus.fireRender(sink, 0.016);
        check("storage esp: in-range containers drawn, out-of-range skipped", sink.boxes == 2);
        se.shulkers.set(false); sink = new FakeSink(); bus.fireRender(sink, 0.016);
        check("storage esp: per-type filter works", sink.boxes == 1);

        RenderModules.Tracers tr = new RenderModules.Tracers(c); tr.setEnabled(true);
        sink = new FakeSink(); bus.fireRender(sink, 0.016);
        check("tracers: one line to the near player only", sink.tracers == 1);
        tr.hostiles.set(true); sink = new FakeSink(); bus.fireRender(sink, 0.016);
        check("tracers: optional hostile mobs", sink.tracers == 2);
        g.inWorld = false; sink = new FakeSink(); bus.fireRender(sink, 0.016);
        check("render modules draw nothing outside a world", sink.boxes == 0 && sink.tracers == 0);
    }

    // ---------------------------------------------------------------- self nametag
    static void nametag() {
        RenderModules.SelfNametag n = new RenderModules.SelfNametag();
        check("self nametag: disabled -> renderer gets null (vanilla)", n.current() == null);
        n.setEnabled(true);
        n.text.set("Paint"); n.colorStart.setRgb(0xFF0000); n.colorEnd.setRgb(0x0000FF); n.scale.set(2.0);
        var st = n.current();
        check("self nametag: enabled style reflects settings",
                st != null && st.resolveText("Steve").equals("Paint") && st.scale == 2.0f && st.colorAt(0, 5) == 0xFFFF0000);
        n.text.set("");
        check("self nametag: empty text falls back to the real name", n.current().resolveText("Steve").equals("Steve"));
        n.setEnabled(false);
        check("self nametag: disabling restores vanilla rendering", n.current() == null);
        check("self nametag: first person hidden by default", !n.firstPerson.get());
    }

    // ---------------------------------------------------------------- base finder module
    static void baseFinder() throws Exception {
        FakeGame g = new FakeGame(); EventBus bus = new EventBus(); ModuleContext c = ctx(g, bus);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) g.blocks.load(dx, dz);
        // A built-up chunk at (1,1): big player floor + long corridor + player blocks.
        for (int x = 16; x < 28; x++) for (int z = 16; z < 28; z++) g.blocks.set(x, 41, z, Kind.PLAYER_PLACED);
        // A purely natural chunk at (-1,-1): lots of amethyst, kelp, vines.
        for (int i = 0; i < 40; i++) g.blocks.set(-16 + (i % 16), 45, -16 + (i / 16), Kind.AMETHYST);
        for (int k = 0; k < 12; k++) for (int y = 41; y <= 55; y++) g.blocks.set(-16 + k, y, -3, Kind.KELP);

        WorldModules.BaseFinder bf = new WorldModules.BaseFinder(c);
        bf.budget.set(5.0);
        bf.setEnabled(true);
        for (int i = 0; i < 200; i++) bus.fireTick();
        var built = bf.store().get("overworld", 1, 1);
        var natural = bf.store().get("overworld", -1, -1);
        check("basefinder: built-up chunk becomes a candidate labelled Possible Structure",
                built != null && built.label() == BaseFinderScorer.Label.POSSIBLE_STRUCTURE);
        check("basefinder: natural-only chunk is never labelled Possible Structure/Activity",
                natural == null || natural.label() == BaseFinderScorer.Label.NATURAL_INDICATOR);
        check("basefinder: natural-only chunk scores far below the built one", natural == null || natural.score() < built.score());
        check("basefinder: empty natural terrain is not reported", bf.store().get("overworld", 0, 0) == null);
        check("basefinder: strong new candidate triggers an alert", g.notes.stream().anyMatch(s -> s.contains("Possible Structure")));
        int size = bf.store().size();
        for (int i = 0; i < 50; i++) bus.fireTick();
        check("basefinder: re-ticking does not duplicate records", bf.store().size() == size);

        FakeSink sink = new FakeSink();
        bus.fireRender(sink, 0.016);
        check("basefinder: draws outlines for reported candidates", sink.boxes >= 1 && sink.labels >= 1);
        sink = new FakeSink(); sink.frustumOk = false; bus.fireRender(sink, 0.016);
        check("basefinder: frustum culls outlines", sink.boxes == 0);
        bf.minScore.set(100.0); sink = new FakeSink(); bus.fireRender(sink, 0.016);
        check("basefinder: min-score filter hides weak candidates", sink.boxes == 0);

        bus.fireWorld(WorldEventType.DIMENSION_CHANGE);
        check("basefinder: dimension change clears scan state but keeps history", bf.scheduler().trackedChunks() == 0 && bf.store().size() == size);
        bf.setEnabled(false);
        check("basefinder: disable removes listeners and persists candidates", bus.listenerCount() == 0 && Files.exists(c.dataDir.resolve("candidates.tsv")));
        WorldModules.BaseFinder again = new WorldModules.BaseFinder(c);
        check("basefinder: history survives a restart", again.store().size() == size);
        g.inWorld = false; again.setEnabled(true); bus.fireTick();
        check("basefinder: does nothing outside a world", again.store().size() == size);
    }

    // ---------------------------------------------------------------- waypoints module
    static void waypoints() throws Exception {
        FakeGame g = new FakeGame(); EventBus bus = new EventBus(); ModuleContext c = ctx(g, bus);
        WorldModules.WaypointsModule w = new WorldModules.WaypointsModule(c);
        g.x = 100; g.z = 200;
        check("waypoints: add at player position", w.addHere("Home") && w.store().size() == 1);
        check("waypoints: blank name rejected", !w.addHere("   "));
        g.dim = "the_nether"; w.addHere("Portal"); g.dim = "overworld";
        w.setEnabled(true);
        g.x = 0; g.z = 0; FakeSink s = new FakeSink(); bus.fireRender(s, 0.016);
        check("waypoints: only current-dimension waypoints are drawn", s.labels == 1 && s.labelText.get(0).startsWith("Home"));
        check("waypoints: label has distance", s.labelText.get(0).contains("224 m"));
        WorldModules.WaypointsModule reload = new WorldModules.WaypointsModule(c);
        check("waypoints: persisted and reloaded", reload.store().size() == 2);
        check("waypoints: remove", w.remove("home") && w.store().size() == 1);
        w.setEnabled(false);
        check("waypoints: disable removes listener", bus.listenerCount() == 0);
    }

    // ---------------------------------------------------------------- registry
    static void registry() throws Exception {
        FakeGame g = new FakeGame(); EventBus bus = new EventBus(); ModuleContext c = ctx(g, bus);
        ModuleManager mm = new ModuleManager();
        Modules.registerAll(mm, c);
        check("registry: modules registered with unique names", mm.all().size() >= 10);
        check("registry: nothing is enabled by default", mm.all().stream().noneMatch(Module::isEnabled));
        mm.get("Freecam").setEnabled(true); mm.get("Player ESP").setEnabled(true); mm.get("Base Finder").setEnabled(true);
        mm.get("Toggle Sprint").setEnabled(true); mm.get("Zoom").setEnabled(true);
        int before = bus.listenerCount();
        int off = mm.panic();
        check("registry: panic disables every enabled module", off == 5 && mm.all().stream().noneMatch(Module::isEnabled));
        check("registry: panic leaves no listeners behind (" + before + " -> " + bus.listenerCount() + ")", before > 0 && bus.listenerCount() == 0);
        check("registry: panic restores camera/input/FOV", !g.suppressed && g.camOverride == null && g.fov == 1.0);

        Path cfg = Files.createTempDirectory("paintreg").resolve("c.properties");
        ((RenderModules.PlayerEsp) mm.get("Player ESP")).range.set(200.0);
        mm.get("Zoom").setEnabled(true);
        mm.bind(mm.get("Freecam"), 70, false);
        ConfigStore.save(mm, cfg);
        ModuleManager mm2 = new ModuleManager();
        Modules.registerAll(mm2, ctx(new FakeGame(), new EventBus()));
        ConfigStore.load(mm2, cfg);
        check("registry: real modules round-trip through the config file",
                ((RenderModules.PlayerEsp) mm2.get("Player ESP")).range.get() == 200.0 && mm2.get("Zoom").isEnabled() && mm2.get("Freecam").getKeyCode() == 70);
        Set<String> names = new HashSet<>();
        for (Module m : mm.all()) names.add(m.name);
        check("registry: every module has a description and category", mm.all().stream().allMatch(m -> !m.description.isBlank() && m.category != null));
    }
}
