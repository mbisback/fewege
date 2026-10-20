package client.paint.core.modules;

import client.paint.core.Category;
import client.paint.core.Module;
import client.paint.core.Risk;
import client.paint.core.Setting;

/** GUI appearance. Enabled = use these values; disabled = built-in defaults. Not touched by panic. */
public final class Appearance extends Module {
    public final Setting.Num guiScale = add(new Setting.Num("GUI scale", 1.0, 0.6, 1.6, 0.05));
    public final Setting.Num background = add(new Setting.Num("Background opacity", 1.0, 0.0, 1.0, 0.05));
    public final Setting.Num animation = add(new Setting.Num("Animation speed", 1.0, 0.25, 3.0, 0.25));

    public Appearance() {
        super("Appearance", "GUI scale, background opacity and animation speed. Turn off to use the defaults.", Category.MISC, Risk.SAFE);
    }

    @Override public boolean panicSafe() { return false; }

    public double effectiveScale() { return isEnabled() ? guiScale.get() : 1.0; }
    public double effectiveBackground() { return isEnabled() ? background.get() : 1.0; }
    public double effectiveAnimation() { return isEnabled() ? animation.get() : 1.0; }
}
