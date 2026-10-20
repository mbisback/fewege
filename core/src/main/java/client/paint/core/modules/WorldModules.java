package client.paint.core.modules;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.Risk;
import client.paint.core.Setting;
import client.paint.core.basefinder.BaseFinderScorer;
import client.paint.core.basefinder.BaseFinderScorer.Label;
import client.paint.core.basefinder.CandidateStore;
import client.paint.core.basefinder.CandidateStore.Candidate;
import client.paint.core.basefinder.ScanScheduler;
import client.paint.core.game.EventBus.WorldEventType;
import client.paint.core.game.GameAccess;
import client.paint.core.game.ModuleContext;
import client.paint.core.game.RenderSink;
import client.paint.core.world.Waypoints;
import client.paint.core.world.Waypoints.Waypoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Waypoints and Base Finder. */
public final class WorldModules {
    private WorldModules() {}

    public static final class WaypointsModule extends Module {
        private final ModuleContext ctx;
        private final Waypoints store = new Waypoints();
        private final Path file;
        public final Setting.Num maxDistance = add(new Setting.Num("Max distance", 2000, 100, 20000, 100));
        public final Setting.Color color = add(new Setting.Color("Colour", 0xFFF974B3));

        public WaypointsModule(ModuleContext ctx) {
            super("Waypoints", "Saved locations per dimension with distance labels in the world. Add with the chat command or the API.",
                    Category.RENDER, Risk.SAFE);
            this.ctx = ctx;
            this.file = ctx.dataDir.resolve("waypoints.tsv");
            load();
        }

        public Waypoints store() { return store; }

        /** Save a waypoint at the player's position. */
        public boolean addHere(String name) {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return false;
            boolean ok = store.put(new Waypoint(name.strip(), g.dimension(), g.posX(), g.posY(), g.posZ()));
            if (ok) save();
            return ok;
        }

        /** Attach a note to an existing waypoint in the current dimension. @return false if there is no such waypoint. */
        public boolean setNote(String name, String note) {
            String dim = ctx.game.dimension();
            for (Waypoint w : store.nearest(dim, 0, 0)) {
                if (w.name().equalsIgnoreCase(name.strip())) {
                    store.put(new Waypoint(w.name(), w.dimension(), w.x(), w.y(), w.z(), clean(note)));
                    save();
                    return true;
                }
            }
            return false;
        }

        private static String clean(String t) { return t == null ? "" : t.replaceAll("[\\t\\r\\n]", " ").strip(); }

        /** Nearest waypoint in this dimension for the HUD compass, or null. */
        public client.paint.core.gui.HudData.WaypointInfo nearestInfo() {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return null;
            List<Waypoint> l = store.nearest(g.dimension(), g.posX(), g.posZ());
            if (l.isEmpty()) return null;
            Waypoint w = l.get(0);
            return new client.paint.core.gui.HudData.WaypointInfo(w.name(), Waypoints.horizontalDistance(g.posX(), g.posZ(), w.x(), w.z()),
                    Waypoints.relativeBearing(g.yaw(), g.posX(), g.posZ(), w.x(), w.z()));
        }

        public boolean remove(String name) {
            boolean ok = store.remove(ctx.game.dimension(), name);
            if (ok) save();
            return ok;
        }

        @Override protected void onEnable() { addCleanup(ctx.events.onRender(this::render)); }

        private void render(RenderSink s, double dt) {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return;
            for (Waypoint w : store.nearest(g.dimension(), g.posX(), g.posZ())) {
                double d = Waypoints.horizontalDistance(g.posX(), g.posZ(), w.x(), w.z());
                if (d > maxDistance.get()) break; // sorted nearest-first
                double bearing = Waypoints.relativeBearing(g.yaw(), g.posX(), g.posZ(), w.x(), w.z());
                String arrow = Math.abs(bearing) < 20 ? "↑" : bearing < 0 ? "←" : "→";
                s.label(w.name() + "  " + Waypoints.formatDistance(d) + " " + arrow + (w.note().isEmpty() ? "" : "  - " + w.note()), w.x() + 0.5, w.y() + 1.5, w.z() + 0.5, color.argb(), 1f);
            }
        }

        public void save() {
            StringBuilder sb = new StringBuilder("#paint-waypoints v2\n");
            for (String dim : new String[] {"overworld", "the_nether", "the_end"}) {
                for (Waypoint w : store.nearest(dim, 0, 0)) {
                    sb.append(dim).append('\t').append(w.name().replaceAll("[\\t\\r\\n]", " ")).append('\t')
                      .append(w.x()).append('\t').append(w.y()).append('\t').append(w.z()).append('\t').append(clean(w.note())).append('\n');
                }
            }
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException ignored) { /* non-fatal */ }
        }

