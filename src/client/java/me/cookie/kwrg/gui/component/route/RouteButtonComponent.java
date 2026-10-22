package me.cookie.kwrg.gui.component.route;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.BaseGuiComponent;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.network.chat.Component;

/** Small route controls share the same hover, toggle and click colour transitions. */
public final class RouteButtonComponent {
    private int x, y, width, height;
    private Color from = Color.ofArgb(AnimationUtil.argb(BaseGuiComponent.cardBg(), 0x24));
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
        int desired = now < pressedUntil ? AnimationUtil.argb(BaseGuiComponent.panelOutline(), 0xCC) : active ? AnimationUtil.argb(BaseGuiComponent.panelOutline(), 0xB8)
                : contains(mouseX, mouseY) ? AnimationUtil.argb(BaseGuiComponent.cardBorder(), 0x70) : AnimationUtil.argb(BaseGuiComponent.cardBg(), 0x30);
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
                current, Color.ofArgb(active ? AnimationUtil.argb(BaseGuiComponent.accentSoft(), 0x88) : AnimationUtil.argb(BaseGuiComponent.panelOutline(), 0x35)));
        RenderUtil.drawCenteredSmoothText(graphics, Component.literal(label), x + width / 2.0f,
                y + height / 2.0f, 0.80f, active ? AnimationUtil.opaqueArgb(BaseGuiComponent.textStrong()) : AnimationUtil.opaqueArgb(BaseGuiComponent.textBody()));
    }
}
