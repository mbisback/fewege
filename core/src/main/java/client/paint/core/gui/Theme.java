package client.paint.core.gui;

import client.paint.core.Category;
import client.paint.core.Risk;

/** Colours sampled from the two reference screenshots (ARGB). */
public final class Theme {
    private Theme() {}

    public static final int BG_BASE = 0xFF0A0B0F;
    public static final int GLOW_TEAL = 0xFF0E3F4A;
    public static final int GLOW_PURPLE = 0xFF53276C;

    public static final int PANEL_FILL = 0xF20D0F13;
    public static final int PANEL_BORDER = 0xFF1C2026;
    public static final int DIVIDER = 0xFF171B20;
    public static final int PILL_FILL = 0xFF14171C;
    public static final int PILL_BORDER = 0xFF23272E;
    public static final int CHIP_FILL = 0xFF23262C;

    public static final int TEXT = 0xFFE9EBEF;
    public static final int MUTED = 0xFF9A9EA6;
    public static final int DIM = 0xFF6F747C;

    public static final int TOGGLE_OFF = 0xFF2C2F36;
    public static final int KNOB_OFF = 0xFF8E919A;
    public static final int KNOB_ON = 0xFF111318;
    public static final int TRACK = 0xFF2A2D33;

    public static final int CORAL = 0xFFFF6E5A;
    public static final int AMBER = 0xFFFBB63E;
    public static final int CYAN = 0xFF40D9E3;
    public static final int VIOLET = 0xFF8E7BFA;
    public static final int PINK = 0xFFF974B3;
    public static final int LIME = 0xFFA1E83A;
    public static final int TEAL = 0xFF5EEAD4;

    public static int categoryColor(Category c) {
        switch (c) {
            case COMBAT: return CORAL;
            case MOVEMENT: return AMBER;
            case RENDER: return CYAN;
            case BASE_HUNT: return VIOLET;
            case DONUT: return PINK;
            default: return LIME;
        }
    }

    public static int badgeText(Risk r) {
        switch (r) {
            case SAFE: return 0xFF5FE3C9;
            case LOW: return 0xFFA8E03C;
            case MED: return 0xFFF0A15E;
            default: return 0xFFF2A0A6;
        }
    }

    public static int badgeFill(Risk r) {
        switch (r) {
            case SAFE: return 0xFF123B3A;
            case LOW: return 0xFF243016;
            case MED: return 0xFF3B2716;
            default: return 0xFF3A1B21;
        }
    }
}
