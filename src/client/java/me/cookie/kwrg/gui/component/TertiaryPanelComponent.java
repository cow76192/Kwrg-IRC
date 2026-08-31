package me.cookie.kwrg.gui.component;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.module.ModuleManager;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class TertiaryPanelComponent extends FlowLayout {

    public static final int PANEL_WIDTH = 190;
    public static final int PANEL_HEIGHT = 315;
    private static final int BG_COLOR_RGB = 0x11161F;

    private final String itemTitle;
    private final SmoothScrollbar scrollbar = new SmoothScrollbar();

    private float originCenterX = 0.0f;
    private float originCenterY = 0.0f;
    private float animationProgress = 0.0f;

    public TertiaryPanelComponent(String itemTitle) {
        super(Sizing.fixed(PANEL_WIDTH), Sizing.fixed(PANEL_HEIGHT), Algorithm.VERTICAL);
        this.itemTitle = itemTitle;

        this.positioning(Positioning.relative(50, 50));
        this.padding(Insets.of(52, 10, 10, 10));
        this.surface(Surface.BLANK);

        initContentForSetting(itemTitle);
    }

    public void setAnimationOrigin(float originX, float originY) {
        this.originCenterX = originX;
        this.originCenterY = originY;
    }

    public void setAnimationProgress(float progress) {
        this.animationProgress = Mth.clamp(progress, 0.0f, 1.0f);
    }

    public String getItemTitle() {
        return this.itemTitle;
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

    private void initContentForSetting(String title) {
        this.clearChildren();
        Module module = ModuleManager.getModule(title);
        if (module != null) {
            module.buildTertiaryUI(this);
        }
    }

    public static int setGlowColor(String colorStr) {
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        boolean isSpecial = colorStr.toLowerCase().contains("rainbow") || colorStr.toLowerCase().contains("gradient");
        if (c == null && !isSpecial) {
            ChatNotifier.send("§c[IRC] 无法解析的颜色格式！支持 #HEX, rgb(...), hsl(...)");
            return 0;
        }

        IrcConfig config = IrcConfig.get();
        config.glowColor = colorStr;
        IrcConfig.save();

        System.out.println("[KWRG Config] 实时更新 glowColor -> " + colorStr);
        return 1;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.animationProgress <= 0.005f) return;

        float ease = RenderUtil.easeOutCubic(this.animationProgress);

        int rootW = this.parent() != null ? this.parent().width() : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int rootH = this.parent() != null ? this.parent().height() : Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int targetCX = rootW / 2 + 101;
        int targetCY = rootH / 2;

        float curCX = Mth.lerp(ease, this.originCenterX, (float) targetCX);
        float curCY = Mth.lerp(ease, this.originCenterY, (float) targetCY);
        float curW = Mth.lerp(ease, 52.0f, (float) PANEL_WIDTH);
        float curH = Mth.lerp(ease, 28.0f, (float) PANEL_HEIGHT);

        int px = (int) Math.round(curCX - curW / 2.0f);
        int py = (int) Math.round(curCY - curH / 2.0f);
        int width = (int) Math.round(curW);
        int height = (int) Math.round(curH);

        this.updateX(px);
        this.updateY(py);

        // 1. 底盘与描边
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

        if (ease > 0.25f) {
            float contentAlpha = (ease - 0.25f) / 0.75f;

            Color badgeBg = RenderUtil.withAlpha(0x1D2633, (int) (255 * contentAlpha));
            Color badgeOutline = RenderUtil.withAlpha(0xFFFFFF, (int) (85 * contentAlpha));
            graphics.drawCircle(iconBadgeX, iconBadgeY, 32, iconBadgeRadius, badgeBg);
            graphics.drawRing(iconBadgeX, iconBadgeY, 32, iconBadgeRadius - 0.8, iconBadgeRadius, badgeOutline, badgeOutline);

            String initialChar = this.itemTitle.isEmpty() ? "•" : this.itemTitle.substring(0, 1).toUpperCase();
            var font = Minecraft.getInstance().font;
            float initialW = font.width(initialChar);
            int badgeTextColor = RenderUtil.withAlpha(0xFFFFFF, (int) (255 * contentAlpha)).argb();
            RenderUtil.drawSmoothText(graphics, Component.literal(initialChar), iconBadgeX - initialW / 2.0f, iconBadgeY - 4.5f, 1.0f, badgeTextColor);

            int headerTextColor = RenderUtil.withAlpha(0xFFFFFF, (int) (255 * contentAlpha)).argb();
            RenderUtil.drawSmoothText(graphics, Component.literal(this.itemTitle), iconBadgeX + 18, iconBadgeY - 4.5f, 1.0f, headerTextColor);

            graphics.fill(px + 12, py + headerHeight, px + width - 12, py + headerHeight + 1, ARGB.color((int) (45 * contentAlpha), 255, 255, 255));
        }

        int itemStartY = py + headerHeight + 8;
        int trackHeight = height - headerHeight - 26;

        int totalContentH = 0;
        for (int i = 0; i < this.children.size(); i++) {
            var child = this.children.get(i);
            int footprint = (child instanceof PanelItemComponent pic) ? pic.getLayoutFootprint() : (child.height() + 4);
            totalContentH += footprint;
        }

        this.scrollbar.update(totalContentH, trackHeight, delta);
        float currentScroll = this.scrollbar.getScrollOffset();

        int curY = (int) (itemStartY - currentScroll);
        for (int i = 0; i < this.children.size(); i++) {
            var child = this.children.get(i);
            int footprint = (child instanceof PanelItemComponent pic) ? pic.getLayoutFootprint() : (child.height() + 4);

            child.updateX(px + 10);
            child.updateY(curY);

            curY += footprint;
        }

        graphics.enableScissor(px + 6, itemStartY, px + width - 6, py + height - 8);

        if (ease > 0.35f) {
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        }

        graphics.disableScissor();

        if (trackHeight > 30) {
            this.scrollbar.draw(graphics, px + width - 5, itemStartY, trackHeight, mouseX, mouseY, panelAlpha, delta);
        }

        graphics.disableScissor();
    }

    public int getActualPanelX() {
        int rootW = this.parent() != null ? this.parent().width() : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return rootW / 2 + 101 - PANEL_WIDTH / 2;
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