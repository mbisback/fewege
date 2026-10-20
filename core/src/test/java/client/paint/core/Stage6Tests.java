package client.paint.core;

import client.paint.core.chat.ChatTools;
import client.paint.core.hud.HudMath;
import client.paint.core.hud.HudMath.CpsCounter;
import client.paint.core.hud.HudMath.DurabilityColor;
import client.paint.core.world.Waypoints;
import client.paint.core.world.Waypoints.Waypoint;

import java.time.ZoneId;

/** Tests for HUD maths, waypoints/compass and chat tools. */
public class Stage6Tests {
    static int passed, failed;

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS " + name); }
        else { failed++; System.out.println("FAIL " + name); }
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    public static int[] runAll() throws Exception {
        passed = 0; failed = 0;
        hud(); waypoints(); chat(); profiles();
        return new int[] {passed, failed};
    }

    public static void main(String[] a) throws Exception {
        int[] r = runAll();
        System.out.println("\n" + r[0] + " passed, " + r[1] + " failed");
        if (r[1] > 0) System.exit(1);
    }

    static void hud() {
        CpsCounter c = new CpsCounter();
        for (int i = 0; i < 5; i++) c.click(1000 + i * 100);
        check("hud: cps counts clicks in the last second", c.cps(1500) == 5);
        check("hud: old clicks expire", c.cps(2300) == 1 && c.cps(5000) == 0);
        CpsCounter big = new CpsCounter();
        for (int i = 0; i < 100000; i++) big.click(i);
        check("hud: cps tracking is bounded", big.cps(99999) <= 200);

        check("hud: durability percent", HudMath.durabilityPercent(0, 100) == 100 && HudMath.durabilityPercent(25, 100) == 75
                && HudMath.durabilityPercent(100, 100) == 0);
        check("hud: durability clamps bad input", HudMath.durabilityPercent(-5, 100) == 100 && HudMath.durabilityPercent(500, 100) == 0);
        check("hud: unbreakable item reports -1", HudMath.durabilityPercent(0, 0) == -1);
        check("hud: durability colour buckets", HudMath.durabilityColor(90) == DurabilityColor.GREEN
                && HudMath.durabilityColor(50) == DurabilityColor.YELLOW && HudMath.durabilityColor(10) == DurabilityColor.RED);

        check("hud: session duration format", HudMath.formatDuration(3_725_000).equals("1:02:05") && HudMath.formatDuration(-5).equals("0:00:00"));
        check("hud: effect timer format (20 ticks = 1s)", HudMath.formatEffectTicks(20 * 102).equals("1:42") && HudMath.formatEffectTicks(-1).equals("0:00"));
        check("hud: speed in blocks per second", near(HudMath.speedBps(3, 4, 1000), 5) && HudMath.speedBps(3, 4, 0) == 0);
    }

    static void waypoints() {
        check("waypoints: target ahead (+Z) is bearing 0 at yaw 0", near(Waypoints.relativeBearing(0, 0, 0, 0, 100), 0));
        check("waypoints: east target while facing south is 90 degrees LEFT", near(Waypoints.relativeBearing(0, 0, 0, 100, 0), -90));
        check("waypoints: west target while facing south is 90 degrees RIGHT", near(Waypoints.relativeBearing(0, 0, 0, -100, 0), 90));
        check("waypoints: target behind is +-180", Math.abs(Math.abs(Waypoints.relativeBearing(0, 0, 0, 0, -100)) - 180) < 1e-6);
        check("waypoints: facing the target gives 0", near(Waypoints.relativeBearing(-90, 0, 0, 100, 0), 0));

        check("waypoints: horizontal and 3D distance", near(Waypoints.horizontalDistance(0, 0, 3, 4), 5) && near(Waypoints.distance3d(0, 0, 0, 2, 3, 6), 7));
        check("waypoints: nether 8:1 conversion round-trips", near(Waypoints.overworldToNether(800), 100) && near(Waypoints.netherToOverworld(100), 800));
        check("waypoints: facing names", Waypoints.facing(0).startsWith("south") && Waypoints.facing(90).startsWith("west")
                && Waypoints.facing(-90).startsWith("east") && Waypoints.facing(180).startsWith("north") && Waypoints.facing(-180).startsWith("north"));
        check("waypoints: distance formatting", Waypoints.formatDistance(842).equals("842 m") && Waypoints.formatDistance(1234).equals("1.2 km"));

        Waypoints w = new Waypoints();
        check("waypoints: blank/NaN rejected", !w.put(new Waypoint(" ", "overworld", 0, 0, 0)) && !w.put(new Waypoint("a", "overworld", Double.NaN, 0, 0)));
        w.put(new Waypoint("Far", "overworld", 1000, 64, 0));
        w.put(new Waypoint("Near", "overworld", 10, 64, 0));
        w.put(new Waypoint("Hell", "the_nether", 1, 64, 1));
        check("waypoints: nearest-first, dimension filtered", w.nearest("overworld", 0, 0).size() == 2 && w.nearest("overworld", 0, 0).get(0).name().equals("Near"));
        w.put(new Waypoint("NEAR", "overworld", 20, 64, 0));
        check("waypoints: same name (any case) replaces instead of duplicating", w.size() == 3);
        check("waypoints: remove", w.remove("overworld", "far") && !w.remove("overworld", "far") && w.size() == 2);
    }

