package client.paint.core;

/** Runs every stage's tests together so a later stage cannot silently break an earlier one. */
public class AllTests {
    public static void main(String[] args) throws Exception {
        CoreTests.passed = 0; CoreTests.failed = 0;
        System.out.println("=== Stage 1: framework, settings, keybinds, config ===");
        CoreTests.runAll();
        System.out.println("\n=== Stages 2-5: layout, BaseFinder, Freecam, Self Nametag, render helpers ===");
        int[] r = FeatureTests.runAll();
        System.out.println("\n=== Stage 6: HUD maths, waypoints/compass, chat tools ===");
        int[] s6 = Stage6Tests.runAll();
        System.out.println("\n=== Modules, scanner, scheduler, registry (fake game) ===");
        int[] mt = ModuleTests.runAll();
        System.out.println("\n=== GUI, editor, viewport (headless) ===");
        int[] gt = GuiTests.runAll();
        System.out.println("\n=== Projection and screen-space ESP ===");
        int[] rm = RenderMathTests.runAll();
        System.out.println("\n=== New features: trajectory, entity/block ESP, utilities, HUD widgets ===");
        int[] nf = NewFeatureTests.runAll();
        int passed = CoreTests.passed + r[0] + s6[0] + mt[0] + gt[0] + rm[0] + nf[0], failed = CoreTests.failed + r[1] + s6[1] + mt[1] + gt[1] + rm[1] + nf[1];
        System.out.println("\nTOTAL: " + passed + " passed, " + failed + " failed (stage 1: "
                + CoreTests.passed + "/" + (CoreTests.passed + CoreTests.failed) + ", stages 2-5: "
                + r[0] + "/" + (r[0] + r[1]) + ", stage 6: " + s6[0] + "/" + (s6[0] + s6[1]) + ", modules: " + mt[0] + "/" + (mt[0] + mt[1]) + ", gui: " + gt[0] + "/" + (gt[0] + gt[1]) + ", projection: " + rm[0] + "/" + (rm[0] + rm[1]) + ", new features: " + nf[0] + "/" + (nf[0] + nf[1]) + ")");
        if (failed > 0) System.exit(1);
    }
}
