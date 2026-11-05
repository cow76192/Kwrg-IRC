package me.cookie.kwrg.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.irc.manager.NickManager;
import me.cookie.kwrg.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WorldTextRenderer {
    public static final float DEFAULT_SCALE = 0.025F;
    public static final int DEFAULT_COLOR = 0xFFFFFFFF;
    public static final int FULL_BRIGHT = 0xF000F0;

    private static final Logger logger = LoggerFactory.getLogger(WorldTextRenderer.class);

    // 浮动振幅
    public static final float DEFAULT_FLOAT_AMPLITUDE = 1.65F;
    // 动态浮动速度
    public static final float DEFAULT_FLOAT_SPEED = 0.006F;

    // 羽化发光内圈偏移
    private static final float[][] GLOW_INNER_OFFSETS = {
            {-0.45F, 0.0F}, {0.45F, 0.0F}, {0.0F, -0.45F}, {0.0F, 0.45F},
            {-0.35F, -0.35F}, {0.35F, -0.35F}, {-0.35F, 0.35F}, {0.35F, 0.35F}
    };
    // 羽化发光外圈扩散偏移
    private static final float[][] GLOW_OUTER_OFFSETS = {
            {-0.95F, 0.0F}, {0.95F, 0.0F}, {0.0F, -0.95F}, {0.0F, 0.95F},
            {-0.70F, -0.70F}, {0.70F, -0.70F}, {-0.70F, 0.70F}, {0.70F, 0.70F}
    };

    private static boolean testHookRegistered = false;
    private static boolean testActive = false;
    private static Vec3 testAnchorPos = null;

    /**
     * 供 /irc debug wordTextRenderTest 调用的测试开关
     */
    public static int toggleDebugTest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            logger.error("当前不在世界中，无法测试渲染");
            return 0;
        }

        if (!testHookRegistered) {
            testHookRegistered = true;
            LevelRenderEvents.END_MAIN.register(WorldTextRenderer::onRenderWorldTest);
        }

        if (!testActive) {
            testActive = true;
            testAnchorPos = mc.player.position().add(0, 0.5, 0);
            IrcMessageHandler.printMessage("§a[IRC] 已开启世界文本渲染测试");
        } else {
            testActive = false;
            testAnchorPos = null;
            IrcMessageHandler.printMessage("§e[IRC] 已关闭世界文本渲染测试。");
        }
        return 1;
    }

    private static void onRenderWorldTest(LevelRenderContext context) {
        if (!testActive || testAnchorPos == null) return;
        PoseStack poseStack = context.poseStack();
        MultiBufferSource.BufferSource bufferSource = context.bufferSource();
        if (poseStack == null || bufferSource == null) return;

        Vec3 leftBase = testAnchorPos.add(-3.8, 0, 0);

        render(poseStack, bufferSource, leftBase.add(0, 3.6, 0), "[L1] String Default (Shadow ON)");
        render(poseStack, bufferSource, leftBase.add(0, 3.3, 0), "[L2] String No-Shadow (Shadow OFF)", false);
        render(poseStack, bufferSource, leftBase.add(0, 3.0, 0), "[L3] String IntColor", 0x00FF87);
        render(poseStack, bufferSource, leftBase.add(0, 2.7, 0), "[L4] String IntColor (Shadow OFF)", 0xFF55AA, false);
        render(poseStack, bufferSource, leftBase.add(0, 2.4, 0), "[L5] String AwtColor", new Color(255, 180, 50));
        render(poseStack, bufferSource, leftBase.add(0, 2.1, 0), "[L6] String AwtColor (Shadow OFF)", new Color(80, 200, 255), false);
        render(poseStack, bufferSource, leftBase.add(0, 1.8, 0), ColorUtil.applyStyle("[L7] Component Rainbow", "rainbow bold"));
        render(poseStack, bufferSource, leftBase.add(0, 1.5, 0), ColorUtil.applyStyle("[L8] Component Rainbow (Shadow OFF)", "rainbow"), false);
        render(poseStack, bufferSource, leftBase.add(0, 1.2, 0), Component.literal("[L9] Component + Fallback Color"), 0xFFFF55);
        render(poseStack, bufferSource, leftBase.add(0, 0.9, 0), Component.literal("[L10] Component + Color (Shadow OFF)"), 0x55FFFF, false);
        renderStyled(poseStack, bufferSource, leftBase.add(0, 0.6, 0), "[L11] renderStyled Sunset (Shadow ON)", "sunset flow");
        renderStyled(poseStack, bufferSource, leftBase.add(0, 0.3, 0), "[L12] renderStyled Neon (Shadow OFF)", "neon animate bold", false);
        render(poseStack, bufferSource, leftBase.add(0, 0.0, 0), ColorUtil.applyStyle("[L13] Full Args Component", "fire"), DEFAULT_COLOR, 0.025F, false, true);
        renderStyled(poseStack, bufferSource, leftBase.add(0, -0.3, 0), "[L14] Full Args Styled", "ocean wave", 0.025F, false, true);

        // ==================== 中列：测试【动态浮动】、【静止浮动(禁止上下运动)】与【羽化发光】重载 ====================
        Vec3 midBase = testAnchorPos.add(0.0, 0, 0);

        // M1. String 动态浮动 (floating=true, 默认动画开启)
        render(poseStack, bufferSource, midBase.add(0, 4.2, 0), "[M1] String Dynamic Floating (Moving)", true, true, false);

        // M2. String 静止浮动 (floating=true, floatAnimated=false -> 每个字高低错落但固定不动!)
        render(poseStack, bufferSource, midBase.add(0, 3.8, 0), "[M2] String Static Floating (Frozen Jitter)", true, true, false, false);

        // M3. String 静止浮动 + 指定颜色 + 羽化发光 (floatAnimated=false, glow=true)
        render(poseStack, bufferSource, midBase.add(0, 3.4, 0), "[M3] String Static Float + Feather Glow!", 0x00F2FE, false, true, false, true);

        // M4. String 动态浮动 + 指定颜色 + 羽化发光 (floatAnimated=true, glow=true)
        render(poseStack, bufferSource, midBase.add(0, 3.0, 0), "[M4] String Dynamic Float + Feather Glow!", 0xFF55AA, false, true, true, true);

        // M5. Component (ColorUtil 渐变) + 静止浮动 (floatAnimated=false) + 羽化发光
        render(poseStack, bufferSource, midBase.add(0, 2.6, 0), ColorUtil.applyStyle("[M5] Comp Static Float + Glow (Frozen)", "neon bold"), false, true, false, true);

        // M6. Component (ColorUtil 樱花流光) + 动态浮动 (floatAnimated=true) + 羽化发光
        render(poseStack, bufferSource, midBase.add(0, 2.2, 0), ColorUtil.applyStyle("[M6] Comp Dynamic Float + Glow (Moving)", "sakura flow bold"), false, true, true, true);

        // M7. renderStyled 静止浮动 + 羽化发光 (floatAnimated=false)
        renderStyled(poseStack, bufferSource, midBase.add(0, 1.8, 0), "[M7] Styled Static Float + Glow", "gold bold", false, true, false, true);

        // M8. renderStyled 动态浮动 + 羽化发光 (floatAnimated=true)
        renderStyled(poseStack, bufferSource, midBase.add(0, 1.4, 0), "[M8] Styled Dynamic Float + Glow", "sunset animate bold", false, true, true, true);

        // M9. 两点 (bottomLeft -> topRight) 3D 世界空间：String 静止浮动 + 羽化发光 (floatAnimated=false)
        Vec3 midBL1 = midBase.add(-1.4, 0.80, 0);
        Vec3 midTR1 = midBase.add(1.4, 1.10, 0);
        render(poseStack, bufferSource, midBL1, midTR1, "[M9] 2-Point Static Float + Glow", 0x00FF87, false, true, false, true);

        // M10. 两点 (bottomLeft -> topRight) 3D 世界空间：Component 静止浮动 + 羽化发光 (floatAnimated=false)
        Vec3 midBL2 = midBase.add(-1.4, 0.35, 0);
        Vec3 midTR2 = midBase.add(1.4, 0.65, 0);
        render(poseStack, bufferSource, midBL2, midTR2, ColorUtil.applyStyle("[M10] 2-Point Comp Static Float+Glow", "rainbow bold"), false, true, false, true);

        // M11. 两点 (bottomLeft -> topRight) 3D 世界空间：renderStyled 静止浮动 + 羽化发光 (floatAnimated=false)
        Vec3 midBL3 = midBase.add(-1.4, -0.10, 0);
        Vec3 midTR3 = midBase.add(1.4, 0.20, 0);
        renderStyled(poseStack, bufferSource, midBL3, midTR3, "[M11] 2-Point Styled Static Float+Glow", "fire bold", false, true, false, true);

        // M12. 两点 (bottomLeft -> topRight) 3D 世界空间：renderStyled 动态浮动 + 羽化发光 (floatAnimated=true)
        Vec3 midBL4 = midBase.add(-1.4, -0.55, 0);
        Vec3 midTR4 = midBase.add(1.4, -0.25, 0);
        renderStyled(poseStack, bufferSource, midBL4, midTR4, "[M12] 2-Point Styled Dynamic Float+Glow", "purple animate bold", false, true, true, true);

        // ==================== 右列：测试两点 (bottomLeft -> topRight) 3D 世界空间基础重载 ====================
        Vec3 rightBL = testAnchorPos.add(2.2, 0, 0);
        Vec3 rightTR = testAnchorPos.add(5.0, 0, 0);

        render(poseStack, bufferSource, rightBL.add(0, 3.6, 0), rightTR.add(0, 3.82, 0), "[R1] 2-Point String (Shadow ON)");
        render(poseStack, bufferSource, rightBL.add(0, 3.3, 0), rightTR.add(0, 3.52, 0), "[R2] 2-Point String (Shadow OFF)", false);
        render(poseStack, bufferSource, rightBL.add(0, 3.0, 0), rightTR.add(0, 3.22, 0), "[R3] 2-Point String IntColor", 0x00FF87);
        render(poseStack, bufferSource, rightBL.add(0, 2.7, 0), rightTR.add(0, 2.92, 0), "[R4] 2-Point IntColor (Shadow OFF)", 0xFF55AA, false);
        render(poseStack, bufferSource, rightBL.add(0, 2.4, 0), rightTR.add(0, 2.62, 0), "[R5] 2-Point AwtColor", new Color(255, 180, 50));
        render(poseStack, bufferSource, rightBL.add(0, 2.1, 0), rightTR.add(0, 2.32, 0), "[R6] 2-Point AwtColor (Shadow OFF)", new Color(80, 200, 255), false);
        render(poseStack, bufferSource, rightBL.add(0, 1.8, 0), rightTR.add(0, 2.02, 0), ColorUtil.applyStyle("[R7] 2-Point Component Rainbow", "rainbow bold"));
        render(poseStack, bufferSource, rightBL.add(0, 1.5, 0), rightTR.add(0, 1.72, 0), ColorUtil.applyStyle("[R8] 2-Point Rainbow (Shadow OFF)", "rainbow"), false);
        render(poseStack, bufferSource, rightBL.add(0, 1.2, 0), rightTR.add(0, 1.42, 0), Component.literal("[R9] 2-Point Comp + Color"), 0xFFFF55);
        render(poseStack, bufferSource, rightBL.add(0, 0.9, 0), rightTR.add(0, 1.12, 0), Component.literal("[R10] 2-Point Comp + Color (NoShadow)"), 0x55FFFF, false);
        renderStyled(poseStack, bufferSource, rightBL.add(0, 0.6, 0), rightTR.add(0, 0.82, 0), "[R11] 2-Point Styled Sakura", "sakura flow");
        renderStyled(poseStack, bufferSource, rightBL.add(0, 0.3, 0), rightTR.add(0, 0.52, 0), "[R12] 2-Point Styled Gold (NoShadow)", "gold animate bold", false);
        renderStyled(poseStack, bufferSource, rightBL.add(0, 0.0, 0), rightTR.add(0, 0.22, 0), "[R13] 2-Point Styled Full Args", "purple wave", false, true);
        render(poseStack, bufferSource, rightBL.add(0, -0.3, 0), rightTR.add(0, -0.08, 0), ColorUtil.applyStyle("[R14] 2-Point Full Args Comp", "gradient(#00F2FE,#00FF87) animate"), DEFAULT_COLOR, false, true);

        bufferSource.endBatch();
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), DEFAULT_COLOR, DEFAULT_SCALE, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, int color) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), color, DEFAULT_SCALE, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, int color, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), color, DEFAULT_SCALE, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, int color, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), color, DEFAULT_SCALE, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, int color, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), color, DEFAULT_SCALE, shadow, true, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, Color color) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), argb, DEFAULT_SCALE, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, Color color, boolean shadow) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), argb, DEFAULT_SCALE, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, Color color, boolean shadow, boolean floating, boolean glow) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), argb, DEFAULT_SCALE, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, Color color, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, Component.literal(text), argb, DEFAULT_SCALE, shadow, true, floating, floatAnimated, glow);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, String styleConfig) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, richText, DEFAULT_COLOR, DEFAULT_SCALE, true, true, false, false, false);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, String styleConfig, boolean shadow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, richText, DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, false, false, false);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, String styleConfig, boolean shadow, boolean floating, boolean glow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, richText, DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, floating, floating, glow);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, String styleConfig, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, richText, DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, floating, floatAnimated, glow);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, String styleConfig, float scale, boolean shadow, boolean seeThrough) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, richText, DEFAULT_COLOR, scale, shadow, seeThrough, false, false, false);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, String styleConfig, float scale, boolean shadow, boolean seeThrough, boolean floating, boolean glow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, richText, DEFAULT_COLOR, scale, shadow, seeThrough, floating, floating, glow);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, String text, String styleConfig, float scale, boolean shadow, boolean seeThrough, boolean floating, boolean floatAnimated, boolean glow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, richText, DEFAULT_COLOR, scale, shadow, seeThrough, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component) {
        render(poseStack, bufferSource, bottomLeft, component, DEFAULT_COLOR, DEFAULT_SCALE, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, component, DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, component, DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, component, DEFAULT_COLOR, DEFAULT_SCALE, shadow, true, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, int color) {
        render(poseStack, bufferSource, bottomLeft, component, color, DEFAULT_SCALE, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, int color, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, component, color, DEFAULT_SCALE, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, int color, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, component, color, DEFAULT_SCALE, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, int color, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, component, color, DEFAULT_SCALE, shadow, true, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, int color, float scale, boolean shadow, boolean seeThrough) {
        render(poseStack, bufferSource, bottomLeft, component, color, scale, shadow, seeThrough, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, int color, float scale, boolean shadow, boolean seeThrough, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, component, color, scale, shadow, seeThrough, floating, floating, glow);
    }

    // 左下角渲染
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Component component, int color, float scale, boolean shadow, boolean seeThrough, boolean floating, boolean floatAnimated, boolean glow) {
        if (component == null || component.getString().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        FormattedCharSequence wrappedSeq = NickManager.wrap(component.getVisualOrderText());

        poseStack.pushPose();
        poseStack.translate(bottomLeft.x - camPos.x, bottomLeft.y - camPos.y, bottomLeft.z - camPos.z);

        poseStack.mulPose(camera.rotation());
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        Font.DisplayMode displayMode = seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

        float startX = 0.0F;
        float startY = -font.lineHeight;
        int finalColor = ColorUtil.ensureAlpha(color);

        drawComponentInternal(font, wrappedSeq, startX, startY, finalColor, shadow, matrix, bufferSource, displayMode, floating, floatAnimated, glow);

        poseStack.popPose();
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), DEFAULT_COLOR, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), DEFAULT_COLOR, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), DEFAULT_COLOR, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), DEFAULT_COLOR, shadow, true, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, int color) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), color, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, int color, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), color, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, int color, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), color, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, int color, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), color, shadow, true, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, Color color) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), argb, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, Color color, boolean shadow) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), argb, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, Color color, boolean shadow, boolean floating, boolean glow) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), argb, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, Color color, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, bottomLeft, topRight, Component.literal(text), argb, shadow, true, floating, floatAnimated, glow);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, String styleConfig) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, topRight, richText, DEFAULT_COLOR, true, true, false, false, false);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, String styleConfig, boolean shadow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, topRight, richText, DEFAULT_COLOR, shadow, true, false, false, false);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, String styleConfig, boolean shadow, boolean seeThrough) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, topRight, richText, DEFAULT_COLOR, shadow, seeThrough, false, false, false);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, String styleConfig, boolean shadow, boolean floating, boolean glow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, topRight, richText, DEFAULT_COLOR, shadow, true, floating, floating, glow);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, String styleConfig, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, topRight, richText, DEFAULT_COLOR, shadow, true, floating, floatAnimated, glow);
    }

    public static void renderStyled(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, String text, String styleConfig, boolean shadow, boolean seeThrough, boolean floating, boolean floatAnimated, boolean glow) {
        Component richText = ColorUtil.applyStyle(text, styleConfig);
        render(poseStack, bufferSource, bottomLeft, topRight, richText, DEFAULT_COLOR, shadow, seeThrough, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, DEFAULT_COLOR, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, DEFAULT_COLOR, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, DEFAULT_COLOR, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, DEFAULT_COLOR, shadow, true, floating, floatAnimated, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, int color) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, color, true, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, int color, boolean shadow) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, color, shadow, true, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, int color, boolean shadow, boolean seeThrough) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, color, shadow, seeThrough, false, false, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, int color, boolean shadow, boolean floating, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, color, shadow, true, floating, floating, glow);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, int color, boolean shadow, boolean floating, boolean floatAnimated, boolean glow) {
        render(poseStack, bufferSource, bottomLeft, topRight, component, color, shadow, true, floating, floatAnimated, glow);
    }

    // 左下角 + 右上角
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, Component component, int color, boolean shadow, boolean seeThrough, boolean floating, boolean floatAnimated, boolean glow) {
        if (component == null || component.getString().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        FormattedCharSequence wrappedSeq = NickManager.wrap(component.getVisualOrderText());
        float textWidth;
        boolean prevBypass = NickManager.isBypassed();
        try {
            NickManager.setBypassed(true);
            textWidth = font.getSplitter().stringWidth(wrappedSeq);
        } finally {
            NickManager.setBypassed(prevBypass);
        }

        int textHeight = font.lineHeight;
        if (textWidth <= 0.0F || textHeight <= 0) return;

        double dx = topRight.x - bottomLeft.x;
        double dy = topRight.y - bottomLeft.y;
        double dz = topRight.z - bottomLeft.z;

        double targetWidth = Math.sqrt(dx * dx + dz * dz);
        double targetHeight = Math.abs(dy);

        if (targetWidth < 1.0E-4 && targetHeight < 1.0E-4) {
            render(poseStack, bufferSource, bottomLeft, component, color, DEFAULT_SCALE, shadow, seeThrough, floating, floatAnimated, glow);
            return;
        }

        if (targetWidth < 1.0E-4) {
            float scale = (float) (targetHeight / textHeight);
            render(poseStack, bufferSource, bottomLeft, component, color, scale, shadow, seeThrough, floating, floatAnimated, glow);
            return;
        }

        Font.DisplayMode displayMode = seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        int finalColor = ColorUtil.ensureAlpha(color);

        float scaleX = (float) (targetWidth / textWidth);
        float scaleY = targetHeight > 1.0E-4 ? (float) (targetHeight / textHeight) : scaleX;
        float scaleZ = Math.min(scaleX, scaleY);

        float yaw = (float) Mth.atan2(-dz, dx);

        // 正面
        poseStack.pushPose();
        poseStack.translate(bottomLeft.x - camPos.x, bottomLeft.y - camPos.y, bottomLeft.z - camPos.z);
        poseStack.mulPose(Axis.YP.rotation(yaw + Mth.PI));
        poseStack.scale(-scaleX, -scaleY, scaleZ);

        drawComponentInternal(font, wrappedSeq, 0.0F, -textHeight, finalColor, shadow, poseStack.last().pose(), bufferSource, displayMode, floating, floatAnimated, glow);
        poseStack.popPose();

        // 背面
        poseStack.pushPose();
        poseStack.translate(topRight.x - camPos.x, bottomLeft.y - camPos.y, topRight.z - camPos.z);
        poseStack.mulPose(Axis.YP.rotation(yaw));
        poseStack.scale(-scaleX, -scaleY, scaleZ);

        drawComponentInternal(font, wrappedSeq, 0.0F, -textHeight, finalColor, shadow, poseStack.last().pose(), bufferSource, displayMode, floating, floatAnimated, glow);
        poseStack.popPose();
    }

    private record StyledGlyph(int index, Style style, int codePoint) {}

    private static void drawComponentInternal(
            Font font,
            FormattedCharSequence sequence,
            float startX,
            float startY,
            int defaultColor,
            boolean shadow,
            Matrix4f matrix,
            MultiBufferSource bufferSource,
            Font.DisplayMode displayMode,
            boolean floating,
            boolean floatAnimated,
            boolean glow
    ) {
        boolean prevBypass = NickManager.isBypassed();
        try {
            NickManager.setBypassed(true);

            if (!floating && !glow) {
                font.drawInBatch(sequence, startX, startY, defaultColor, shadow, matrix, bufferSource, displayMode, 0, FULL_BRIGHT);
                return;
            }

            List<StyledGlyph> glyphs = new ArrayList<>();
            sequence.accept((index, style, codePoint) -> {
                glyphs.add(new StyledGlyph(index, style, codePoint));
                return true;
            });

            float cursorX = startX;
            float time = floatAnimated ? (float) (System.currentTimeMillis() % 100000L) * DEFAULT_FLOAT_SPEED : 0.0F;

            for (int i = 0; i < glyphs.size(); i++) {
                StyledGlyph glyph = glyphs.get(i);
                FormattedCharSequence singleCharSeq = sink -> sink.accept(0, glyph.style(), glyph.codePoint());

                float charAdvance = font.getSplitter().stringWidth(singleCharSeq);

                float yOffset = 0.0F;
                if (floating) {
                    if (floatAnimated) {
                        float wave1 = Mth.sin(time + i * 0.75F);
                        float wave2 = 0.35F * Mth.cos(time * 1.6F - i * 1.15F);
                        yOffset = (wave1 + wave2) * DEFAULT_FLOAT_AMPLITUDE;
                    } else {
                        float staticWave1 = Mth.sin(i * 1.35F + 0.6F);
                        float staticWave2 = 0.45F * Mth.cos(i * 2.45F - 0.8F);
                        yOffset = (staticWave1 + staticWave2) * DEFAULT_FLOAT_AMPLITUDE;
                    }
                }

                float charY = startY + yOffset;

                if (glow) {
                    renderFeatherGlowForChar(font, glyph, cursorX, charY, defaultColor, matrix, bufferSource, displayMode);
                }

                font.drawInBatch(
                        singleCharSeq,
                        cursorX,
                        charY,
                        defaultColor,
                        shadow,
                        matrix,
                        bufferSource,
                        displayMode,
                        0,
                        FULL_BRIGHT
                );

                cursorX += charAdvance;
            }
        } finally {
            NickManager.setBypassed(prevBypass);
        }
    }

    private static void renderFeatherGlowForChar(
            Font font,
            StyledGlyph glyph,
            float x,
            float y,
            int defaultColor,
            Matrix4f matrix,
            MultiBufferSource bufferSource,
            Font.DisplayMode displayMode
    ) {
        TextColor styleColor = glyph.style().getColor();
        int baseRgb = (styleColor != null) ? (styleColor.getValue() & 0xFFFFFF) : (defaultColor & 0xFFFFFF);
        int glowRgb = ColorUtil.brightenRgb(baseRgb, 0.20F);

        Style glowStyle = stripColorPreserveFormatting(glyph.style());
        FormattedCharSequence glowSeq = sink -> sink.accept(0, glowStyle, glyph.codePoint());

        int baseAlpha = (defaultColor >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 255;

        int outerAlpha = Math.max(10, (int) (baseAlpha * 0.12F));
        int outerColor = (outerAlpha << 24) | glowRgb;
        for (float[] offset : GLOW_OUTER_OFFSETS) {
            font.drawInBatch(
                    glowSeq,
                    x + offset[0],
                    y + offset[1],
                    outerColor,
                    false,
                    matrix,
                    bufferSource,
                    displayMode,
                    0,
                    FULL_BRIGHT
            );
        }

        int innerAlpha = Math.max(20, (int) (baseAlpha * 0.26F));
        int innerColor = (innerAlpha << 24) | glowRgb;
        for (float[] offset : GLOW_INNER_OFFSETS) {
            font.drawInBatch(
                    glowSeq,
                    x + offset[0],
                    y + offset[1],
                    innerColor,
                    false,
                    matrix,
                    bufferSource,
                    displayMode,
                    0,
                    FULL_BRIGHT
            );
        }
    }

    private static Style stripColorPreserveFormatting(Style source) {
        if (source == null || source == Style.EMPTY) return Style.EMPTY;
        Style result = Style.EMPTY
                .withBold(source.isBold())
                .withItalic(source.isItalic())
                .withUnderlined(source.isUnderlined())
                .withStrikethrough(source.isStrikethrough())
                .withObfuscated(source.isObfuscated());
        if (source.getFont() != null) {
            result = result.withFont(source.getFont());
        }
        return result;
    }
}