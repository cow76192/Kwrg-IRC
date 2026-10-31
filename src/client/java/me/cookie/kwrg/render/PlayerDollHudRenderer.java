package me.cookie.kwrg.render;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.theme.GuiThemeService;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class PlayerDollHudRenderer {

    private static final float BASE_WIDTH = 50.0f;
    private static final float BASE_HEIGHT = 70.0f;
    private static final int BASE_MODEL_SIZE = 30;
    private static final float MODEL_OFFSET_Y = 0.0625f;

    private static final float[] DOLL_BOUNDS = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

    private static boolean editorPreview = false;
    private static boolean renderedThisFrame = false;
    private static volatile boolean extractingHudDoll = false;
    private static volatile Object hudDollRenderState = null;
    private static volatile float currentOpacity = 1.0f;

    // 视角跟随平滑状态
    private static float smoothedYawDiff = 0.0f;
    private static float smoothedPitch = 0.0f;
    private static long lastFrameMs = -1L;

    private PlayerDollHudRenderer() {
    }

    public static void resetFrameState() {
        renderedThisFrame = false;
    }

    public static void setEditorPreview(boolean preview) {
        editorPreview = preview;
    }

    public static float[] getDollBounds() {
        return DOLL_BOUNDS;
    }

    public static boolean isExtractingHudDoll() {
        return extractingHudDoll;
    }

    public static void markHudDollRenderState(Object state) {
        hudDollRenderState = state;
    }

    public static boolean isHudDollRenderState(Object state) {
        return state != null && state == hudDollRenderState;
    }

    public static int getBlitColor() {
        int a = Mth.clamp(Math.round(currentOpacity * 255.0f), 0, 255);
        return ARGB.color(a, a, a, a);
    }

    public static void renderPreviewIfEmpty(GuiGraphicsExtractor graphics) {
        if (!renderedThisFrame && editorPreview) {
            render(graphics, 0.016f);
        }
    }

    public static void render(GuiGraphicsExtractor graphics, float delta) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof me.cookie.kwrg.gui.IrcScreen || mc.screen instanceof InventoryScreen) {
            return;
        }

        HudElement.State state = HudElementRegistry.state(HudElementRegistry.PLAYER_DOLL);
        boolean enabled = state.enabled;
        if (!enabled && !editorPreview) {
            lastFrameMs = -1L;
            return;
        }

        IrcConfig config = IrcConfig.get();
        boolean followGlobal = config.playerDollFollowGlobal;

        // 直接使用 HudElement.State 的 scale
        float rawScale = followGlobal ? config.guiScale : state.scale;
        float scale = Mth.clamp(Float.isFinite(rawScale) ? rawScale : 1.0f, HudElement.State.MIN_SCALE, HudElement.State.MAX_SCALE);

        float baseOpacity = followGlobal
                ? Mth.clamp(GuiThemeService.opacity(), 0.05f, 1.0f)
                : Mth.clamp(Float.isFinite(config.playerDollOpacity) ? config.playerDollOpacity : state.opacity, 0.05f, 1.0f);

        currentOpacity = editorPreview ? Math.max(0.35f, baseOpacity) : baseOpacity;
        if (currentOpacity <= 0.01f && !editorPreview) {
            return;
        }

        float boxW = BASE_WIDTH * scale;
        float boxH = BASE_HEIGHT * scale;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float offsetX = HudElementRegistry.resolveOffsetX(HudElementRegistry.PLAYER_DOLL);
        float offsetY = HudElementRegistry.resolveOffsetY(HudElementRegistry.PLAYER_DOLL);

        float posX = HudElement.Anchor.resolveX(state.anchorX, state.marginX, offsetX, boxW, screenW);
        float posY = HudElement.Anchor.resolveY(state.anchorY, state.marginY, offsetY, boxH, screenH);

        DOLL_BOUNDS[0] = posX;
        DOLL_BOUNDS[1] = posY;
        DOLL_BOUNDS[2] = boxW;
        DOLL_BOUNDS[3] = boxH;

        renderedThisFrame = true;

        Player player = mc.player;
        if (player == null) {
            if (editorPreview) {
                OwoUIGraphics owo = OwoUIGraphics.of(graphics);
                RenderUtil.drawFluidSubpixelText(
                        owo,
                        Component.literal("玩家模型"),
                        posX + 6.0f,
                        posY + boxH * 0.5f - 4.0f,
                        AnimationUtil.argb(0xFFFFFF, 200)
                );
            }
            return;
        }

        int x0 = Math.round(posX);
        int y0 = Math.round(posY);
        int x1 = Math.round(posX + boxW);
        int y1 = Math.round(posY + boxH);
        int modelSize = Math.max(10, Math.round(BASE_MODEL_SIZE * scale));

        float midX = (x0 + x1) * 0.5f;
        float midY = (y0 + y1) * 0.5f;

        float lookX;
        float lookY;

        long now = System.currentTimeMillis();
        float dt = lastFrameMs < 0L ? 0.016f : Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;

        if (mc.screen != null) {
            // 打开 HUD 编辑器或聊天框等界面时跟随鼠标光标转动
            var window = mc.getWindow();
            lookX = (float) (mc.mouseHandler.xpos() * ((double) window.getGuiScaledWidth() / (double) window.getScreenWidth()));
            lookY = (float) (mc.mouseHandler.ypos() * ((double) window.getGuiScaledHeight() / (double) window.getScreenHeight()));
        } else {
            // 正常游戏内视角锁定时，跟随玩家视角转动
            float headYaw = Mth.lerp(delta, player.yHeadRotO, player.yHeadRot);
            float bodyYaw = Mth.lerp(delta, player.yBodyRotO, player.yBodyRot);
            float rawYawDiff = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -60.0f, 60.0f);
            float rawPitch = Mth.clamp(player.getViewXRot(delta), -60.0f, 60.0f);

            float smoothFactor = 1.0f - (float) Math.pow(0.0005f, dt);
            smoothedYawDiff = AnimationUtil.lerp(smoothFactor, smoothedYawDiff, rawYawDiff);
            smoothedPitch = AnimationUtil.lerp(smoothFactor, smoothedPitch, rawPitch);

            lookX = midX - smoothedYawDiff * 1.35f;
            lookY = midY + smoothedPitch * 1.15f;
        }

        // 若实体自身 Attributes.SCALE 不为 1.0，补偿 modelSize 使其始终渲染为 1.0 倍标准体型
        float entityAttrScale = player.getScale();
        if (Float.isFinite(entityAttrScale) && Math.abs(entityAttrScale - 1.0f) > 0.001f) {
            modelSize = Math.max(10, Math.round(modelSize * entityAttrScale));
        }

        boolean prevInInventory = PlayerCustomizationManager.inInventoryRendering;
        PlayerCustomizationManager.inInventoryRendering = true;
        extractingHudDoll = true;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics,
                    x0, y0, x1, y1,
                    modelSize,
                    MODEL_OFFSET_Y,
                    lookX, lookY,
                    player
            );
        } finally {
            extractingHudDoll = false;
            PlayerCustomizationManager.inInventoryRendering = prevInInventory;
        }
    }
}