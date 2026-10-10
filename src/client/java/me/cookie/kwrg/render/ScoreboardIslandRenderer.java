package me.cookie.kwrg.render;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ScoreboardIslandRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-scoreboard");
    private static final float MIN_EXTERNAL_WIDTH = 40.0f;
    private static final float MIN_EXTERNAL_HEIGHT = 12.0f;

    private static final float SIZE_SMOOTH_TAU = 0.12f;
    private static final float SIZE_SNAP = 0.05f;
    private static final float ALPHA_ANIM_SPEED = 8.0f;
    private static final float POSITION_ANIM_SPEED = 12.0f;
    private static final long CONTINUOUS_TIMEOUT_MS = 150L;
    private static final float CORNER_RADIUS = 12.0f;
    private static final float PLACEHOLDER_W = 140.0f;
    private static final float PLACEHOLDER_H = 90.0f;

    private static float targetW = PLACEHOLDER_W;
    private static float targetH = PLACEHOLDER_H;
    private static float smoothW = PLACEHOLDER_W;
    private static float smoothH = PLACEHOLDER_H;

    private static float smoothX = -1.0f;
    private static float smoothY = -1.0f;
    private static float currentAlpha = 0.0f;

    private static long lastSeenMs = 0L;
    private static long lastTickMs = 0L;
    private static boolean drawnThisFrame = false;
    private static boolean hasEverSeen = false;
    private static boolean posePushed = false;
    private static boolean editorPreview = false;
    private static boolean externalActive = false;
    private static int externalRejects = 0;
    private static long lastExternalLogMs = 0L;
    private static long lastExternalRenderMs = 0L;
    private static GuiGraphicsExtractor activeGraphics = null;

    public static boolean isIslandEnabled() {
        return HudElementRegistry.state(HudElementRegistry.SCOREBOARD).enabled;
    }

    public static boolean hasActiveIsland() {
        return activeGraphics != null;
    }

    public static boolean isThirdPartyActive() {
        return lastExternalRenderMs > 0L && System.currentTimeMillis() - lastExternalRenderMs < 250L;
    }

    public static void setEditorPreview(boolean enabled) {
        editorPreview = enabled;
    }

    public static float[] getIslandBounds() {
        if (hasEverSeen) {
            return new float[]{smoothX, smoothY, smoothW, smoothH};
        }
        float[] anchored = resolveAnchoredRect(PLACEHOLDER_W, PLACEHOLDER_H);
        return new float[]{anchored[0], anchored[1], PLACEHOLDER_W, PLACEHOLDER_H};
    }

    private static float[] resolveAnchoredRect(float width, float height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return new float[]{0.0f, 0.0f};

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.SCOREBOARD);
        float x = HudElement.Anchor.resolveX(state.anchorX, state.marginX,
                HudElementRegistry.resolveOffsetX(HudElementRegistry.SCOREBOARD), width, mc.getWindow().getGuiScaledWidth());
        float y = HudElement.Anchor.resolveY(state.anchorY, state.marginY,
                HudElementRegistry.resolveOffsetY(HudElementRegistry.SCOREBOARD), height, mc.getWindow().getGuiScaledHeight());
        return new float[]{x, y};
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        float dt = lastTickMs == 0L ? 0.016f : (now - lastTickMs) / 1000.0f;
        lastTickMs = now;
        dt = Mth.clamp(dt, 0.001f, 0.1f);

        drawnThisFrame = false;

        if (activeGraphics != null) {
            LOGGER.warn("[ScoreboardIsland] releasing render state leaked from the previous frame");
            endIsland();
            externalActive = false;
        }

        float sizeEase = 1.0f - (float) Math.exp(-dt / SIZE_SMOOTH_TAU);

        smoothW = Mth.lerp(sizeEase, smoothW, targetW);
        smoothH = Mth.lerp(sizeEase, smoothH, targetH);

        if (Math.abs(targetW - smoothW) < SIZE_SNAP) smoothW = targetW;
        if (Math.abs(targetH - smoothH) < SIZE_SNAP) smoothH = targetH;

        float[] anchored = resolveAnchoredRect(smoothW, smoothH);
        if (smoothX < 0.0f) {
            smoothX = anchored[0];
            smoothY = anchored[1];
        } else {
            smoothX = Mth.lerp(dt * POSITION_ANIM_SPEED, smoothX, anchored[0]);
            smoothY = Mth.lerp(dt * POSITION_ANIM_SPEED, smoothY, anchored[1]);
        }

        boolean continuous = hasEverSeen && (now - lastSeenMs) < CONTINUOUS_TIMEOUT_MS;
        float targetAlpha = (continuous || editorPreview) ? Mth.clamp(HudElementRegistry.state(HudElementRegistry.SCOREBOARD).opacity, 0.0f, 1.0f) : 0.0f;
        currentAlpha = Mth.lerp(Mth.clamp(dt * ALPHA_ANIM_SPEED, 0.0f, 1.0f), currentAlpha, targetAlpha);
        if (currentAlpha < 0.005f) currentAlpha = 0.0f;
    }

    public static void updateTargetBounds(int width, int height) {
        lastSeenMs = System.currentTimeMillis();
        hasEverSeen = true;

        float w = Math.max(24.0f, width);
        float h = Math.max(20.0f, height);

        targetW = w;
        targetH = h;
    }

    public static boolean beginIsland(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY) {
        if (graphics == null || !isIslandEnabled()) return false;
        if (activeGraphics != null) return false;

        float pad = Math.max(0.0f, HudElementRegistry.state(HudElementRegistry.SCOREBOARD).padding);
        updateTargetBounds(Math.round(maxX - minX + pad * 2.0f), Math.round(maxY - minY + pad * 2.0f));

        drawnThisFrame = true;

        float x = smoothX;
        float y = smoothY;
        if (x < 0.0f || y < 0.0f) return false;

        if (currentAlpha > 0.005f) {
            drawPanel(OwoUIGraphics.of(graphics), x, y, smoothW, smoothH, currentAlpha);
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + pad - minX, y + pad - minY);
        posePushed = true;
        activeGraphics = graphics;
        return true;
    }

    public static void endIsland() {
        GuiGraphicsExtractor graphics = activeGraphics;
        activeGraphics = null;

        if (graphics == null) {
            posePushed = false;
            return;
        }

        if (posePushed) {
            posePushed = false;
            graphics.pose().popMatrix();
        }
    }

    public static void renderFadeOut(GuiGraphicsExtractor graphics) {
        if (graphics == null || drawnThisFrame) return;
        if (currentAlpha <= 0.005f || !hasEverSeen || smoothX < 0.0f) return;

        drawPanel(OwoUIGraphics.of(graphics), smoothX, smoothY, smoothW, smoothH, currentAlpha);
    }

    public static void beginExternalSource() {
        endIsland();
        externalActive = isIslandEnabled();
        if (externalActive) {
            lastExternalRenderMs = System.currentTimeMillis();
        }
    }

    public static void endExternalSource() {
        endIsland();
        externalActive = false;
    }

    public static boolean isExternalSourceActive() {
        return externalActive;
    }

    public static void renderExternalContent(GuiGraphicsExtractor graphics, float contentX, float contentY, float contentWidth, float contentHeight) {
        if (!externalActive || graphics == null) return;

        if (!isPlausibleExternalRect(graphics, contentX, contentY, contentWidth, contentHeight)) {
            noteImplausibleExternal(contentX, contentY, contentWidth, contentHeight);
            return;
        }

        int minX = Math.round(contentX);
        int minY = Math.round(contentY);
        beginIsland(graphics, minX, minY, Math.round(contentX + contentWidth), Math.round(contentY + contentHeight));
    }

    private static boolean isPlausibleExternalRect(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
        if (width < MIN_EXTERNAL_WIDTH || height < MIN_EXTERNAL_HEIGHT) return false;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return false;
        if (width > graphics.guiWidth() * 2.0f || height > graphics.guiHeight() * 2.0f) return false;

        return x + width > 0.0f && y + height > 0.0f && x < graphics.guiWidth() && y < graphics.guiHeight();
    }

    private static void noteImplausibleExternal(float x, float y, float width, float height) {
        externalRejects++;
        long now = System.currentTimeMillis();
        if (now - lastExternalLogMs > 1000L) {
            lastExternalLogMs = now;
            LOGGER.info("[ScoreboardIsland] skipped {} implausible third-party rects, last was x={} y={} w={} h={}",
                    externalRejects, x, y, width, height);
        }
    }

    public static void endExternalContent() {
        endIsland();
    }

    private static void drawPanel(OwoUIGraphics graphics, float x, float y, float width, float height, float alpha) {
        if (alpha <= 0.005f || width <= 0.0f || height <= 0.0f) return;

        int bgAlpha = (int) (245.0f * alpha);
        int borderAlpha = (int) (210.0f * alpha);

        Color background = Color.ofArgb(ARGB.color(bgAlpha, 17, 22, 31));
        Color border = Color.ofArgb(ARGB.color(borderAlpha, 84, 152, 178));

        if (HudElementRegistry.state(HudElementRegistry.SCOREBOARD).shadow) {
            RenderUtil.drawSoftDropShadow(graphics, x, y, width, height, CORNER_RADIUS,
                    DynamicIslandRenderer.getShadowBlur(), DynamicIslandRenderer.getShadowOffsetY(),
                    DynamicIslandRenderer.getShadowBaseAlpha(), alpha);
        }

        RenderUtil.drawSubpixelRoundedPanel(graphics, x, y, width, height, CORNER_RADIUS, 1.2f, background, border);
    }
}
