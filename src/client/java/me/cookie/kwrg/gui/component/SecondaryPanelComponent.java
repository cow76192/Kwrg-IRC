package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.Locale;

public class SecondaryPanelComponent extends FlowLayout {

    public static final int PANEL_WIDTH = 190;
    public static final int PANEL_HEIGHT = 270;
    private static final int BG_COLOR_RGB = 0x11161F;

    private final String categoryTitle;
    private final Identifier iconTexture;
    private final StackLayout rootContainer;
    private final SmoothScrollbar scrollbar = new SmoothScrollbar();

    private float originCenterX = 0.0f;
    private float originCenterY = 0.0f;
    private float animationProgress = 0.0f;

    private TertiaryPanelComponent tertiaryPanel = null;
    private boolean tertiaryOpen = false;
    private long tertiaryAnimStartTime = 0L;
    private static final long TERTIARY_ANIM_DURATION = 250L;

    public SecondaryPanelComponent(String categoryTitle, StackLayout rootContainer) {
        super(Sizing.fixed(PANEL_WIDTH), Sizing.fixed(PANEL_HEIGHT), Algorithm.VERTICAL);
        this.categoryTitle = categoryTitle;
        this.rootContainer = rootContainer;
        this.iconTexture = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/buttons/" + categoryTitle.toLowerCase(Locale.ROOT) + ".png");

        this.positioning(Positioning.relative(50, 50));
        this.padding(Insets.of(52, 10, 10, 10));
        this.surface(Surface.BLANK);

        initItemsForCategory(categoryTitle);
    }

    public void setAnimationOrigin(float originX, float originY) {
        this.originCenterX = originX;
        this.originCenterY = originY;
    }

    public void setAnimationProgress(float progress) {
        this.animationProgress = Mth.clamp(progress, 0.0f, 1.0f);
    }

    public SmoothScrollbar getScrollbar() {
        return this.scrollbar;
    }