    static void chat() {
        ZoneId syd = ZoneId.of("Australia/Sydney");
        long t = java.time.ZonedDateTime.of(2026, 10, 19, 15, 4, 9, 0, syd).toInstant().toEpochMilli();
        check("chat: 24h timestamp in the given zone", ChatTools.timestamp(t, syd, true).equals("[15:04:09] "));
        check("chat: 12h timestamp", ChatTools.timestamp(t, syd, false).equals("[3:04:09 PM] "));
        check("chat: same instant, different zone", ChatTools.timestamp(t, ZoneId.of("UTC"), true).equals("[04:04:09] "));

        ChatTools.Filter f = new ChatTools.Filter();
        check("chat: empty filter hides nothing", !f.shouldHide("anything"));
        f.add("Spam"); f.add("  "); f.add(null);
        check("chat: case-insensitive substring match", f.shouldHide("this is SPAM!") && !f.shouldHide("hello"));
        check("chat: null message safe", !f.shouldHide(null));
        f.clear();
        check("chat: clear removes terms", !f.shouldHide("spam"));
    }

    static void profiles() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("paintprof");
        ProfileManager pm = new ProfileManager(dir.resolve("profiles"));
        ModuleManager mm = new ModuleManager();
        client.paint.core.modules.Appearance ap = new client.paint.core.modules.Appearance();
        mm.register(ap);
        ap.guiScale.set(1.3); ap.setEnabled(true);
        check("profile: names are validated", ProfileManager.validName("pvp_1") && !ProfileManager.validName("../x") && !ProfileManager.validName("") && !ProfileManager.validName("a b"));
        check("profile: save rejects bad names", !pm.save("../evil", mm) && pm.list().isEmpty());
        check("profile: save and list", pm.save("main", mm) && pm.save("alt", mm) && pm.list().equals(java.util.List.of("alt", "main")));
        ap.guiScale.set(0.8); ap.setEnabled(false);
        check("profile: load restores the saved state", pm.load("main", mm) != null && ap.guiScale.get() == 1.3 && ap.isEnabled());
        check("profile: effective values follow the enabled state", ap.effectiveScale() == 1.3);
        check("profile: loading a missing profile reports null and changes nothing", pm.load("nope", mm) == null && ap.guiScale.get() == 1.3);
        java.nio.file.Path ex = dir.resolve("exported.properties");
        check("profile: export", pm.exportTo("main", ex) && java.nio.file.Files.exists(ex));
        check("profile: import as a new profile", pm.importFrom(ex, "copy") && pm.list().contains("copy"));
        java.nio.file.Files.writeString(dir.resolve("bad.properties"), "x=\\uZZZZ");
        check("profile: corrupt import is rejected", !pm.importFrom(dir.resolve("bad.properties"), "bad") && !pm.list().contains("bad"));
        check("profile: delete", pm.delete("copy") && !pm.list().contains("copy") && !pm.delete("copy"));
        ProfileManager.resetAll(mm);
        check("profile: reset-all disables and restores defaults", !ap.isEnabled() && ap.guiScale.get() == 1.0 && ap.effectiveScale() == 1.0);
        check("appearance: not touched by panic", !ap.panicSafe());
    }
}
