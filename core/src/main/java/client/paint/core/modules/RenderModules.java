package client.paint.core.modules;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.Risk;
import client.paint.core.Setting;
import client.paint.core.game.ModuleContext;
import client.paint.core.game.RenderSink;
import client.paint.core.game.WorldView;
import client.paint.core.game.WorldView.EntityInfo;
import client.paint.core.game.WorldView.EntityKind;
import client.paint.core.game.WorldView.StorageInfo;
import client.paint.core.nametag.NametagStyle;
import client.paint.core.render.EspFilter;

/** Player ESP, Storage ESP, Tracers, Self Nametag (shown as "Nametags"). */
public final class RenderModules {
    private RenderModules() {}

    public enum Vis {
        ALWAYS("Always"), OBSTRUCTED("Only behind blocks"), VISIBLE("Only in sight");
        private final String t;
        Vis(String t) { this.t = t; }
        @Override public String toString() { return t; }
        EspFilter.Visibility toFilter() {
            return switch (this) { case ALWAYS -> EspFilter.Visibility.ALWAYS;
                case OBSTRUCTED -> EspFilter.Visibility.ONLY_WHEN_OBSTRUCTED; case VISIBLE -> EspFilter.Visibility.ONLY_WHEN_VISIBLE; };
        }
    }

    static double distSq(RenderSink s, double x, double y, double z) {
        double dx = x - s.camX(), dy = y - s.camY(), dz = z - s.camZ();
        return dx * dx + dy * dy + dz * dz;
    }