    public boolean handleScroll(double mouseX, double mouseY, double amount) {
        return this.scrollbar.handleMouseScroll(mouseX, mouseY, amount, getActualPanelX(), getActualPanelY(), PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public boolean isInBoundingBox(double mouseX, double mouseY) {
        return this.isPointInside(mouseX, mouseY);
    }

    private void initItemsForCategory(String categoryName) {
        this.clearChildren();
        Category cat = Category.fromName(categoryName);
        if (cat != null) {
            for (Module module : ModuleManager.getModulesByCategory(cat)) {
                this.child(module.createPanelItem(this::openTertiaryPanel));
            }
        }
    }

    public void openTertiaryPanel(PanelItemComponent item) {
        if (this.tertiaryOpen && this.tertiaryPanel != null && this.tertiaryPanel.getItemTitle().equalsIgnoreCase(item.getTitle())) {
            this.closeTertiaryPanel();
            return;
        }

        this.tertiaryOpen = true;
        this.tertiaryAnimStartTime = System.currentTimeMillis();

        if (this.tertiaryPanel != null) {
            this.rootContainer.removeChild(this.tertiaryPanel);
        }
        this.tertiaryPanel = new TertiaryPanelComponent(item.getTitle());
        this.tertiaryPanel.setAnimationOrigin(item.x() + item.width() / 2.0f, item.y() + item.height() / 2.0f);
        this.rootContainer.child(this.tertiaryPanel);
    }

    public void openTertiaryDirectly(String moduleName) {
        this.tertiaryOpen = true;
        this.tertiaryAnimStartTime = 0L; // 0L 代表已处于完成态

        int rootW = this.parent() != null ? this.parent().width() : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int rootH = this.parent() != null ? this.parent().height() : Minecraft.getInstance().getWindow().getGuiScaledHeight();
        float originX = rootW / 2.0f + 101;
        float originY = rootH / 2.0f;

        if (this.tertiaryPanel != null) {
            this.rootContainer.removeChild(this.tertiaryPanel);
        }

        this.tertiaryPanel = new TertiaryPanelComponent(moduleName);
        this.tertiaryPanel.setAnimationOrigin(originX, originY);
        this.tertiaryPanel.setAnimationProgress(1.0f); // 直接满帧可见
        this.rootContainer.child(this.tertiaryPanel);
    }

    public boolean closeTertiaryPanel() {
        if (!this.tertiaryOpen) return false;
        this.tertiaryOpen = false;
        this.tertiaryAnimStartTime = System.currentTimeMillis();
        return true;
    }

    public void destroyTertiaryImmediately() {
        if (this.tertiaryPanel != null) {
            this.rootContainer.removeChild(this.tertiaryPanel);
            this.tertiaryPanel = null;
            this.tertiaryOpen = false;
            this.tertiaryAnimStartTime = 0L;
        }
    }

    public boolean isTertiaryOpen() {
        return this.tertiaryOpen;
    }

    public boolean isTertiaryActive() {
        return this.tertiaryPanel != null;
    }

    public TertiaryPanelComponent getTertiaryPanel() {
        return this.tertiaryPanel;
    }

    public boolean isClickInsideTertiary(double mouseX, double mouseY) {
        return this.tertiaryOpen && this.tertiaryPanel != null && this.tertiaryPanel.isPointInside(mouseX, mouseY);
    }

    private float getTertiaryEase() {
        if (this.tertiaryAnimStartTime == 0L) return this.tertiaryOpen ? 1.0f : 0.0f;
        long elapsed = System.currentTimeMillis() - this.tertiaryAnimStartTime;
        float rawT = Mth.clamp((float) elapsed / TERTIARY_ANIM_DURATION, 0.0f, 1.0f);
        float progress = this.tertiaryOpen ? rawT : (1.0f - rawT);
        return RenderUtil.easeOutCubic(progress);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.tertiaryPanel != null) {
            long now = System.currentTimeMillis();
            long tElapsed = now - this.tertiaryAnimStartTime;
            float tRaw = Mth.clamp((float) tElapsed / TERTIARY_ANIM_DURATION, 0.0f, 1.0f);
            float tProgress = this.tertiaryOpen ? tRaw : (1.0f - tRaw);

            if ((tProgress <= 0.001f && !this.tertiaryOpen) || (this.animationProgress <= 0.005f)) {
                this.destroyTertiaryImmediately();
            } else {
                this.tertiaryPanel.setAnimationProgress(tProgress);
            }
        }

        if (this.animationProgress <= 0.005f) return;

        float ease = RenderUtil.easeOutCubic(this.animationProgress);
        float tertiaryEase = getTertiaryEase();

        int rootW = this.parent() != null ? this.parent().width() : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int rootH = this.parent() != null ? this.parent().height() : Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int baseCX = rootW / 2;
        int targetCX = (int) Mth.lerp(tertiaryEase, (float) baseCX, (float) (baseCX - 101));
        int targetCY = rootH / 2;

        float curCX = Mth.lerp(ease, this.originCenterX, (float) targetCX);
        float curCY = Mth.lerp(ease, this.originCenterY, (float) targetCY);
        float curW = Mth.lerp(ease, 52.0f, (float) PANEL_WIDTH);
        float curH = Mth.lerp(ease, 52.0f, (float) PANEL_HEIGHT);

        int px = Math.round(curCX - curW / 2.0f);
        int py = Math.round(curCY - curH / 2.0f);
        int width = Math.round(curW);
        int height = Math.round(curH);

        this.updateX(px);
        this.updateY(py);

        float panelAlpha = ease;
        int bgAlpha = (int) (255 * panelAlpha);
        Color panelBg = RenderUtil.withAlpha(BG_COLOR_RGB, bgAlpha);
        int outlineAlpha = (int) (85 * panelAlpha);
        Color outlineColor = RenderUtil.withAlpha(0xFFFFFF, outlineAlpha);

        RenderUtil.drawRoundedPanel(graphics, px, py, width, height, 14.0f, panelBg, outlineColor);

        graphics.enableScissor(px + 1, py + 1, px + width - 1, py + height - 1);

        int headerHeight = 44;
        int iconBadgeRadius = 13;
        int iconBadgeX = px + 22;
        int iconBadgeY = py + 22;

        Color badgeBg = RenderUtil.withAlpha(0x1D2633, (int) (255 * panelAlpha));
        Color badgeOutline = RenderUtil.withAlpha(0xFFFFFF, (int) (85 * panelAlpha));
        graphics.drawCircle(iconBadgeX, iconBadgeY, 32, iconBadgeRadius, badgeBg);
        graphics.drawRing(iconBadgeX, iconBadgeY, 32, iconBadgeRadius - 0.8, iconBadgeRadius, badgeOutline, badgeOutline);

        boolean hasIcon = Minecraft.getInstance().getResourceManager().getResource(this.iconTexture).isPresent();
        if (hasIcon) {
            int iconSize = 16;
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.iconTexture, iconBadgeX - iconSize / 2, iconBadgeY - iconSize / 2, 0, 0, iconSize, iconSize, 512, 512);
        }

        String titleStr = Character.toUpperCase(this.categoryTitle.charAt(0)) + this.categoryTitle.substring(1);
        int headerTextColor = RenderUtil.withAlpha(0xFFFFFF, (int) (255 * panelAlpha)).argb();
        RenderUtil.drawSmoothText(graphics, Component.literal(titleStr), iconBadgeX + 18, iconBadgeY - 4.5f, 1.0f, headerTextColor);

        graphics.fill(px + 12, py + headerHeight, px + width - 12, py + headerHeight + 1, ARGB.color((int) (45 * panelAlpha), 255, 255, 255));

        int trackStartY = py + headerHeight + 8;
        int trackHeight = height - headerHeight - 26;

        int totalContentH = 0;
        for (int i = 0; i < this.children.size(); i++) {
            totalContentH += this.children.get(i).height();
            if (i > 0) totalContentH += 4;
        }

        this.scrollbar.update(totalContentH, trackHeight, delta);
        float currentScroll = this.scrollbar.getScrollOffset();

        int curY = (int) (trackStartY - currentScroll);
        for (int i = 0; i < this.children.size(); i++) {
            var child = this.children.get(i);
            child.updateX(px + 10);
            child.updateY(curY);
            curY += child.height() + 4;
        }

        graphics.enableScissor(px + 6, trackStartY, px + width - 6, py + height - 8);

        if (ease > 0.35f) {
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        }

        graphics.disableScissor();

        if (trackHeight > 30) {
            this.scrollbar.draw(graphics, px + width - 5, trackStartY, trackHeight, mouseX, mouseY, panelAlpha, delta);
        }

        graphics.disableScissor();
    }

    public int getActualPanelX() {
        int rootW = this.parent() != null ? this.parent().width() : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int baseCX = rootW / 2;
        float tertiaryEase = getTertiaryEase();
        int targetCX = (int) Mth.lerp(tertiaryEase, (float) baseCX, (float) (baseCX - 101));
        return targetCX - PANEL_WIDTH / 2;
    }

    public int getActualPanelY() {
        int rootH = this.parent() != null ? this.parent().height() : Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return (rootH - PANEL_HEIGHT) / 2;
    }

    public boolean isPointInside(double mouseX, double mouseY) {
        int px = getActualPanelX();
        int py = getActualPanelY();
        return mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= py && mouseY <= py + PANEL_HEIGHT;
    }
}