package me.cookie.kwrg.gui;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;

public abstract class OverlayScreen {

    protected static final long ANIMATION_MS = 250;

    protected final IrcScreen host;
    protected final PanelItemComponent originComponent;
    protected final float originX, originY;
    protected final long openedAt = System.currentTimeMillis();

    protected long closingAt = -1;
    protected float closingFrom;

    protected int panelX, panelY, panelW, panelH;
    protected float morphOriginX, morphOriginY, morphScaleX, morphScaleY;

    protected OverlayScreen(IrcScreen host, PanelItemComponent originComponent, float originX, float originY) {
        this.host = host;
        this.originComponent = originComponent;
        this.originX = originX;
        this.originY = originY;
    }

    public float progress() {
        long now = System.currentTimeMillis();
        return closingAt < 0 ? AnimationUtil.easeOutCubic((now - openedAt) / (float) ANIMATION_MS)
                : closingFrom * (1 - AnimationUtil.easeOutCubic((now - closingAt) / (float) ANIMATION_MS));
    }

    public boolean isFinished() {
        return closingAt >= 0 && System.currentTimeMillis() - closingAt >= ANIMATION_MS;
    }

    protected boolean revealed() {
        return closingAt < 0 && System.currentTimeMillis() - openedAt >= ANIMATION_MS;
    }

    protected final void closeOverlay() {
        if (!beforeClose()) return;
        closingFrom = progress();
        closingAt = System.currentTimeMillis();
    }

    protected boolean beforeClose() {
        return true;
    }

    public final void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int width, int height) {
        beforeLayout(width, height, mouseX, mouseY);
        layout(width, height);

        float alpha = progress();
        float sourceX = originX, sourceY = originY, sourceW = 52, sourceH = 28;

        if (originComponent != null && originComponent.parent() != null) {
            float scale = host.getAnimatedScale();
            sourceX = width / 2.0f + (originComponent.x() + originComponent.width() / 2.0f - width / 2.0f) * scale;
            sourceY = height / 2.0f + (originComponent.y() + originComponent.height() / 2.0f - height / 2.0f) * scale;
            sourceW = originComponent.width() * scale;
            sourceH = originComponent.height() * scale;
        }

        float w = AnimationUtil.lerp(alpha, sourceW, panelW), h = AnimationUtil.lerp(alpha, sourceH, panelH);
        float cx = AnimationUtil.lerp(alpha, sourceX, width / 2.0f), cy = AnimationUtil.lerp(alpha, sourceY, height / 2.0f);

        morphScaleX = w / panelW;
        morphScaleY = h / panelH;
        morphOriginX = cx - w / 2;
        morphOriginY = cy - h / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx - w / 2, cy - h / 2);
        graphics.pose().scale(w / panelW, h / panelH);
        graphics.pose().translate(-panelX, -panelY);

        OwoUIGraphics owo = RenderUtil.withOpacity(OwoUIGraphics.of(graphics), alpha * GuiThemeService.opacity());
        RenderUtil.drawSubpixelRoundedPanel(owo, panelX, panelY, panelW, panelH, 14,
                AnimationUtil.withAlpha(GuiThemeService.panelBg(), 0xF5), AnimationUtil.withAlpha(GuiThemeService.accent(), 0x80));

        if (alpha > 0.03f) {
            renderContent(owo, graphics, mouseX, mouseY, partialTick, alpha);
        }

        graphics.pose().popMatrix();
    }

    protected void beforeLayout(int width, int height, int mouseX, int mouseY) {
    }

    protected abstract void layout(int width, int height);

    protected abstract void renderContent(OwoUIGraphics owo, GuiGraphicsExtractor graphics,
                                          int mouseX, int mouseY, float partialTick, float alpha);

    public abstract void mouseClicked(MouseButtonEvent click, boolean doubled);

    public abstract void mouseReleased(MouseButtonEvent click);

    public abstract void mouseDragged(MouseButtonEvent click, double deltaX, double deltaY);

    public abstract void mouseScrolled(double x, double y, double amount);

    public abstract void keyPressed(KeyEvent input);

    public abstract void charTyped(CharacterEvent input);

    public abstract boolean preeditUpdated(PreeditEvent input);

    public abstract boolean flush();
}