    static int withAlpha(int argb, double opacity) {
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, opacity)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static final class PlayerEsp extends Module {
        private final ModuleContext ctx;
        private final EspFilter filter = new EspFilter();
        public final Setting.Num range = add(new Setting.Num("Range", 128, 16, 256, 8));
        public final Setting.Choice<Vis> visibility = add(new Setting.Choice<>("Visibility", Vis.class, Vis.ALWAYS));
        public final Setting.Color color = add(new Setting.Color("Colour", 0xFFFF6E5A));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 2, 1, 6, 0.5));
        public final Setting.Num fill = add(new Setting.Num("Fill opacity", 0.12, 0, 0.6, 0.02));
        public final Setting.Bool names = add(new Setting.Bool("Show names", true));

        public PlayerEsp(ModuleContext ctx) {
            super("Player ESP", "Outlines other players through walls with distance fade. Client-side drawing only.", Category.RENDER, Risk.LOW);
            this.ctx = ctx;
        }

        @Override protected void onEnable() { addCleanup(ctx.events.onRender(this::render)); }

        private void render(RenderSink s, double dt) {
            if (!ctx.game.inWorld()) return;
            filter.maxDistance = range.get();
            filter.visibility = visibility.get().toFilter();
            for (EntityInfo e : ctx.game.world().entities()) {
                if (e.kind() != EntityKind.PLAYER || e.self()) continue;
                double hw = e.width() / 2;
                double cy = e.y() + e.height() / 2, d2 = distSq(s, e.x(), cy, e.z());
                if (d2 > filter.maxDistance * filter.maxDistance) continue;
                if (!s.inFrustum(e.x() - hw, e.y(), e.z() - hw, e.x() + hw, e.y() + e.height(), e.z() + hw)) continue;
                if (!filter.shouldRender(d2, ctx.game.world().lineOfSight(e.x(), cy, e.z()))) continue;
                double op = filter.opacity(d2);
                s.box(e.x() - hw, e.y(), e.z() - hw, e.x() + hw, e.y() + e.height(), e.z() + hw,
                        withAlpha(color.argb(), op), withAlpha(color.argb(), op * fill.get()), thickness.getFloat(), true);
                if (names.get()) s.label(e.name() + " " + Math.round(Math.sqrt(d2)) + "m", e.x(), e.y() + e.height() + 0.4, e.z(), withAlpha(0xFFFFFFFF, op), 1f);
            }
        }
    }

    public static final class StorageEsp extends Module {
        private final ModuleContext ctx;
        private final EspFilter filter = new EspFilter();
        public final Setting.Num range = add(new Setting.Num("Range", 96, 16, 256, 8));
        public final Setting.Bool chests = add(new Setting.Bool("Chests", true));
        public final Setting.Bool shulkers = add(new Setting.Bool("Shulker boxes", true));
        public final Setting.Bool enderChests = add(new Setting.Bool("Ender chests", true));
        public final Setting.Bool barrels = add(new Setting.Bool("Barrels", true));
        public final Setting.Bool spawners = add(new Setting.Bool("Spawners", true));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 1.5, 1, 6, 0.5));

        public StorageEsp(ModuleContext ctx) {
            super("Storage ESP", "Outlines chests, shulker boxes, ender chests, barrels and spawners the client has loaded.", Category.RENDER, Risk.LOW);
            this.ctx = ctx;
        }

        @Override protected void onEnable() { addCleanup(ctx.events.onRender(this::render)); }

        private boolean wants(WorldView.StorageKind k) {
            return switch (k) { case CHEST, TRAPPED_CHEST -> chests.get(); case SHULKER -> shulkers.get();
                case ENDER_CHEST -> enderChests.get(); case BARREL -> barrels.get(); case SPAWNER -> spawners.get(); };
        }

        static int colorOf(WorldView.StorageKind k) {
            return switch (k) { case CHEST -> 0xFFFBB63E; case TRAPPED_CHEST -> 0xFFFF6E5A; case ENDER_CHEST -> 0xFF8E7BFA;
                case SHULKER -> 0xFFF974B3; case BARREL -> 0xFFC8924A; case SPAWNER -> 0xFF40D9E3; };
        }

        private void render(RenderSink s, double dt) {
            if (!ctx.game.inWorld()) return;
            filter.maxDistance = range.get();
            for (StorageInfo b : ctx.game.world().storage()) {
                if (!wants(b.kind())) continue;
                double d2 = distSq(s, b.x() + 0.5, b.y() + 0.5, b.z() + 0.5);
                if (d2 > filter.maxDistance * filter.maxDistance) continue;
                if (!s.inFrustum(b.x(), b.y(), b.z(), b.x() + 1, b.y() + 1, b.z() + 1)) continue;
                double op = filter.opacity(d2);
                int c = colorOf(b.kind());
                s.box(b.x(), b.y(), b.z(), b.x() + 1, b.y() + 1, b.z() + 1, withAlpha(c, op), withAlpha(c, op * 0.1), thickness.getFloat(), true);
            }
        }
    }

    public static final class Tracers extends Module {
        private final ModuleContext ctx;
        public final Setting.Num range = add(new Setting.Num("Range", 128, 16, 256, 8));
        public final Setting.Color color = add(new Setting.Color("Colour", 0xFF40D9E3));
        public final Setting.Num thickness = add(new Setting.Num("Thickness", 1.5, 1, 4, 0.5));
        public final Setting.Bool hostiles = add(new Setting.Bool("Include hostile mobs", false));

        public Tracers(ModuleContext ctx) {
            super("Tracers", "Draws lines from your crosshair to nearby players. Client-side drawing only.", Category.RENDER, Risk.LOW);
            this.ctx = ctx;
        }

        @Override protected void onEnable() { addCleanup(ctx.events.onRender(this::render)); }

        private void render(RenderSink s, double dt) {
            if (!ctx.game.inWorld()) return;
            double max2 = range.get() * range.get();
            for (EntityInfo e : ctx.game.world().entities()) {
                boolean ok = (e.kind() == EntityKind.PLAYER && !e.self()) || (hostiles.get() && e.kind() == EntityKind.HOSTILE);
                if (!ok) continue;
                double cy = e.y() + e.height() / 2, d2 = distSq(s, e.x(), cy, e.z());
                if (d2 > max2) continue;
                double fade = 1.0 - 0.7 * Math.sqrt(d2 / max2);
                s.lineFromCrosshair(e.x(), cy, e.z(), withAlpha(color.argb(), fade), thickness.getFloat());
            }
        }
    }

    /** Local-only cosmetic: changes how your own name looks on your screen. */
    public static final class SelfNametag extends Module {
        public final Setting.Text text = add(new Setting.Text("Display text", "", 32));
        public final Setting.Color colorStart = add(new Setting.Color("Colour", 0xFFFFFFFF));
        public final Setting.Color colorEnd = add(new Setting.Color("Gradient end", 0xFFFFFFFF));
        public final Setting.Bool bold = add(new Setting.Bool("Bold", false));
        public final Setting.Bool shadow = add(new Setting.Bool("Shadow", true));
        public final Setting.Bool outline = add(new Setting.Bool("Outline", false));
        public final Setting.Color background = add(new Setting.Color("Background", 0x40000000));
        public final Setting.Num scale = add(new Setting.Num("Scale", 1.0, 0.5, 3.0, 0.1));
        public final Setting.Num padX = add(new Setting.Num("Padding X", 2, 0, 16, 1));
        public final Setting.Num padY = add(new Setting.Num("Padding Y", 1, 0, 16, 1));
        public final Setting.Text prefix = add(new Setting.Text("Prefix symbol", "", 4));
        public final Setting.Text suffix = add(new Setting.Text("Suffix symbol", "", 4));
        public final Setting.Bool firstPerson = add(new Setting.Bool("Show in first person", false));
        public final Setting.Bool thirdPerson = add(new Setting.Bool("Show in third person", true));
        private final NametagStyle style = new NametagStyle();

        public SelfNametag() {
            super("Nametags", "Changes how your own name looks on YOUR screen only. Other players still see your real name.", Category.RENDER, Risk.SAFE);
        }

        /** Style for the renderer, or null when disabled (renderer must then draw vanilla). */
        public NametagStyle current() {
            if (!isEnabled()) return null;
            style.customText = text.get();
            style.colorStart = colorStart.argb(); style.colorEnd = colorEnd.argb();
            style.bold = bold.get(); style.shadow = shadow.get(); style.outline = outline.get();
            style.backgroundArgb = background.argb();
            style.setScale(scale.getFloat());
            style.setPadding((int) Math.round(padX.get()), (int) Math.round(padY.get()));
            style.prefixSymbol = prefix.get(); style.suffixSymbol = suffix.get();
            style.showInFirstPerson = firstPerson.get(); style.showInThirdPerson = thirdPerson.get();
            return style;
        }
    }
}
