package client.paint.core;

import java.util.function.Predicate;

/**
 * A validated, persistable module setting. {@link #set} rejects invalid values and leaves the
 * current value untouched; {@link #parse} does the same for text read from the config file.
 */
public abstract class Setting<T> {
    public final String name;
    protected final T defaultValue;
    protected T value;
    private final Predicate<T> validator;

    protected Setting(String name, T defaultValue, Predicate<T> validator) {
        if (!validator.test(defaultValue)) {
            throw new IllegalArgumentException("Invalid default for setting " + name);
        }
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;
    }

    public T get() { return value; }
    public float getFloat() { return ((Number) value).floatValue(); }
    public T getDefault() { return defaultValue; }

    /** @return true if the value was accepted. */
    public boolean set(T v) {
        if (v == null || !validator.test(v)) return false;
        value = v;
        return true;
    }

    public void reset() { value = defaultValue; }

    public abstract String serialize();

    /** @return true if the text was valid and applied. */
    public abstract boolean parse(String text);

    public static final class Bool extends Setting<Boolean> {
        public Bool(String name, boolean def) { super(name, def, v -> true); }
        @Override public String serialize() { return Boolean.toString(value); }
        @Override public boolean parse(String t) {
            if ("true".equalsIgnoreCase(t)) return set(true);
            if ("false".equalsIgnoreCase(t)) return set(false);
            return false;
        }
    }

    public static final class Num extends Setting<Double> {
        public final double min, max, step;
        public Num(String name, double def, double min, double max, double step) {
            super(name, def, v -> !v.isNaN() && v >= min && v <= max);
            this.min = min; this.max = max; this.step = step;
        }
        /** UI helper: clamps to range instead of rejecting (sliders can't leave the range anyway). */
        public void setClamped(double v) {
            if (Double.isNaN(v)) return;
            set(Math.max(min, Math.min(max, v)));
        }
        @Override public String serialize() { return Double.toString(value); }
        @Override public boolean parse(String t) {
            try { return set(Double.parseDouble(t.trim())); }
            catch (NumberFormatException e) { return false; }
        }
    }

    public static final class Choice<E extends Enum<E>> extends Setting<E> {
        private final Class<E> type;
        public Choice(String name, Class<E> type, E def) { super(name, def, v -> true); this.type = type; }
        /** Step to the next (dir=+1) or previous (dir=-1) constant, wrapping around. */
        public void cycle(int dir) {
            E[] all = type.getEnumConstants();
            value = all[(value.ordinal() + dir % all.length + all.length) % all.length];
        }
        /** Text shown in the GUI (the constant's toString, so enums can supply pretty names). */
        public String display() { return value.toString(); }
        @Override public String serialize() { return value.name(); }
        @Override public boolean parse(String t) {
            try { return set(Enum.valueOf(type, t.trim())); }
            catch (IllegalArgumentException e) { return false; }
        }
    }

    /** ARGB colour, persisted as #AARRGGBB. */
    public static final class Color extends Setting<Integer> {
        public static final int[] PRESETS = {0xFFFF6E5A, 0xFFFBB63E, 0xFFA1E83A, 0xFF40D9E3, 0xFF8E7BFA, 0xFFF974B3, 0xFFFFFFFF, 0xFF5EEAD4};
        public Color(String name, int def) { super(name, def, v -> true); }
        public int argb() { return value; }
        public void setRgb(int rgb) { set(0xFF000000 | (rgb & 0xFFFFFF)); }
        @Override public String serialize() { return String.format("#%08X", value); }
        @Override public boolean parse(String t) {
            String h = t == null ? "" : t.trim();
            if (h.startsWith("#")) h = h.substring(1);
            try {
                if (h.length() == 6) return set(0xFF000000 | Integer.parseInt(h, 16));
                if (h.length() == 8) return set((int) Long.parseLong(h, 16));
            } catch (NumberFormatException ignored) { }
            return false;
        }
    }

    /** Short free text (single line, no control characters). */
    public static final class Text extends Setting<String> {
        public final int maxLen;
        public Text(String name, String def, int maxLen) {
            super(name, def, v -> v.length() <= maxLen && v.chars().noneMatch(Character::isISOControl));
            this.maxLen = maxLen;
        }
        @Override public String serialize() { return value; }
        @Override public boolean parse(String t) { return t != null && set(t); }
    }
}
