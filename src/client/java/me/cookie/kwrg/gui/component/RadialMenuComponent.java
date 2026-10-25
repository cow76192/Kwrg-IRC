package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.cookie.kwrg.gui.IrcScreen;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.module.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class RadialMenuComponent extends BaseGuiComponent {

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
    private boolean waitingForTertiaryToClose = false;

    public RadialMenuComponent(IrcScreen parentScreen, StackLayout rootContainer) {
        this.rootContainer = rootContainer;
        this.sizing(Sizing.fixed(1), Sizing.fixed(1));

        for (Category category : Category.values()) {
            CircleButtonComponent btn = new CircleButtonComponent(category.getName(), this::openSecondaryPanel);
            btn.setVisualAlpha(0.0f);
            btn.setExactCenter(-2000.0f, -2000.0f);
            btn.moveTo(-2000, -2000);
            buttons.add(btn);
        }

        for (var btn : buttons) {
            rootContainer.child(btn);
        }
    }

    public void openSecondaryPanel(CircleButtonComponent button) {
        if (this.panelOpen && !this.waitingForTertiaryToClose) return;
        this.waitingForTertiaryToClose = false;
        this.selectedButton = button;
        this.panelOpen = true;
        this.panelAnimStartTime = System.currentTimeMillis();

        if (this.secondaryPanel != null) {
            this.secondaryPanel.destroyTertiaryImmediately();
            this.rootContainer.removeChild(this.secondaryPanel);
        }

        this.secondaryPanel = new SecondaryPanelComponent(button.getTitle(), this.rootContainer);
        this.secondaryPanel.setAnimationOrigin(button.getExactCenterX(), button.getExactCenterY());
        this.rootContainer.child(this.secondaryPanel);
    }

    public boolean closeSecondaryPanel() {
        if (!this.panelOpen && !this.waitingForTertiaryToClose) return false;

        if (this.secondaryPanel != null && this.secondaryPanel.isTertiaryActive()) {
            if (this.secondaryPanel.isTertiaryOpen()) {
                this.secondaryPanel.closeTertiaryPanel();
            }
            this.waitingForTertiaryToClose = true;
            return true;
        }

        this.waitingForTertiaryToClose = false;
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

    public List<CircleButtonComponent> getChildren() {
        return this.buttons;
    }

    public void openDirectly(String categoryName, String moduleName) {
        this.waitingForTertiaryToClose = false;

        CircleButtonComponent targetBtn = null;
        for (CircleButtonComponent btn : this.buttons) {
            if (categoryName.equalsIgnoreCase(btn.getTitle())) {
                targetBtn = btn;
                break;
            }
        }
        if (targetBtn == null) return;

        this.selectedButton = targetBtn;
        this.panelOpen = true;
        this.panelAnimStartTime = 0L;

        int rootW = this.parent() != null ? this.parent().width() : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int rootH = this.parent() != null ? this.parent().height() : Minecraft.getInstance().getWindow().getGuiScaledHeight();
        float originX = rootW / 2.0f;
        float originY = rootH / 2.0f;

        if (this.secondaryPanel != null) {
            this.secondaryPanel.destroyTertiaryImmediately();
            this.rootContainer.removeChild(this.secondaryPanel);
        }

        this.secondaryPanel = new SecondaryPanelComponent(targetBtn.getTitle(), this.rootContainer);
        this.secondaryPanel.setAnimationOrigin(originX, originY);
        this.secondaryPanel.setAnimationProgress(1.0f);
        this.rootContainer.child(this.secondaryPanel);

        if (moduleName != null) {
            this.secondaryPanel.openTertiaryDirectly(moduleName);
        }
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics = RenderUtil.withOpacity(graphics, GuiThemeService.opacity());
        long now = System.currentTimeMillis();

        if (this.waitingForTertiaryToClose) {
            if (this.secondaryPanel == null || !this.secondaryPanel.isTertiaryActive()) {
                this.waitingForTertiaryToClose = false;
                this.panelOpen = false;
                this.panelAnimStartTime = System.currentTimeMillis();
            }
        }

        long mainElapsed = now - openTime;
        float mainProgress = (float) mainElapsed / EXPAND_DURATION;
        float mainEase = AnimationUtil.easeOutCubic(mainProgress);

        float panelProgress;
        if (this.panelAnimStartTime == 0L) {
            panelProgress = this.panelOpen ? 1.0f : 0.0f;
        } else {
            float elapsed = (float) (now - this.panelAnimStartTime) / PANEL_TRANSITION_DURATION;
            float rawT = Mth.clamp(elapsed, 0.0f, 1.0f);
            panelProgress = this.panelOpen ? rawT : (1.0f - rawT);
        }
        float panelEase = AnimationUtil.easeOutCubic(panelProgress);

        var client = Minecraft.getInstance();
        int screenW = client.screen != null ? client.screen.width : (this.parent() != null ? this.parent().width() : client.getWindow().getGuiScaledWidth());
        int screenH = client.screen != null ? client.screen.height : (this.parent() != null ? this.parent().height() : client.getWindow().getGuiScaledHeight());

        int cx = screenW / 2;
        int cy = screenH / 2;

        double maxSafeRadius = Math.max(45.0, (double) cy - 36.0);
        double actualRingRadius = Math.min(BIG_RING_RADIUS, maxSafeRadius);

        float menuAlpha = (1.0f - panelEase) * mainEase;

        if (menuAlpha > 0.01f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) cx, (float) cy);
            graphics.pose().scale(mainEase, mainEase);

            renderCenterLogo(graphics, menuAlpha);

            int nodeCount = buttons.size();
            double stepDeg = 360.0 / nodeCount;
            double gapDeg = Math.toDegrees(Math.asin(30.0 / actualRingRadius));

            int ringAlpha = (int) (255 * 0.35f * menuAlpha);
            Color ringPeak = AnimationUtil.withAlpha(this.textStrong(), ringAlpha);
            Color ringFade = AnimationUtil.withAlpha(this.textStrong(), 0);

            for (int i = 0; i < nodeCount; i++) {
                double a1 = 90.0 + i * stepDeg + gapDeg;
                double a2 = 90.0 + (i + 1) * stepDeg - gapDeg;

                RenderUtil.drawAAArc(graphics, 0, 0, a1, a2, actualRingRadius, ringPeak, ringFade);
            }

            graphics.pose().popMatrix();
        }

        double startAngleRad = -Math.PI / 2.0;
        double stepAngleRad = Math.PI * 2.0 / buttons.size();

        float styleTopX = (float) cx;
        float styleTopY = (float) (cy - actualRingRadius * mainEase);

        for (int i = 0; i < buttons.size(); i++) {
            CircleButtonComponent btn = buttons.get(i);
            double angle = startAngleRad + i * stepAngleRad;
            double curRadius = actualRingRadius * mainEase;

            float radialX = (float) (cx + Math.cos(angle) * curRadius);
            float radialY = (float) (cy + Math.sin(angle) * curRadius);

            if (this.selectedButton == btn) {
                float animX = Mth.lerp(panelEase, radialX, styleTopX);
                float animY = Mth.lerp(panelEase, radialY, styleTopY);

                btn.setExactCenter(animX, animY);
                btn.setVisualAlpha(1.0f - panelEase);
                btn.moveTo(Math.round(animX - btn.width() / 2.0f), Math.round(animY - btn.height() / 2.0f));
            } else {
                btn.setExactCenter(radialX, radialY);
                btn.setVisualAlpha(1.0f - panelEase);
                btn.moveTo(Math.round(radialX - btn.width() / 2.0f), Math.round(radialY - btn.height() / 2.0f));
            }
        }

        if (this.secondaryPanel != null) {
            if (panelProgress <= 0.001f && !this.panelOpen) {
                this.secondaryPanel.destroyTertiaryImmediately();
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
            int color = AnimationUtil.argb(this.textStrong(), (int) (255 * alpha));

            graphics.drawText(Component.literal(text), -textW / 2.0f, -textH / 2.0f, 2.4f, color, OwoUIGraphics.TextAnchor.TOP_LEFT);
        }
    }
}