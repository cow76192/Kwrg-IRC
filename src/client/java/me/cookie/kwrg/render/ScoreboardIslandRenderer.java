package me.cookie.kwrg.render;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public class ScoreboardIslandRenderer {

    private static final FontDescription SCOREBOARD_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("kwrg", "island"));
    private static final Style SCOREBOARD_STYLE = Style.EMPTY.withFont(SCOREBOARD_FONT);

    private static final float CORNER_RADIUS = 8.0f;
    private static final float SHADOW_BLUR = 4.0f;
    private static final float SHADOW_OFFSET_Y = 1.5f;
    private static final int SHADOW_BASE_ALPHA = 45;

    // 动画时长常量（秒）
    private static final float SIZE_ANIM_DURATION = 0.28f;

    private static float targetW = 100.0f;
    private static float targetH = 120.0f;

    private static float smoothW = 100.0f;
    private static float startW = 100.0f;
    private static float widthAnimTimer = 1.0f;

    private static float smoothH = 120.0f;
    private static float startH = 120.0f;
    private static float heightAnimTimer = 1.0f;

    private static float smoothX = 0.0f;
    private static float smoothY = 0.0f;

    private static long lastUpdateTime = -1L;
    private static long lastRenderActiveTime = 0L;
    private static boolean editorPreview = false;
    private static boolean skyHanniActiveThisFrame = false;
    private static boolean isScissorActive = false;

    public static boolean isEnabled() {
        return HudElementRegistry.isEnabled(HudElementRegistry.SCOREBOARD);
    }

    public static void setEditorPreview(boolean enabled) {
        editorPreview = enabled;
    }

    public static boolean isEditorPreview() {
        return editorPreview;
    }

    public static void markSkyHanniActive() {
        skyHanniActiveThisFrame = true;
        lastRenderActiveTime = System.currentTimeMillis();
    }

    public static boolean isSkyHanniActive() {
        return skyHanniActiveThisFrame;
    }

    public static void resetFrameState() {
        skyHanniActiveThisFrame = false;
    }

    public static float getSmoothX() { return smoothX; }
    public static float getSmoothY() { return smoothY; }
    public static float getSmoothW() { return smoothW; }
    public static float getSmoothH() { return smoothH; }

    public static float[] getIslandBounds() {
        return new float[]{smoothX, smoothY, smoothW, smoothH};
    }

    public static float resolveScoreboardX(float width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return 0.0f;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
        return HudElement.Anchor.resolveX(state.anchorX, state.marginX,
                HudElementRegistry.resolveOffsetX(HudElementRegistry.SCOREBOARD),
                width, mc.getWindow().getGuiScaledWidth());
    }

    public static float resolveScoreboardY(float height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return 0.0f;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
        return HudElement.Anchor.resolveY(state.anchorY, state.marginY,
                HudElementRegistry.resolveOffsetY(HudElementRegistry.SCOREBOARD),
                height, mc.getWindow().getGuiScaledHeight());
    }

    public static void updateTargetBounds(float rawContentW, float rawContentH) {
        lastRenderActiveTime = System.currentTimeMillis();

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
        int pad = state.padding;

        float newTargetW = Math.max(50.0f, rawContentW + pad * 2.0f);
        float newTargetH = Math.max(30.0f, rawContentH + pad * 2.0f);

        if (Math.abs(newTargetW - targetW) > 1.0f) {
            startW = smoothW;
            targetW = newTargetW;
            widthAnimTimer = 0.0f;
        }

        if (Math.abs(newTargetH - targetH) > 1.0f) {
            startH = smoothH;
            targetH = newTargetH;
            heightAnimTimer = 0.0f;
        }
    }

    public static void updateAnimation() {
        long now = System.currentTimeMillis();
        if (lastUpdateTime < 0L) {
            lastUpdateTime = now;
            smoothX = resolveScoreboardX(smoothW);
            smoothY = resolveScoreboardY(smoothH);
            return;
        }

        float dt = (now - lastUpdateTime) / 1000.0f;
        lastUpdateTime = now;
        dt = Mth.clamp(dt, 0.001f, 0.05f);

        if (widthAnimTimer < 1.0f) {
            widthAnimTimer = Math.min(1.0f, widthAnimTimer + dt / SIZE_ANIM_DURATION);
            smoothW = Mth.lerp(AnimationUtil.easeSmoothDecel(widthAnimTimer), startW, targetW);
        } else {
            smoothW = targetW;
        }

        if (heightAnimTimer < 1.0f) {
            heightAnimTimer = Math.min(1.0f, heightAnimTimer + dt / SIZE_ANIM_DURATION);
            smoothH = Mth.lerp(AnimationUtil.easeSmoothDecel(heightAnimTimer), startH, targetH);
        } else {
            smoothH = targetH;
        }

        smoothX = resolveScoreboardX(smoothW);
        smoothY = resolveScoreboardY(smoothH);
    }

    public static void renderScoreboardBackground(OwoUIGraphics graphics) {
        if (!isEnabled()) return;

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
        float opacity = Mth.clamp(state.opacity, 0.0f, 1.0f);
        if (opacity <= 0.005f) return;

        if (state.shadow) {
            RenderUtil.drawSoftDropShadow(graphics, smoothX, smoothY, smoothW, smoothH, CORNER_RADIUS,
                    SHADOW_BLUR, SHADOW_OFFSET_Y, SHADOW_BASE_ALPHA, opacity);
        }

        int bgAlpha = (int) (0xE6 * opacity);
        int borderAlpha = (int) (0x80 * opacity);

        Color bgColor = Color.ofArgb(ARGB.color(bgAlpha, 17, 22, 31));
        Color borderColor = Color.ofArgb(ARGB.color(borderAlpha, 79, 195, 247));

        RenderUtil.drawSubpixelRoundedPanel(graphics, smoothX, smoothY, smoothW, smoothH, CORNER_RADIUS, 1.0f, bgColor, borderColor);
    }

    public static boolean beginScissor(GuiGraphicsExtractor extractor) {
        if (extractor == null || isScissorActive) return false;

        int sX1 = (int) Math.floor(smoothX);
        int sY1 = (int) Math.floor(smoothY);
        int sX2 = (int) Math.ceil(smoothX + smoothW);
        int sY2 = (int) Math.ceil(smoothY + smoothH);

        if (sX2 > sX1 && sY2 > sY1) {
            extractor.enableScissor(sX1, sY1, sX2, sY2);
            isScissorActive = true;
            return true;
        }
        return false;
    }

    public static void endScissor(GuiGraphicsExtractor extractor) {
        if (extractor == null || !isScissorActive) return;

        extractor.disableScissor();
        isScissorActive = false;
    }

    public static void renderPreviewIfEmpty(GuiGraphicsExtractor extractor) {
        if (!editorPreview || !isEnabled()) return;

        long now = System.currentTimeMillis();
        if ((now - lastRenderActiveTime) < 200L) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        Component title = Component.literal("§6§lDynamicScoreboard").setStyle(SCOREBOARD_STYLE);
        Component[] sampleLines = new Component[]{
                Component.literal("§701/10/26 §8m12A").setStyle(SCOREBOARD_STYLE),
                Component.literal(" "),
                Component.literal("§fCoins: §612,450,890").setStyle(SCOREBOARD_STYLE),
                Component.literal("§fGems: §a1,200").setStyle(SCOREBOARD_STYLE),
                Component.literal("§fServer: §emini12D").setStyle(SCOREBOARD_STYLE),
                Component.literal(" "),
                Component.literal("§e§owww.hypixel.net").setStyle(SCOREBOARD_STYLE)
        };

        float maxW = font.width(title);
        for (Component c : sampleLines) maxW = Math.max(maxW, font.width(c));
        float totalH = (sampleLines.length + 1) * 9.0f;

        updateTargetBounds(maxW, totalH);
        updateAnimation();

        OwoUIGraphics graphics = OwoUIGraphics.of(extractor);
        renderScoreboardBackground(graphics);

        boolean scissored = beginScissor(extractor);
        try {
            HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
            float textX = smoothX + state.padding;
            float textY = smoothY + state.padding;

            float titleX = smoothX + (smoothW - font.width(title)) / 2.0f;
            RenderUtil.drawFluidSubpixelText(graphics, title, titleX, textY, 0xFFFFFFFF);
            textY += 10.0f;

            for (Component line : sampleLines) {
                RenderUtil.drawFluidSubpixelText(graphics, line, textX, textY, 0xFFFFFFFF);
                textY += 9.0f;
            }
        } finally {
            if (scissored) {
                endScissor(extractor);
            }
        }
    }
}