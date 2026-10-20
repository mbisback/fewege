package client.paint.core;

import client.paint.core.basefinder.*;
import client.paint.core.basefinder.BaseFinderScorer.Confidence;
import client.paint.core.basefinder.BaseFinderScorer.Label;
import client.paint.core.freecam.FreecamCamera;
import client.paint.core.layout.LayoutModel;
import client.paint.core.layout.LayoutModel.Handle;
import client.paint.core.layout.LayoutModel.Widget;
import client.paint.core.nametag.NametagStyle;
import client.paint.core.render.BoundedCache;
import client.paint.core.render.EspFilter;

import java.nio.file.Files;
import java.nio.file.Path;

/** Tests for layout editor, BaseFinder, Freecam, Self Nametag and render helpers. */
public class FeatureTests {
    static int passed, failed;

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("PASS " + name); }
        else { failed++; System.out.println("FAIL " + name); }
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    /** @return {passed, failed} */
    public static int[] runAll() throws Exception {
        passed = 0; failed = 0;
        layout(); baseFinder(); store(); freecam(); nametag(); render();
        return new int[] {passed, failed};
    }

    public static void main(String[] a) throws Exception {
        int[] r = runAll();
        System.out.println("\n" + r[0] + " passed, " + r[1] + " failed");
        if (r[1] > 0) System.exit(1);
    }

    // ---------- layout editor ----------
    static LayoutModel model() {
        LayoutModel m = new LayoutModel(1920, 1080);
        m.add(new Widget("fps", "FPS and Ping", 0.02, 0.10, 0.10, 0.06, 40, 20));
        m.add(new Widget("coords", "Coordinates", 0.02, 0.20, 0.12, 0.10, 40, 20));
        return m;
    }

    static void layout() throws Exception {
        LayoutModel m = model();
        Widget fps = m.get("fps");
        check("layout: default placement scales to screen", near(fps.x, 38.4) && near(fps.y, 108));
        check("layout: pick selects topmost widget under cursor", m.pick(fps.x + 5, fps.y + 5) == fps && m.selected() == fps);
        check("layout: click on empty space deselects", m.pick(1800, 1000) == null && m.selected() == null);

        m.setSnap(true);
        m.drag(fps, 20, 13);
        check("layout: drag snaps to 8px grid", fps.x % 8 == 0 && fps.y % 8 == 0);
        m.drag(fps, -99999, -99999);
        check("layout: drag clamps to top-left of screen", fps.x == 0 && fps.y == 0);
        m.drag(fps, 99999, 99999);
        check("layout: drag clamps so widget stays fully on screen", fps.x + fps.w <= 1920 && fps.y + fps.h <= 1080);

        m.setSnap(false);
        double x0 = fps.x;
        m.drag(fps, -3, 0);
        check("layout: snap off allows free movement", near(fps.x, x0 - 3));

        m.setSnap(false);
        Widget c = m.get("coords");
        double right = c.x + c.w, bottom = c.y + c.h;
        m.resize(c, Handle.SE, 30, 20);
        check("layout: SE resize grows and keeps top-left fixed", near(c.x + c.w, right + 30) && near(c.y + c.h, bottom + 20));
        m.resize(c, Handle.NW, 5000, 5000);
        check("layout: resize enforces minimum size", c.w >= 40 && c.h >= 20);
        check("layout: NW resize keeps bottom-right fixed", near(c.x + c.w, right + 30) && near(c.y + c.h, bottom + 20));
        m.resize(c, Handle.SE, 99999, 99999);
        check("layout: resize cannot leave the screen", c.x + c.w <= 1920 + 1e-9 && c.y + c.h <= 1080 + 1e-9);

        m.pick(c.x + 2, c.y + 2);
        check("layout: corner handle hit-test", m.handleAt(c.x, c.y, 6) == Handle.NW && m.handleAt(c.x + c.w, c.y + c.h, 6) == Handle.SE);

        // persistence + resolution independence
        LayoutModel a = model();
        a.setSnap(false);
        a.drag(a.get("fps"), 400, 300);
        Path file = Files.createTempDirectory("paintlayout").resolve("layout.properties");
        a.save(file);
        LayoutModel b = new LayoutModel(1280, 720);
        b.add(new Widget("fps", "FPS and Ping", 0.02, 0.10, 0.10, 0.06, 40, 20));
        b.add(new Widget("coords", "Coordinates", 0.02, 0.20, 0.12, 0.10, 40, 20));
        check("layout: load succeeds", b.load(file));
        check("layout: saved layout is rescaled to a different resolution",
                near(b.get("fps").x / 1280, a.get("fps").x / 1920) && near(b.get("fps").y / 720, a.get("fps").y / 1080));

        Files.writeString(file, "widget.fps.x=banana\nwidget.coords.x=2.5\n");
        LayoutModel d = model();
        double dx = d.get("fps").x;
        d.load(file);
        check("layout: invalid saved values fall back to defaults", near(d.get("fps").x, dx));
        Files.writeString(file, "x=\\uZZ");
        check("layout: corrupt file ignored without exception", !model().load(file));

        LayoutModel e = model();
        e.setSnap(false);
        e.drag(e.get("fps"), 300, 300);
        e.resetAll();
        check("layout: reset restores defaults", near(e.get("fps").x, 38.4));

        LayoutModel f = model();
        f.setSnap(false);
        double fx = f.get("fps").x / 1920;
        f.setScreenSize(1280, 720);
        check("layout: live screen resize keeps relative position", near(f.get("fps").x / 1280, fx));
    }

    // ---------- BaseFinder scoring ----------
    static BaseFinderScorer scorer() { return new BaseFinderScorer(new BaseFinderScorer.Config()); }

    static void baseFinder() {
        BaseFinderScorer s = scorer();
        var natural = s.score(new ChunkEvidence().put(EvidenceType.AMETHYST_LARGE, 100)
                .put(EvidenceType.TALL_KELP, 500).put(EvidenceType.LONG_VINES, 500)
                .put(EvidenceType.UNUSUAL_VEGETATION, 100));
        check("basefinder: natural-only chunk is labelled Natural Indicator", natural.label == Label.NATURAL_INDICATOR);
        check("basefinder: natural-only chunk never exceeds LOW confidence", natural.confidence == Confidence.LOW);
        check("basefinder: natural-only score is capped", natural.score <= 30 + 1e-9);

        var noise = s.score(new ChunkEvidence().put(EvidenceType.TALL_KELP, 2));
        check("basefinder: below minCount contributes nothing", noise.score == 0 && !noise.reportable);

        var structure = s.score(new ChunkEvidence().put(EvidenceType.PLAYER_BLOCKS, 40)
                .put(EvidenceType.CONSTRUCTION_PATTERN, 3));
        check("basefinder: construction evidence -> Possible Structure, HIGH confidence",
                structure.label == Label.POSSIBLE_STRUCTURE && structure.confidence == Confidence.HIGH && structure.reportable);

        var activity = s.score(new ChunkEvidence().put(EvidenceType.EXCAVATION, 8));
        check("basefinder: excavation -> Possible Activity", activity.label == Label.POSSIBLE_ACTIVITY);

        check("basefinder: structure outranks any amount of natural evidence", structure.score > natural.score);

        var partial = s.score(new ChunkEvidence().put(EvidenceType.PLAYER_BLOCKS, 40)
                .put(EvidenceType.CONSTRUCTION_PATTERN, 3).completeness(0.3));
        check("basefinder: incomplete data lowers score and limits confidence",
                partial.incomplete && partial.score < structure.score && partial.confidence == Confidence.LOW);

        check("basefinder: evidence summary lists only used indicators",
                structure.evidenceSummary.contains("PLAYER_BLOCKS x40") && !structure.evidenceSummary.contains("TALL_KELP"));

        var cfg = new BaseFinderScorer.Config();
        cfg.weights.put(EvidenceType.CONSTRUCTION_PATTERN, 1.0);
        var tuned = new BaseFinderScorer(cfg).score(new ChunkEvidence().put(EvidenceType.CONSTRUCTION_PATTERN, 3));
        check("basefinder: weights are adjustable", tuned.score == 3.0);
    }

    // ---------- candidate history ----------
    static void store() throws Exception {
        CandidateStore st = new CandidateStore();
        st.upsert("overworld", 10, -4, 40, Label.POSSIBLE_STRUCTURE, "PLAYER_BLOCKS x40", 1000L);
        st.upsert("overworld", 10, -4, 20, Label.POSSIBLE_ACTIVITY, "weaker rescan", 2000L);
        check("store: duplicate chunk is one record", st.size() == 1);
        check("store: weaker rescan does not overwrite a better hit", st.get("overworld", 10, -4).score() == 40);
        st.upsert("overworld", 10, -4, 55, Label.POSSIBLE_STRUCTURE, "better", 3000L);
        check("store: stronger rescan updates score but keeps first detection time",
                st.get("overworld", 10, -4).score() == 55 && st.get("overworld", 10, -4).detectedAtMs() == 1000L);
        st.upsert("the_nether", 10, -4, 30, Label.POSSIBLE_ACTIVITY, "x", 1L);
        check("store: same chunk in another dimension is separate", st.size() == 2);

        st.setBookmarked("overworld", 10, -4, true);
        st.setRevisited("overworld", 10, -4, true);
        st.upsert("overworld", 10, -4, 99, Label.POSSIBLE_STRUCTURE, "again", 4000L);
        check("store: bookmark and revisit status survive re-detection",
                st.get("overworld", 10, -4).bookmarked() && st.get("overworld", 10, -4).revisited());
        check("store: query filters by min score and sorts",
                st.query(50, CandidateStore.BY_SCORE_DESC).size() == 1 && st.query(0, CandidateStore.BY_SCORE_DESC).get(0).score() == 99);

        String text = st.export();
        CandidateStore other = new CandidateStore();
        int[] r = other.importFrom(text);
        check("store: export/import roundtrip", r[0] == 2 && r[2] == 0 && other.get("overworld", 10, -4).bookmarked());
        int[] r2 = other.importFrom(text);
        check("store: re-importing merges instead of duplicating", other.size() == 2 && r2[0] == 0 && r2[1] == 2);
        int[] r3 = new CandidateStore().importFrom(HEADERLESS_BAD);
        check("store: malformed lines skipped, good line kept", r3[0] == 1 && r3[2] == 4);

        CandidateStore small = new CandidateStore(3);
        small.upsert("o", 0, 0, 1, Label.NATURAL_INDICATOR, "", 1);
        small.setBookmarked("o", 0, 0, true);
        for (int i = 1; i <= 5; i++) small.upsert("o", i, 0, 1, Label.NATURAL_INDICATOR, "", i);
        check("store: bounded size, bookmarked entries are never evicted", small.size() == 3 && small.get("o", 0, 0) != null);

        Path f = Files.createTempDirectory("paintcand").resolve("c.tsv");
        st.save(f);
        CandidateStore loaded = new CandidateStore();
        loaded.load(f);
        check("store: save/load from disk", loaded.size() == 2);
        check("store: missing file loads as empty", new CandidateStore().load(f.resolveSibling("nope"))[0] == 0);
    }

    static final String HEADERLESS_BAD = String.join("\n",
            "#paint-candidates v1",
            "overworld\t1\t2\t30.0\tPOSSIBLE_ACTIVITY\t5\tfalse\tfalse\tok",
            "overworld\tX\t2\t30.0\tPOSSIBLE_ACTIVITY\t5\tfalse\tfalse\tbadint",
            "overworld\t1\t3\tNaN\tPOSSIBLE_ACTIVITY\t5\tfalse\tfalse\tnan",
            "overworld\t1\t4\t30.0\tCONFIRMED_BASE\t5\tfalse\tfalse\tbadlabel",
            "too\tfew\tfields");

    // ---------- Freecam ----------
    static void freecam() {
        FreecamCamera c = new FreecamCamera();
        c.begin(100, 64, 100, 0f, 10f);
        c.move(1, 0, 0, 10, 1); // W facing +Z
        check("freecam: yaw 0 forward moves +Z", near(c.x, 100) && near(c.z, 110));
        c.begin(0, 0, 0, 99f, 99f);
        check("freecam: begin while active never overwrites the saved snapshot", near(c.x, 100));

        FreecamCamera e = new FreecamCamera();
        e.begin(0, 0, 0, -90f, 0f); // facing +X
        e.move(1, 0, 0, 5, 1);
        check("freecam: yaw -90 forward moves +X", near(e.x, 5) && near(e.z, 0));
        e.move(0, 1, 0, 5, 1);
        check("freecam: facing +X, D (right) moves +Z", near(e.z, 5));
        e.move(0, -1, 0, 5, 1);
        check("freecam: facing +X, A (left) moves -Z", near(e.z, 0));
        e.move(-1, 0, 0, 5, 1);
        check("freecam: S moves backward", near(e.x, 0));

        FreecamCamera w = new FreecamCamera();
        w.begin(0, 0, 0, 90f, 0f); // facing -X
        w.move(1, 0, 0, 4, 1);
        check("freecam: yaw 90 forward moves -X", near(w.x, -4));

        FreecamCamera g = new FreecamCamera();
        g.begin(0, 0, 0, 0f, 0f);
        g.move(1, 1, 0, 10, 1);
        check("freecam: diagonal is normalised (no speed boost)", near(Math.hypot(g.x, g.z), 10));
        g.move(0, 0, 1, 10, 1);
        check("freecam: vertical input moves Y independently", near(g.y, 10));

        FreecamCamera p = new FreecamCamera();
        p.begin(0, 0, 0, 0f, 0f);
        p.look(0, 500f);
        check("freecam: pitch clamps to 90", p.pitch == 90f);
        p.look(540f, 0);
        check("freecam: yaw wraps into [-180,180)", p.yaw >= -180f && p.yaw < 180f);

        FreecamCamera s = new FreecamCamera();
        s.begin(1, 2, 3, 45f, 20f);
        s.move(1, 0, 1, 10, 3);
        var snap = s.stop(FreecamCamera.StopReason.DEATH);
        check("freecam: stop returns the original player view exactly",
                snap.isPresent() && snap.get().x() == 1 && snap.get().y() == 2 && snap.get().z() == 3
                        && snap.get().yaw() == 45f && snap.get().pitch() == 20f);
        check("freecam: stop is idempotent (disconnect/dimension after death is safe)",
                !s.isActive() && s.stop(FreecamCamera.StopReason.DISCONNECT).isEmpty());
        s.move(1, 0, 0, 10, 1);
        check("freecam: inactive camera ignores input", near(s.x, s.x) && !s.isActive());
    }

    // ---------- Self Nametag ----------
    static void nametag() {
        NametagStyle n = new NametagStyle();
        check("nametag: default shows the real username unchanged", n.resolveText("Steve").equals("Steve"));
        n.customText = "  Paint§kHero\n ";
        check("nametag: control chars and section-sign codes are stripped", n.resolveText("Steve").equals("PaintHero"));
        n.customText = "x".repeat(500);
        check("nametag: text length is bounded", n.resolveText("Steve").length() <= NametagStyle.MAX_TEXT + 8);
        n.customText = "Hero"; n.prefixSymbol = "★ "; n.suffixSymbol = " ✦";
        check("nametag: decorative symbols wrap the text", n.resolveText("Steve").contains("Hero") && n.resolveText("Steve").startsWith("★"));

        n.reset();
        n.colorStart = 0xFFFF0000; n.colorEnd = 0xFF0000FF;
        check("nametag: gradient starts and ends on the chosen colours",
                n.colorAt(0, 5) == 0xFFFF0000 && n.colorAt(4, 5) == 0xFF0000FF);
        int mid = n.colorAt(2, 5);
        check("nametag: gradient midpoint blends", ((mid >> 16) & 0xFF) == 128 && (mid & 0xFF) == 128);
        n.colorEnd = n.colorStart;
        check("nametag: equal colours give a solid colour", n.colorAt(3, 7) == 0xFFFF0000);

        check("nametag: #RRGGBB parses opaque", NametagStyle.parseColor("#00FF00", 0) == 0xFF00FF00);
        check("nametag: #AARRGGBB parses alpha", NametagStyle.parseColor("#8000FF00", 0) == 0x8000FF00);
        check("nametag: invalid colour uses fallback", NametagStyle.parseColor("zzz", 7) == 7 && NametagStyle.parseColor(null, 9) == 9);

        n.setScale(99f);
        check("nametag: scale clamps", n.scale == 3.0f);
        n.setPadding(-5, 100);
        check("nametag: padding clamps", n.paddingX == 0 && n.paddingY == 16);
        check("nametag: first/third person visibility", !n.visibleIn(true) && n.visibleIn(false));

        n.customText = "Hero"; n.bold = true; n.outline = true; n.reset();
        check("nametag: reset restores vanilla-looking defaults",
                n.customText.isEmpty() && !n.bold && !n.outline && n.scale == 1.0f && n.resolveText("Steve").equals("Steve"));
    }

    // ---------- render helpers ----------
    static void render() {
        EspFilter f = new EspFilter();
        f.maxDistance = 100;
        check("esp: beyond max distance is culled", !f.shouldRender(101 * 101, true) && f.shouldRender(99 * 99, true));
        f.visibility = EspFilter.Visibility.ONLY_WHEN_OBSTRUCTED;
        check("esp: obstructed-only mode", f.shouldRender(100, false) && !f.shouldRender(100, true));
        f.visibility = EspFilter.Visibility.ONLY_WHEN_VISIBLE;
        check("esp: visible-only mode", !f.shouldRender(100, false) && f.shouldRender(100, true));
        check("esp: opacity fades from 1.0 to minOpacity",
                near(f.opacity(0), 1.0) && near(f.opacity(100 * 100), f.minOpacity) && near(f.opacity(1e12), f.minOpacity));

        BoundedCache<Integer, String> cache = new BoundedCache<>(3);
        for (int i = 0; i < 10; i++) cache.put(i, "v" + i);
        check("cache: size is bounded", cache.size() == 3 && cache.get(0) == null && "v9".equals(cache.get(9)));
        cache.get(7);
        cache.put(100, "x");
        check("cache: least-recently-used entry is evicted first", cache.get(7) != null && cache.get(8) == null);
        cache.clear();
        check("cache: clear empties (world unload / module disable)", cache.size() == 0);
    }
}