        private void load() {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.startsWith("#") || line.isBlank()) continue;
                    String[] f = line.split("\t");
                    if (f.length != 5 && f.length != 6) continue; // v1 files have no note column
                    try { store.put(new Waypoint(f[1], f[0], Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4]), f.length == 6 ? f[5] : "")); }
                    catch (NumberFormatException ignored) { /* skip bad line */ }
                }
            } catch (IOException | RuntimeException ignored) { /* none yet or unreadable: start empty */ }
        }
    }

    public static final class BaseFinder extends Module {
        private final ModuleContext ctx;
        private final ScanScheduler scheduler = new ScanScheduler();
        private final BaseFinderScorer.Config scoreConfig = new BaseFinderScorer.Config();
        private final BaseFinderScorer scorer = new BaseFinderScorer(scoreConfig);
        private final CandidateStore store = new CandidateStore();
        private final Path file;

        public final Setting.Num radius = add(new Setting.Num("Scan radius (chunks)", 6, 2, 16, 1));
        public final Setting.Num budget = add(new Setting.Num("Budget (ms/tick)", 1.0, 0.25, 5.0, 0.25));
        public final Setting.Num minScore = add(new Setting.Num("Min score", 10, 0, 100, 1));
        public final Setting.Num drawRange = add(new Setting.Num("Draw distance (chunks)", 12, 2, 32, 1));
        public final Setting.Num thickness = add(new Setting.Num("Outline thickness", 2, 1, 6, 0.5));
        public final Setting.Num opacity = add(new Setting.Num("Opacity", 0.9, 0.1, 1.0, 0.05));
        public final Setting.Bool labels = add(new Setting.Bool("Show labels", true));
        public final Setting.Bool alerts = add(new Setting.Bool("Alert on strong candidates", true));

        public BaseFinder(ModuleContext ctx) {
            super("Base Finder", "Scores loaded chunks for signs of player activity. Natural clues are only hints, never proof of a base.",
                    Category.BASE_HUNT, Risk.LOW);
            this.ctx = ctx;
            this.file = ctx.dataDir.resolve("candidates.tsv");
            store.load(file);
        }

        public CandidateStore store() { return store; }
        public ScanScheduler scheduler() { return scheduler; }
        public BaseFinderScorer.Config scoreConfig() { return scoreConfig; }

        @Override protected void onEnable() {
            addCleanup(ctx.events.onTick(this::tick));
            addCleanup(ctx.events.onRender(this::render));
            addCleanup(ctx.events.onWorldEvent(t -> {
                if (t != WorldEventType.WORLD_LOAD) scheduler.clear();
                if (t == WorldEventType.DISCONNECT) saveStore();
            }));
            addCleanup(() -> { scheduler.clear(); saveStore(); });
        }

        public void saveStore() { try { store.save(file); } catch (IOException ignored) { /* non-fatal */ } }

        private void tick() {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return;
            scheduler.radius = (int) Math.round(radius.get());
            int pcx = Math.floorDiv((int) Math.floor(g.posX()), 16), pcz = Math.floorDiv((int) Math.floor(g.posZ()), 16);
            String dim = g.dimension();
            long now = g.nowMs();
            scheduler.step(g.world().blocks(), pcx, pcz, now, (long) (budget.get() * 1_000_000), (cx, cz, ev) -> {
                BaseFinderScorer.Result r = scorer.score(ev);
                if (!r.reportable) return;
                Candidate before = store.get(dim, cx, cz);
                store.upsert(dim, cx, cz, r.score, r.label, r.label.title + " (" + r.confidence + "): " + r.evidenceSummary, now);
                boolean isNew = before == null;
                if (alerts.get() && isNew && r.label != Label.NATURAL_INDICATOR && r.confidence != BaseFinderScorer.Confidence.LOW) {
                    g.notifyPlayer("Base Finder: " + r.label.title + " at chunk " + cx + ", " + cz + " (score " + Math.round(r.score) + ")");
                }
            });
        }

        static int colorFor(Label l, double score) {
            return switch (l) {
                case NATURAL_INDICATOR -> 0xFF40D9E3;
                case POSSIBLE_ACTIVITY -> score >= 40 ? 0xFFFBB63E : 0xFFA1E83A;
                case POSSIBLE_STRUCTURE -> 0xFFFF6E5A;
            };
        }

        private void render(RenderSink s, double dt) {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return;
            String dim = g.dimension();
            int pcx = Math.floorDiv((int) Math.floor(g.posX()), 16), pcz = Math.floorDiv((int) Math.floor(g.posZ()), 16);
            int range = (int) Math.round(drawRange.get());
            double y0 = s.camY() - 24, y1 = s.camY() + 24;
            for (Candidate c : store.query(minScore.get(), CandidateStore.BY_SCORE_DESC)) {
                if (!c.dimension().equals(dim)) continue;
                if (Math.abs(c.chunkX() - pcx) > range || Math.abs(c.chunkZ() - pcz) > range) continue;
                double x0 = c.chunkX() * 16.0, z0 = c.chunkZ() * 16.0;
                if (!s.inFrustum(x0, y0, z0, x0 + 16, y1, z0 + 16)) continue;
                int col = colorFor(c.label(), c.score());
                int a = (int) Math.round(255 * opacity.get());
                s.box(x0, y0, z0, x0 + 16, y1, z0 + 16, (a << 24) | (col & 0xFFFFFF), ((a / 8) << 24) | (col & 0xFFFFFF),
                        thickness.getFloat(), true);
                if (labels.get()) s.label(c.label().title + " " + Math.round(c.score()), x0 + 8, s.camY(), z0 + 8, 0xFFFFFFFF, 1f);
            }
        }
    }
}
