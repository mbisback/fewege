package client.paint.mc;

import client.paint.core.gui.Gfx;
import client.paint.core.gui.RasterGfx;
import client.paint.core.gui.Viewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Hosts the ClickGUI or the layout editor. This is the most version-sensitive file: Screen's render and input method
 * signatures changed in 1.21.9 (event records) and again for 26.x. VERIFY every @Override against your version; the
 * bodies only forward to the tested core views, so renaming is mechanical.
 */
final class PaintScreen extends Screen {
    interface View {
        void render(Gfx g, int w, int h, long now);
        void mouseMoved(double x, double y);
        boolean mouseDown(double x, double y, int button);
        void mouseDragged(double x, double y);
        void mouseUp();
        void scroll(double x, double y, double amount);
        boolean key(int code);
        boolean typed(char c);
        boolean wantsKeyboard();
        void closed();
    }

    private final View view;
    private final boolean clickGui;

    PaintScreen(View view, boolean clickGui) { super(Component.literal("Paint Client")); this.view = view; this.clickGui = clickGui; }

    boolean isClickGui() { return clickGui; }


    /** Design-space mapping for the ClickGUI (fits the 1407x840 layout into the window); identity for the editor. */
    private Viewport viewport() {
        if (!clickGui) return Viewport.identity();
        return Viewport.fit(width, height, client.paint.core.gui.ClickGuiView.DW, client.paint.core.gui.ClickGuiView.DH, PaintClient.appearance().effectiveScale());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {     // VERIFY name
        RasterGfx gfx = new RasterGfx(new McBackend(g, Minecraft.getInstance().font), width, height);
        Viewport vp = viewport();
        gfx.setViewport(vp);
        view.mouseMoved(vp.toDesignX(mouseX), vp.toDesignY(mouseY));
        view.render(gfx, width, height, System.currentTimeMillis());
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {                              // VERIFY signature
        Viewport vp = viewport();
        view.mouseDown(vp.toDesignX(e.x()), vp.toDesignY(e.y()), e.button());
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {                            // VERIFY signature
        Viewport vp = viewport();
        view.mouseDragged(vp.toDesignX(e.x()), vp.toDesignY(e.y()));
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { view.mouseUp(); return true; }                   // VERIFY signature
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {                           // VERIFY signature
        Viewport vp = viewport();
        view.scroll(vp.toDesignX(x), vp.toDesignY(y), sy);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {                                                            // VERIFY signature
        if (view.key(e.key())) return true;
        if (e.key() == 256 && !view.wantsKeyboard()) { onClose(); return true; }   // Esc closes
        return super.keyPressed(e);
    }
    @Override public boolean charTyped(CharacterEvent e) {                                                       // VERIFY signature
        return view.typed((char) e.codepoint());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { view.closed(); }
}
