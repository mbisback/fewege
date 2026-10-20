package client.paint.tools;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.ModuleManager;
import client.paint.core.Risk;
import client.paint.core.Setting;

/**
 * PREVIEW ONLY. Inert stand-ins carrying the exact names/risk tags/layout shown in Reference 1 so the
 * GUI can be rendered and compared pixel-for-pixel. These are never registered in the real client.
 */
public final class ReferenceCatalog {
    private ReferenceCatalog() {}

    enum Rot { Smooth, Snap, Off }
    enum Tgt { PLAYERS_HOSTILES("Players, Hostiles"), PLAYERS("Players");
        final String t; Tgt(String t) { this.t = t; }
        @Override public String toString() { return t; } }

    static final class Stub extends Module {
        Stub(String n, Category c, Risk r, String d) { super(n, d, c, r); }
        <S extends Setting<?>> S with(S s) { return add(s); }
    }

    private static void add(ModuleManager mm, Category c, Object[][] rows) {
        for (Object[] r : rows) mm.register(new Stub((String) r[0], c, (Risk) r[1], "Kill Aura".equals(r[0])
                ? "Detected by almost every anti-cheat. Intended for anarchy servers such as 2b2t."
                : "Preview stand-in for " + r[0] + "."));
    }

    public static ModuleManager build() {
        Risk S = Risk.SAFE, L = Risk.LOW, M = Risk.MED, H = Risk.HIGH;
        ModuleManager mm = new ModuleManager();
        add(mm, Category.COMBAT, new Object[][] {{"Kill Aura", H}, {"Aim Assist", M}, {"Auto Clicker", M}, {"Auto Totem", M},
            {"Auto Crystal", H}, {"Anchor Macro", H}, {"Mace Swap", M}, {"Shield Breaker", M}, {"Trigger Bot", H}, {"Hitbox Expand", H}});
        add(mm, Category.MOVEMENT, new Object[][] {{"Toggle Sprint", S}, {"Fly", H}, {"Boat Fly", H}, {"Speed", H}, {"Elytra Assist", M},
            {"Step", M}, {"No Fall", M}, {"Safe Walk", L}, {"Velocity", H}, {"Auto Walk", L}});
        add(mm, Category.RENDER, new Object[][] {{"Player ESP", L}, {"Storage ESP", L}, {"Tracers", L}, {"X-Ray", M}, {"Fullbright", L},
            {"Freecam", L}, {"Nametags", L}, {"Zoom", S}, {"Waypoints", S}, {"Hole ESP", L}});
        add(mm, Category.BASE_HUNT, new Object[][] {{"Base Finder", L}, {"Stash Finder", L}, {"Trail Finder", L}, {"Tunnel Finder", L},
            {"Hole Finder", L}, {"Portal Finder", L}, {"Spawner Finder", L}, {"Sus Chunk Finder", L}, {"Netherite Finder", L}});
        add(mm, Category.DONUT, new Object[][] {{"Auction Sniper", H}, {"Auto Sell", H}, {"Spawner Protect", M}, {"Item Dropper", M},
            {"RTP Base Finder", L}, {"Shulker Buyer", H}, {"Anti Trap", M}, {"Fake Stats", L}, {"Tunnel Base Fin…", L}});
        add(mm, Category.MISC, new Object[][] {{"Auto Eat", L}, {"Auto Tool", L}, {"Auto Reconnect", S}, {"Fast Place", M}, {"Auto Loot", M},
            {"Auto Mine", M}, {"Name Protect", S}, {"Force Pull", H}, {"Auto Log", L}});

        Stub ka = (Stub) mm.get("Kill Aura");
        ka.with(new Setting.Num("Range", 3.6, 0.0, 6.0, 0.1));
        ka.with(new Setting.Num("Attack speed (CPS)", 11, 1, 22, 1));
        ka.with(new Setting.Choice<>("Rotations", Rot.class, Rot.Smooth));
        ka.with(new Setting.Choice<>("Targets", Tgt.class, Tgt.PLAYERS_HOSTILES));
        mm.bind(ka, 82, false);
        mm.get("Toggle Sprint").setEnabled(true);
        mm.get("Auto Totem").setEnabled(true);
        return mm;
    }
}
