package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.module.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class RadialMenuComponent extends BaseUIComponent {

    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/logo.png");
    private static final long EXPAND_DURATION = 500L;
    private static final double BIG_RING_RADIUS = 108.0;
    private static final long PANEL_TRANSITION_DURATION = 250L;

    private final StackLayout rootContainer;
    private final long openTime = System.currentTimeMillis();
    private final List<CircleButtonComponent> buttons = new ArrayList<>();

    private boolean panelOpen = false;
    private long panelAnimStartTime = 0L;
    private CircleButtonComponent selectedButton = null;
    private SecondaryPanelComponent secondaryPanel = null;

    public RadialMenuComponent(IrcScreen parentScreen, StackLayout rootContainer) {
        this.rootContainer = rootContainer;
        this.sizing(Sizing.fixed(1), Sizing.fixed(1));

        for (Category category : Category.values()) {
            buttons.add(new CircleButtonComponent(category.getName(), this::openSecondaryPanel));
        }

        for (var btn : buttons) {
            rootContainer.child(btn);
        }
    }

    public void openSecondaryPanel(CircleButtonComponent button) {
        if (this.panelOpen) return;
        this.selectedButton = button;
        this.panelOpen = true;
        this.panelAnimStartTime = System.currentTimeMillis();

        if (this.secondaryPanel != null) {
            this.rootContainer.removeChild(this.secondaryPanel);
        }

        this.secondaryPanel = new SecondaryPanelComponent(button.getTitle(), this.rootContainer);
        this.secondaryPanel.setAnimationOrigin(button.getExactCenterX(), button.getExactCenterY());
        this.rootContainer.child(this.secondaryPanel);
    }

    public boolean closeSecondaryPanel() {
        if (!this.panelOpen) return false;
        if (this.secondaryPanel != null && this.secondaryPanel.isTertiaryOpen()) {
            this.secondaryPanel.closeTertiaryPanel();
        }

        this.panelOpen = false;
        this.panelAnimStartTime = System.currentTimeMillis();
        return true;
    }

    public boolean closeTertiaryPanel() {
        if (this.secondaryPanel != null && this.secondaryPanel.isTertiaryOpen()) {
            return this.secondaryPanel.closeTertiaryPanel();
        }
        return false;
    }

    public boolean isTertiaryOpen() {
        return this.secondaryPanel != null && this.secondaryPanel.isTertiaryOpen();
    }

    public boolean isPanelOpen() {
        return this.panelOpen;
    }

    public SecondaryPanelComponent getSecondaryPanel() {
        return this.secondaryPanel;
    }

    public boolean isClickInsidePanel(double mouseX, double mouseY) {
        if (!this.panelOpen || this.secondaryPanel == null) return false;
        return this.secondaryPanel.isPointInside(mouseX, mouseY);
    }

    public boolean isClickInsideTertiary(double mouseX, double mouseY) {
        if (!this.panelOpen || this.secondaryPanel == null) return false;
        return this.secondaryPanel.isClickInsideTertiary(mouseX, mouseY);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        long now = System.currentTimeMillis();

        long mainElapsed = now - openTime;
        float mainProgress = (float) mainElapsed / EXPAND_DURATION;
        float mainEase = RenderUtil.easeOutCubic(mainProgress);

        float panelProgress;
        if (this.panelAnimStartTime == 0L) {
            panelProgress = this.panelOpen ? 1.0f : 0.0f;
        } else {
            float elapsed = (float) (now - this.panelAnimStartTime) / PANEL_TRANSITION_DURATION;
            float rawT = Mth.clamp(elapsed, 0.0f, 1.0f);
            panelProgress = this.panelOpen ? rawT : (1.0f - rawT);
        }
        float panelEase = RenderUtil.easeOutCubic(panelProgress);

        int cx = this.parent() != null ? this.parent().width() / 2 : this.x();
        int cy = this.parent() != null ? this.parent().height() / 2 : this.y();

        float menuAlpha = (1.0f - panelEase) * mainEase;

        if (menuAlpha > 0.01f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) cx, (float) cy);
            graphics.pose().scale(mainEase, mainEase);

            renderCenterLogo(graphics, menuAlpha);

            int nodeCount = buttons.size();
            double stepDeg = 360.0 / nodeCount;
            double gapDeg = Math.toDegrees(Math.asin(30.0 / BIG_RING_RADIUS));

            int ringAlpha = (int) (255 * 0.35f * menuAlpha);
            Color ringPeak = RenderUtil.withAlpha(0xFFFFFF, ringAlpha);
            Color ringFade = RenderUtil.withAlpha(0xFFFFFF, 0);

            for (int i = 0; i < nodeCount; i++) {
                double a1 = 90.0 + i * stepDeg + gapDeg;
                double a2 = 90.0 + (i + 1) * stepDeg - gapDeg;

                RenderUtil.drawAAArc(graphics, 0, 0, a1, a2, BIG_RING_RADIUS, ringPeak, ringFade);
            }

            graphics.pose().popMatrix();
        }

        double startAngleRad = -Math.PI / 2.0;
        double stepAngleRad = Math.PI * 2.0 / buttons.size();
        float styleTopX = (float) cx;
        float styleTopY = (float) (cy - BIG_RING_RADIUS * mainEase);

        for (int i = 0; i < buttons.size(); i++) {
            CircleButtonComponent btn = buttons.get(i);
            double angle = startAngleRad + i * stepAngleRad;
            double curRadius = BIG_RING_RADIUS * mainEase;

            float radialX = (float) (cx + Math.cos(angle) * curRadius);
            float radialY = (float) (cy + Math.sin(angle) * curRadius);

            if (this.selectedButton == btn) {
                float animX = Mth.lerp(panelEase, radialX, styleTopX);
                float animY = Mth.lerp(panelEase, radialY, styleTopY);

                btn.setExactCenter(animX, animY);
                btn.setVisualAlpha(1.0f - panelEase);
                btn.moveTo((int) Math.round(animX - btn.width() / 2.0f), (int) Math.round(animY - btn.height() / 2.0f));
            } else {
                btn.setExactCenter(radialX, radialY);
                btn.setVisualAlpha(1.0f - panelEase);
                btn.moveTo((int) Math.round(radialX - btn.width() / 2.0f), (int) Math.round(radialY - btn.height() / 2.0f));
            }
        }

        if (this.secondaryPanel != null) {
            if (panelProgress <= 0.001f && !this.panelOpen) {
                this.rootContainer.removeChild(this.secondaryPanel);
                this.secondaryPanel = null;
                this.panelAnimStartTime = 0L;
            } else {
                this.secondaryPanel.setAnimationProgress(panelProgress);
            }
        }
    }

    private void renderCenterLogo(OwoUIGraphics graphics, float alpha) {
        if (alpha <= 0.01f) return;

        boolean hasTexture = false;
        try {
            hasTexture = Minecraft.getInstance().getResourceManager().getResource(LOGO_TEXTURE).isPresent();
        } catch (Throwable ignored) {}

        if (hasTexture) {
            int size = 52;
            graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, -size / 2, -size / 2, 0.0f, 0.0f, size, size, size, size);
        } else {
            var font = Minecraft.getInstance().font;
            String text = "K";
            float textW = font.width(text);
            float textH = font.lineHeight;
            int color = ARGB.color((int) (255 * alpha), 255, 255, 255);

            graphics.drawText(Component.literal(text), -textW / 2.0f, -textH / 2.0f, 2.4f, color, OwoUIGraphics.TextAnchor.TOP_LEFT);
        }
    }
}