package client.paint.core.gui;

/** GLFW key codes used by the GUI plus a readable name for keybind chips. */
public final class GuiKeys {
    private GuiKeys() {}

    public static final int ESCAPE = 256, ENTER = 257, BACKSPACE = 259, DELETE = 261;

    public static String name(int code) {
        if (code < 0) return "—";
        if (code >= 65 && code <= 90) return String.valueOf((char) code);
        if (code >= 48 && code <= 57) return String.valueOf((char) code);
        if (code >= 290 && code <= 301) return "F" + (code - 289);
        switch (code) {
            case 32: return "Space";
            case 262: return "→";
            case 263: return "←";
            case 264: return "↓";
            case 265: return "↑";
            case 258: return "Tab";
            case 257: return "Enter";
            case 340: case 344: return "Shift";
            case 341: case 345: return "Ctrl";
            case 342: case 346: return "Alt";
            case 91: return "[";
            case 93: return "]";
            case 59: return ";";
            case 39: return "'";
            case 44: return ",";
            case 46: return ".";
            case 47: return "/";
            case 92: return "\\";
            case 45: return "-";
            case 61: return "=";
            case 96: return "`";
            default: return "K" + code;
        }
    }
}
