package client.paint.core.nametag;

/**
 * Local-only Self Nametag style: validation, text resolution and gradient colouring. Purely cosmetic
 * and client-side; other players never see it. Rendering hooks consume {@link #resolveText} and
 * {@link #colorAt}. Defaults mean "look exactly like vanilla" when the module is disabled or empty.
 */
public final class NametagStyle {
    public static final int MAX_TEXT = 48;

    public String customText = "";        // empty = use the real username
    public int colorStart = 0xFFFFFFFF;   // ARGB
    public int colorEnd = 0xFFFFFFFF;     // equal to start = solid colour
    public int backgroundArgb = 0x40000000;
    public boolean shadow = true, bold = false, outline = false;
    public float scale = 1.0f;            // 0.5 .. 3.0
    public int paddingX = 2, paddingY = 1; // 0 .. 16
    public String prefixSymbol = "", suffixSymbol = "";
    public boolean showInFirstPerson = false; // vanilla never draws your own tag in first person
    public boolean showInThirdPerson = true;

    public void reset() {
        NametagStyle d = new NametagStyle();
        customText = d.customText; colorStart = d.colorStart; colorEnd = d.colorEnd;
        backgroundArgb = d.backgroundArgb; shadow = d.shadow; bold = d.bold; outline = d.outline;
        scale = d.scale; paddingX = d.paddingX; paddingY = d.paddingY;
        prefixSymbol = d.prefixSymbol; suffixSymbol = d.suffixSymbol;
        showInFirstPerson = d.showInFirstPerson; showInThirdPerson = d.showInThirdPerson;
    }

    public void setScale(float s) { if (!Float.isNaN(s)) scale = Math.max(0.5f, Math.min(3.0f, s)); }
    public void setPadding(int x, int y) { paddingX = Math.max(0, Math.min(16, x)); paddingY = Math.max(0, Math.min(16, y)); }

    /** Final text shown. Strips control/format characters, limits length, falls back to the real name. */
    public String resolveText(String realUsername) {
        String core = sanitize(customText);
        if (core.isEmpty()) core = realUsername == null ? "" : realUsername;
        String s = sanitize(prefixSymbol) + core + sanitize(suffixSymbol);
        return s.length() > MAX_TEXT + 8 ? s.substring(0, MAX_TEXT + 8) : s;
    }

    /** Colour for character {@code i} of {@code n} (gradient start to end, per-channel linear). */
    public int colorAt(int i, int n) {
        if (n <= 1 || colorStart == colorEnd) return colorStart;
        double t = Math.max(0, Math.min(1, i / (double) (n - 1)));
        return lerp(colorStart, colorEnd, t);
    }

    public boolean visibleIn(boolean firstPerson) { return firstPerson ? showInFirstPerson : showInThirdPerson; }

    /** Parse "#RRGGBB" or "#AARRGGBB". @return ARGB, or {@code fallback} if invalid. */
    public static int parseColor(String s, int fallback) {
        if (s == null) return fallback;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException ignored) { }
        return fallback;
    }

    static int lerp(int a, int b, double t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < MAX_TEXT; i++) {
            char c = s.charAt(i);
            if (c == '§') { i++; continue; } // legacy format code: drop the section sign AND its code letter
            if (Character.isISOControl(c)) continue;
            sb.append(c);
        }
        return sb.toString().strip();
    }
}
