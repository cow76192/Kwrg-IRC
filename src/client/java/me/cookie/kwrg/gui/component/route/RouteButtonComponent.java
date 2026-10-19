package me.cookie.kwrg.gui.component.route;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.network.chat.Component;

/** Small route controls share the same hover, toggle and click colour transitions. */
public final class RouteButtonComponent {
    private int x, y, width, height;
    private Color from = Color.ofArgb(0x242C3B4B);
    private Color target = from;
    private Color current = from;
    private long transitionStart;
    private long pressedUntil;
    private boolean initialized;

    public record Bounds(int x, int y, int width, int height) {}

    public Bounds bounds() { return new Bounds(x, y, width, height); }
    public Color color() { return current; }

    public void bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void press() { this.pressedUntil = System.currentTimeMillis() + 160; }

    public void draw(OwoUIGraphics graphics, String label, boolean active, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        int desired = now < pressedUntil ? 0xCC609CC2 : active ? 0xB8357888
                : contains(mouseX, mouseY) ? 0x70445B73 : 0x302C3B4B;
        if (!initialized) {
            from = target = current = Color.ofArgb(desired);
            initialized = true;
        }
        if (target.argb() != desired) {
            from = current;
            target = Color.ofArgb(desired);
            transitionStart = now;
        }
        current = AnimationUtil.lerpColor(AnimationUtil.easeOutCubic((now - transitionStart) / 180.0f), from, target);
        RenderUtil.drawSubpixelRoundedPanel(graphics, x, y, width, height, 5,
                current, Color.ofArgb(active ? 0x8869D1D8 : 0x354FC3F7));
        RenderUtil.drawCenteredSmoothText(graphics, Component.literal(label), x + width / 2.0f,
                y + height / 2.0f, 0.80f, active ? 0xFFB2F5E7 : 0xFFD2DFE8);
    }
}
