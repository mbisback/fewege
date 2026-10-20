package client.paint.core.modules;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.Risk;
import client.paint.core.Setting;
import client.paint.core.basefinder.BlockHighlighter;
import client.paint.core.basefinder.BlockSource.Kind;
import client.paint.core.game.EventBus.WorldEventType;
import client.paint.core.game.GameAccess;
import client.paint.core.game.ModuleContext;
import client.paint.core.game.ProjectileLaunch;
import client.paint.core.game.RenderSink;
import client.paint.core.game.WorldView.EntityInfo;
import client.paint.core.game.WorldView.EntityKind;
import client.paint.core.render.EspFilter;
import client.paint.core.render.Trajectory;

import java.util.List;

import static client.paint.core.modules.RenderModules.distSq;
import static client.paint.core.modules.RenderModules.withAlpha;

/** Entity ESP, Projectile Trajectory, Chunk Borders, Block ESP. All drawing is client-side only. */
public final class ExtraRenderModules {
    private ExtraRenderModules() {}

    public static final class EntityEsp extends Module {
        private final ModuleContext ctx;
        private final EspFilter filter = new EspFilter();
        public final Setting.Num range = add(new Setting.Num("Range", 64, 8, 192, 8));
        public final Setting.Bool hostiles = add(new Setting.Bool("Hostile mobs", true));
        public final Setting.Bool passives = add(new Setting.Bool("Passive mobs", false));
        public final Setting.Bool items = add(new Setting.Bool("Dropped items", true));
        public final Setting.Bool projectiles = add(new Setting.Bool("Projectiles", false));
        public final Setting.Color hostileColor = add(new Setting.Color("Hostile colour", 0xFFFF6E5A));
        public final Setting.Color passiveColor = add(new Setting.Color("Passive colour", 0xFFA1E83A));
        public final Setting.Color itemColor = add(new Setting.Color("Item colour", 0xFFFBB63E));
        public final Setting.Color projectileColor = add(new Setting.Color("Projectile colour", 0xFF40D9E3));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 1.5, 1, 5, 0.5));
        public final Setting.Bool labels = add(new Setting.Bool("Labels (name, health)", true));

        public EntityEsp(ModuleContext ctx) {
            super("Entity ESP", "Outlines mobs, dropped items (with item names) and projectiles you have loaded. Client-side drawing only.",
                    Category.RENDER, Risk.LOW);
            this.ctx = ctx;
        }

        @Override protected void onEnable() { addCleanup(ctx.events.onRender(this::render)); }

        private int colorFor(EntityKind k) {
            return switch (k) {
                case HOSTILE -> hostiles.get() ? hostileColor.argb() : 0;
                case PASSIVE -> passives.get() ? passiveColor.argb() : 0;
                case ITEM -> items.get() ? itemColor.argb() : 0;
                case PROJECTILE -> projectiles.get() ? projectileColor.argb() : 0;
                default -> 0;
            };
        }

        static String labelFor(EntityInfo e, double dist) {
            StringBuilder sb = new StringBuilder(e.name());
            if (e.health() >= 0 && e.kind() != EntityKind.ITEM) {
                sb.append(' ').append(Math.round(e.health()));
                if (e.maxHealth() > 0) sb.append('/').append(Math.round(e.maxHealth()));
            }
            return sb.append("  ").append(Math.round(dist)).append('m').toString();
        }

        private void render(RenderSink s, double dt) {
            if (!ctx.game.inWorld()) return;
            filter.maxDistance = range.get();
            double max2 = filter.maxDistance * filter.maxDistance;
            for (EntityInfo e : ctx.game.world().entities()) {
                if (e.self()) continue;
                int col = colorFor(e.kind());
                if (col == 0) continue;
                double hw = Math.max(0.15, e.width() / 2), h = Math.max(0.15, e.height());
                double d2 = distSq(s, e.x(), e.y() + h / 2, e.z());
                if (d2 > max2) continue;
                if (!s.inFrustum(e.x() - hw, e.y(), e.z() - hw, e.x() + hw, e.y() + h, e.z() + hw)) continue;
                double op = filter.opacity(d2);
                s.box(e.x() - hw, e.y(), e.z() - hw, e.x() + hw, e.y() + h, e.z() + hw,
                        withAlpha(col, op), withAlpha(col, op * 0.1), thickness.getFloat(), true);
                if (labels.get() && e.kind() != EntityKind.PROJECTILE) {
                    s.label(labelFor(e, Math.sqrt(d2)), e.x(), e.y() + h + 0.35, e.z(), withAlpha(0xFFFFFFFF, op), 1f);
                }
            }
        }
    }

    /**
     * Predicted flight path of whatever you are drawing/holding. Uses vanilla's per-tick motion rules; it
     * ignores entity hits and anything the server decides differently, so treat it as an estimate.
     */
    public static final class ProjectileTrajectory extends Module {
        private final ModuleContext ctx;
        public final Setting.Num steps = add(new Setting.Num("Max steps (ticks)", 120, 20, 300, 10));
        public final Setting.Color color = add(new Setting.Color("Colour", 0xFF5EEAD4));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 1.5, 1, 4, 0.5));
        public final Setting.Bool impact = add(new Setting.Bool("Mark impact point", true));

        public ProjectileTrajectory(ModuleContext ctx) {
            super("Trajectory", "Shows where a bow, crossbow, trident, pearl, snowball, egg or potion will land if thrown now (estimate only).",
                    Category.RENDER, Risk.SAFE);
            this.ctx = ctx;
        }

        /** The predicted path for the current held item, or an empty list. Exposed for tests and the HUD. */
        public List<double[]> predict() {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return List.of();
            ProjectileLaunch l = g.heldProjectile();
            if (l == null) return List.of();
            return Trajectory.simulate(l.kind(), l.charge(), g.eyeX(), g.eyeY() - 0.1, g.eyeZ(), g.yaw(), g.pitch(),
                    (int) Math.round(steps.get()), g.world()::segmentBlocked);
        }

        @Override protected void onEnable() { addCleanup(ctx.events.onRender(this::render)); }

        private void render(RenderSink s, double dt) {
            List<double[]> p = predict();
            if (p.size() < 2) return;
            int col = color.argb();
            for (int i = 1; i < p.size(); i++) {
                double[] a = p.get(i - 1), b = p.get(i);
                double fade = 1.0 - 0.6 * i / p.size();
                s.line(a[0], a[1], a[2], b[0], b[1], b[2], withAlpha(col, fade), thickness.getFloat());
            }
            if (impact.get()) {
                double[] e = p.get(p.size() - 1);
                s.box(e[0] - 0.15, e[1] - 0.15, e[2] - 0.15, e[0] + 0.15, e[1] + 0.15, e[2] + 0.15, col, withAlpha(col, 0.35), thickness.getFloat(), true);
            }
        }
    }

    /** Chunk borders and 16-block subchunk rings around the camera. Purely visual. */
    public static final class ChunkBorders extends Module {
        private final ModuleContext ctx;
        public final Setting.Num radius = add(new Setting.Num("Radius (chunks)", 2, 0, 6, 1));
        public final Setting.Color color = add(new Setting.Color("Colour", 0xFF40D9E3));
        public final Setting.Color currentColor = add(new Setting.Color("Current chunk", 0xFFFBB63E));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 1.5, 1, 4, 0.5));
        public final Setting.Bool subchunks = add(new Setting.Bool("Subchunk rings (every 16 blocks)", true));
        public final Setting.Num span = add(new Setting.Num("Vertical span (blocks)", 32, 16, 96, 16));

        public ChunkBorders(ModuleContext ctx) {
            super("Chunk Borders", "Draws chunk edges and 16-block layers around you to help judge chunk boundaries.", Category.RENDER, Risk.SAFE);
            this.ctx = ctx;
        }

        /** Number of line segments a frame would draw (also used by tests to prove it stays bounded). */
        public static int lineCount(int radius, boolean subchunks, int span) {
            int edges = 2 * radius + 2;                     // grid lines per axis
            int levels = subchunks ? span / 16 * 2 + 1 : 0; // rings at multiples of 16 within +-span
            return edges * edges + 2 * edges * levels;
        }

        @Override protected void onEnable() { addCleanup(ctx.events.onRender(this::render)); }

        private void render(RenderSink s, double dt) {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return;
            int r = (int) Math.round(radius.get());
            int pcx = Math.floorDiv((int) Math.floor(s.camX()), 16), pcz = Math.floorDiv((int) Math.floor(s.camZ()), 16);
            double x0 = (pcx - r) * 16.0, x1 = (pcx + r + 1) * 16.0, z0 = (pcz - r) * 16.0, z1 = (pcz + r + 1) * 16.0;
            double sp = span.get(), y0 = Math.floor((s.camY() - sp) / 16) * 16, y1 = Math.ceil((s.camY() + sp) / 16) * 16;
            int col = color.argb();
            float t = thickness.getFloat();
            for (int i = 0; i <= 2 * r + 1; i++) {
                double x = x0 + i * 16, z = z0 + i * 16;
                for (int j = 0; j <= 2 * r + 1; j++) {
                    double xx = x0 + i * 16, zz = z0 + j * 16;
                    s.line(xx, s.camY() - sp, zz, xx, s.camY() + sp, zz, col, t);
                }
                if (subchunks.get()) {
                    for (double y = y0; y <= y1; y += 16) {
                        s.line(x, y, z0, x, y, z1, withAlpha(col, 0.6), t);
                        s.line(x0, y, z, x1, y, z, withAlpha(col, 0.6), t);
                    }
                }
            }
            double cx = pcx * 16.0, cz = pcz * 16.0;
            s.box(cx, y0, cz, cx + 16, y1, cz + 16, currentColor.argb(), 0, t + 0.5f, true);
        }
    }

    /** Highlights ores (per type) and Nether portals the client has loaded, using its own bounded scanner. */
    public static final class BlockEsp extends Module {
        private final ModuleContext ctx;
        private final BlockHighlighter scanner = new BlockHighlighter();
        public final Setting.Bool diamond = add(new Setting.Bool("Diamond", true));
        public final Setting.Bool debris = add(new Setting.Bool("Ancient debris", true));
        public final Setting.Bool emerald = add(new Setting.Bool("Emerald", false));
        public final Setting.Bool gold = add(new Setting.Bool("Gold", false));
        public final Setting.Bool iron = add(new Setting.Bool("Iron", false));
        public final Setting.Bool otherOres = add(new Setting.Bool("Other ores", false));
        public final Setting.Bool portals = add(new Setting.Bool("Nether portals", true));
        public final Setting.Num scanRadius = add(new Setting.Num("Scan radius (chunks)", 5, 1, 12, 1));
        public final Setting.Num drawDistance = add(new Setting.Num("Draw distance (blocks)", 64, 16, 192, 8));
        public final Setting.Num maxBoxes = add(new Setting.Num("Max boxes drawn", 300, 50, 1500, 50));
        public final Setting.Num budget = add(new Setting.Num("Budget (ms/tick)", 1.0, 0.25, 4.0, 0.25));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 1.5, 1, 4, 0.5));
        public final Setting.Bool throughWalls = add(new Setting.Bool("Through walls", true));

        public BlockEsp(ModuleContext ctx) {
            super("Block ESP", "Outlines ores and Nether portals in chunks the client has loaded. Only shows what the server has already sent you.",
                    Category.RENDER, Risk.LOW);
            this.ctx = ctx;
        }

        public BlockHighlighter scanner() { return scanner; }

        public boolean wants(Kind k) {
            return switch (k) {
                case ORE_DIAMOND -> diamond.get(); case ORE_DEBRIS -> debris.get(); case ORE_EMERALD -> emerald.get();
                case ORE_GOLD -> gold.get(); case ORE_IRON -> iron.get(); case ORE_OTHER -> otherOres.get();
                case PORTAL -> portals.get(); default -> false;
            };
        }

        static int colorOf(Kind k) {
            return switch (k) {
                case ORE_DIAMOND -> 0xFF40D9E3; case ORE_DEBRIS -> 0xFFC8924A; case ORE_EMERALD -> 0xFFA1E83A;
                case ORE_GOLD -> 0xFFFBB63E; case ORE_IRON -> 0xFFE9EBEF; case ORE_OTHER -> 0xFF8E7BFA;
                case PORTAL -> 0xFFF974B3; default -> 0xFFFFFFFF;
            };
        }

        @Override protected void onEnable() {
            addCleanup(ctx.events.onTick(this::tick));
            addCleanup(ctx.events.onRender(this::render));
            addCleanup(ctx.events.onWorldEvent(t -> { if (t != WorldEventType.WORLD_LOAD) scanner.clear(); }));
            addCleanup(scanner::clear);
        }

        private int lastMask = -1;

        private void tick() {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return;
            int mask = 0, bit = 1;
            for (Kind k : Kind.values()) { if (wants(k)) mask |= bit; bit <<= 1; }
            if (mask != lastMask) { scanner.clear(); lastMask = mask; } // a different selection needs a fresh scan
            scanner.radius = (int) Math.round(scanRadius.get());
            int pcx = Math.floorDiv((int) Math.floor(g.posX()), 16), pcz = Math.floorDiv((int) Math.floor(g.posZ()), 16);
            scanner.step(g.world().blocks(), pcx, pcz, g.nowMs(), (long) (budget.get() * 1_000_000), this::wants);
        }

        private void render(RenderSink s, double dt) {
            GameAccess g = ctx.game;
            if (!g.inWorld()) return;
            double dd = drawDistance.get(), d2max = dd * dd;
            int pcx = Math.floorDiv((int) Math.floor(s.camX()), 16), pcz = Math.floorDiv((int) Math.floor(s.camZ()), 16);
            int cr = (int) Math.ceil(dd / 16.0), cap = (int) Math.round(maxBoxes.get()), drawn = 0;
            for (int ring = 0; ring <= cr && drawn < cap; ring++) {            // nearest chunks first
                for (int dx = -ring; dx <= ring && drawn < cap; dx++) {
                    for (int dz = -ring; dz <= ring && drawn < cap; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        for (BlockHighlighter.Hit h : scanner.hits(pcx + dx, pcz + dz)) {
                            if (drawn >= cap) break;
                            if (!wants(h.kind())) continue;
                            if (distSq(s, h.x() + 0.5, h.y() + 0.5, h.z() + 0.5) > d2max) continue;
                            if (!s.inFrustum(h.x(), h.y(), h.z(), h.x() + 1, h.y() + 1, h.z() + 1)) continue;
                            int c = colorOf(h.kind());
                            s.box(h.x(), h.y(), h.z(), h.x() + 1, h.y() + 1, h.z() + 1, c, withAlpha(c, 0.15), thickness.getFloat(), throughWalls.get());
                            drawn++;
                        }
                    }
                }
            }
        }
    }
}
