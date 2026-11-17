package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldTextureRenderer {

    public static final int DEFAULT_COLOR = 0xFFFFFFFF;
    public static final float DEFAULT_SIZE = 1.0F;

    private static final Logger logger = LoggerFactory.getLogger(WorldTextureRenderer.class);

    public static final Identifier TEST_LOGO_TEXTURE = Identifier.fromNamespaceAndPath("kwrg", "textures/gui/logo.png");

    private static final RenderPipeline TEXTURE_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/world_texture_depth"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline TEXTURE_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/world_texture_no_depth"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withCull(false)
                    .build()
    );

    private static final Map<Identifier, RenderType> DEPTH_RENDER_TYPES = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> NO_DEPTH_RENDER_TYPES = new ConcurrentHashMap<>();

    private static boolean testHookRegistered = false;
    private static boolean testActive = false;
    private static Vec3 testAnchorPos = null;

    // test 1
    private static final WorldTextureRenderer TEST_DEPTH_ON_RENDERER = new WorldTextureRenderer(TEST_LOGO_TEXTURE, true);
    // test 2
    private static final WorldTextureRenderer TEST_DEPTH_OFF_RENDERER = new WorldTextureRenderer(TEST_LOGO_TEXTURE, false);

    private final boolean depthTest;
    private final Identifier boundTexture;

    public WorldTextureRenderer() {
        this(null, true);
    }

    public WorldTextureRenderer(boolean depthTest) {
        this(null, depthTest);
    }

    public WorldTextureRenderer(Identifier boundTexture, boolean depthTest) {
        this.boundTexture = boundTexture;
        this.depthTest = depthTest;
    }

    public static int toggleDebugTest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            logger.error("当前不在世界中，无法测试纹理渲染");
            return 0;
        }

        if (!testHookRegistered) {
            testHookRegistered = true;
            LevelRenderEvents.END_MAIN.register(WorldTextureRenderer::onRenderWorldTest);
        }

        if (!testActive) {
            testActive = true;
            testAnchorPos = mc.player.position().add(0, 0.5, 0);
            ChatNotifier.sendInChatScreen("§a[IRC] 已开启世界纹理渲染测试 (assets/kwrg/texture/gui/logo.png)");
        } else {
            testActive = false;
            testAnchorPos = null;
            ChatNotifier.sendInChatScreen("§e[IRC] 已关闭世界纹理渲染测试。");
        }
        return 1;
    }

    private static void onRenderWorldTest(LevelRenderContext context) {
        if (!testActive || testAnchorPos == null) return;
        PoseStack poseStack = context.poseStack();
        MultiBufferSource.BufferSource bufferSource = context.bufferSource();
        if (poseStack == null || bufferSource == null) return;

        // ==================== 左列：测试开启深度测试 (depthTest = true，会被方块遮挡) ====================
        Vec3 leftBase = testAnchorPos.add(-2.2, 0, 0);

        WorldTextRenderer.renderStyled(poseStack, bufferSource, leftBase.add(-0.5, 4.8, 0), "[Left Column] DepthTest = TRUE (Occluded by blocks)", "neon bold");

        // 1. 绑定纹理默认大小渲染 (1x1 Billboard)
        WorldTextRenderer.render(poseStack, bufferSource, leftBase.add(0, 4.4, 0), "[D1] Bound Texture Default (1x1)");
        TEST_DEPTH_ON_RENDERER.render(poseStack, bufferSource, leftBase.add(0, 3.3, 0));

        // 2. 指定宽高 + 颜色调制 (薄荷绿)
        WorldTextRenderer.render(poseStack, bufferSource, leftBase.add(0, 3.0, 0), "[D2] Custom Size (1.4x0.8) + Tint", 0x00FF87);
        TEST_DEPTH_ON_RENDERER.render(poseStack, bufferSource, leftBase.add(0, 2.1, 0), 1.4F, 0.8F, 0x00FF87);

        // 3. 传入 Identifier + AWT Color 重载
        WorldTextRenderer.render(poseStack, bufferSource, leftBase.add(0, 1.8, 0), "[D3] Explicit Identifier + AwtColor");
        TEST_DEPTH_ON_RENDERER.render(poseStack, bufferSource, TEST_LOGO_TEXTURE, leftBase.add(0, 0.9, 0), 0.8F, 0.8F, new Color(255, 200, 80));

        // 4. 两点 (bottomLeft -> topRight) 3D 世界空间固定平面
        WorldTextRenderer.render(poseStack, bufferSource, leftBase.add(-0.6, 0.6, 0), "[D4] 2-Point World Space (bottomLeft -> topRight)");
        TEST_DEPTH_ON_RENDERER.render(poseStack, bufferSource, leftBase.add(-0.6, -0.5, 0), leftBase.add(0.8, 0.5, 0));


        // ==================== 右列：测试关闭深度测试 (depthTest = false，隔墙透视) ====================
        Vec3 rightBase = testAnchorPos.add(1.8, 0, 0);

        WorldTextRenderer.renderStyled(poseStack, bufferSource, rightBase.add(-0.5, 4.8, 0), "[Right Column] DepthTest = FALSE (See-Through)", "sunset bold");

        // 1. 绑定纹理默认大小渲染 (1x1 透视 Billboard)
        WorldTextRenderer.render(poseStack, bufferSource, rightBase.add(0, 4.4, 0), "[S1] Bound Texture See-Through (1x1)");
        TEST_DEPTH_OFF_RENDERER.render(poseStack, bufferSource, rightBase.add(0, 3.3, 0));

        // 2. 指定宽高 + 颜色调制 (樱花粉)
        WorldTextRenderer.render(poseStack, bufferSource, rightBase.add(0, 3.0, 0), "[S2] Custom Size (1.4x0.8) + Tint", 0xFF758C);
        TEST_DEPTH_OFF_RENDERER.render(poseStack, bufferSource, rightBase.add(0, 2.1, 0), 1.4F, 0.8F, 0xFF758C);

        // 3. 单点自定义 UV 区域裁剪 (只渲染贴图的左上 1/4 或完整 UV)
        WorldTextRenderer.render(poseStack, bufferSource, rightBase.add(0, 1.8, 0), "[S3] Custom UV Region (0,0 -> 1,1)");
        TEST_DEPTH_OFF_RENDERER.render(poseStack, bufferSource, TEST_LOGO_TEXTURE, rightBase.add(0, 0.9, 0), 0.8F, 0.8F, 0.0F, 0.0F, 1.0F, 1.0F, DEFAULT_COLOR);

        // 4. 两点 (bottomLeft -> topRight) 3D 世界空间透视平面
        WorldTextRenderer.render(poseStack, bufferSource, rightBase.add(-0.6, 0.6, 0), "[S4] 2-Point See-Through (bottomLeft -> topRight)");
        TEST_DEPTH_OFF_RENDERER.render(poseStack, bufferSource, rightBase.add(-0.6, -0.5, 0), rightBase.add(0.8, 0.5, 0));

        bufferSource.endBatch();
    }

    public boolean isDepthTest() {
        return this.depthTest;
    }

    public Identifier getBoundTexture() {
        return this.boundTexture;
    }

    public RenderType getRenderType(Identifier texture) {
        if (this.depthTest) {
            return DEPTH_RENDER_TYPES.computeIfAbsent(texture, tex -> RenderType.create(
                    "kwrg_world_tex_depth_" + tex.toDebugFileName(),
                    RenderSetup.builder(TEXTURE_DEPTH_PIPELINE)
                            .withTexture("Sampler0", tex)
                            .sortOnUpload()
                            .createRenderSetup()
            ));
        } else {
            return NO_DEPTH_RENDER_TYPES.computeIfAbsent(texture, tex -> RenderType.create(
                    "kwrg_world_tex_no_depth_" + tex.toDebugFileName(),
                    RenderSetup.builder(TEXTURE_NO_DEPTH_PIPELINE)
                            .withTexture("Sampler0", tex)
                            .sortOnUpload()
                            .createRenderSetup()
            ));
        }
    }

    // 使用构造时绑定的纹理的重载方法
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft) {
        requireBoundTexture();
        render(poseStack, bufferSource, this.boundTexture, bottomLeft, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_COLOR);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, int color) {
        requireBoundTexture();
        render(poseStack, bufferSource, this.boundTexture, bottomLeft, DEFAULT_SIZE, DEFAULT_SIZE, color);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, float width, float height) {
        requireBoundTexture();
        render(poseStack, bufferSource, this.boundTexture, bottomLeft, width, height, DEFAULT_COLOR);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, float width, float height, int color) {
        requireBoundTexture();
        render(poseStack, bufferSource, this.boundTexture, bottomLeft, width, height, color);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight) {
        requireBoundTexture();
        render(poseStack, bufferSource, this.boundTexture, bottomLeft, topRight, DEFAULT_COLOR);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 bottomLeft, Vec3 topRight, int color) {
        requireBoundTexture();
        render(poseStack, bufferSource, this.boundTexture, bottomLeft, topRight, color);
    }

    // 指定左下角的重载方法 (面向相机的 Billboard 模式)
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft) {
        render(poseStack, bufferSource, texture, bottomLeft, DEFAULT_SIZE, DEFAULT_SIZE, 0.0F, 0.0F, 1.0F, 1.0F, DEFAULT_COLOR);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, int color) {
        render(poseStack, bufferSource, texture, bottomLeft, DEFAULT_SIZE, DEFAULT_SIZE, 0.0F, 0.0F, 1.0F, 1.0F, color);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, Color color) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, texture, bottomLeft, DEFAULT_SIZE, DEFAULT_SIZE, 0.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, float width, float height) {
        render(poseStack, bufferSource, texture, bottomLeft, width, height, 0.0F, 0.0F, 1.0F, 1.0F, DEFAULT_COLOR);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, float width, float height, int color) {
        render(poseStack, bufferSource, texture, bottomLeft, width, height, 0.0F, 0.0F, 1.0F, 1.0F, color);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, float width, float height, Color color) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, texture, bottomLeft, width, height, 0.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    // 左下角的世界空间 3D 区域纹理渲染方法
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Identifier texture,
            Vec3 bottomLeft,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int color
    ) {
        if (texture == null || width <= 0.0F || height <= 0.0F) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        poseStack.pushPose();
        poseStack.translate(bottomLeft.x - camPos.x, bottomLeft.y - camPos.y, bottomLeft.z - camPos.z);

        // 对齐相机视角
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(getRenderType(texture));
        int finalColor = ColorUtil.ensureAlpha(color);

        drawQuad(consumer, matrix, 0.0F, 0.0F, width, height, u0, v0, u1, v1, finalColor);

        poseStack.popPose();
    }

    //  指定左下角  和 右上角 的世界空间 3D 区域重载方法
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, Vec3 topRight) {
        render(poseStack, bufferSource, texture, bottomLeft, topRight, 0.0F, 0.0F, 1.0F, 1.0F, DEFAULT_COLOR);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, Vec3 topRight, int color) {
        render(poseStack, bufferSource, texture, bottomLeft, topRight, 0.0F, 0.0F, 1.0F, 1.0F, color);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Identifier texture, Vec3 bottomLeft, Vec3 topRight, Color color) {
        int argb = color != null ? color.getRGB() : DEFAULT_COLOR;
        render(poseStack, bufferSource, texture, bottomLeft, topRight, 0.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    /**
     * 左下角 + 右上角的世界空间 3D 区域纹理渲染方法
     */
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Identifier texture,
            Vec3 bottomLeft,
            Vec3 topRight,
            float u0,
            float v0,
            float u1,
            float v1,
            int color
    ) {
        if (texture == null) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        double dx = topRight.x - bottomLeft.x;
        double dy = topRight.y - bottomLeft.y;
        double dz = topRight.z - bottomLeft.z;

        float targetWidth = (float) Math.sqrt(dx * dx + dz * dz);
        float targetHeight = (float) Math.abs(dy);

        if (targetWidth < 1.0E-4F && targetHeight < 1.0E-4F) {
            render(poseStack, bufferSource, texture, bottomLeft, DEFAULT_SIZE, DEFAULT_SIZE, u0, v0, u1, v1, color);
            return;
        }

        if (targetWidth < 1.0E-4F) {
            render(poseStack, bufferSource, texture, bottomLeft, targetHeight, targetHeight, u0, v0, u1, v1, color);
            return;
        }

        float height = targetHeight > 1.0E-4F ? targetHeight : targetWidth;
        float yaw = (float) Mth.atan2(-dz, dx);

        poseStack.pushPose();
        poseStack.translate(bottomLeft.x - camPos.x, bottomLeft.y - camPos.y, bottomLeft.z - camPos.z);
        poseStack.mulPose(Axis.YP.rotation(yaw));

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(getRenderType(texture));
        int finalColor = ColorUtil.ensureAlpha(color);

        drawQuad(consumer, matrix, 0.0F, 0.0F, targetWidth, height, u0, v0, u1, v1, finalColor);

        poseStack.popPose();
    }

    /** Four world-space corners, ordered bottom-left, bottom-right, top-right, top-left. */
    public void renderQuad(PoseStack poses, MultiBufferSource buffers, Identifier texture,
                           Vec3 bl, Vec3 br, Vec3 tr, Vec3 tl, float u0, float v0, float u1, float v1) {
        renderQuad(poses, buffers, texture, bl, br, tr, tl, u0, v0, u1, v1, DEFAULT_COLOR);
    }

    /** Draws an oriented surface without imposing billboard rotation. */
    public void renderQuad(PoseStack poses, MultiBufferSource buffers, Identifier texture,
                           Vec3 bottomLeft, Vec3 bottomRight, Vec3 topRight, Vec3 topLeft,
                           float u0, float v0, float u1, float v1, int color) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        bottomLeft = bottomLeft.subtract(camera);
        bottomRight = bottomRight.subtract(camera);
        topRight = topRight.subtract(camera);
        topLeft = topLeft.subtract(camera);
        Matrix4f matrix = poses.last().pose();
        VertexConsumer consumer = buffers.getBuffer(getRenderType(texture));
        consumer.addVertex(matrix, (float) bottomLeft.x, (float) bottomLeft.y, (float) bottomLeft.z).setUv(u0, v1).setColor(color);
        consumer.addVertex(matrix, (float) bottomRight.x, (float) bottomRight.y, (float) bottomRight.z).setUv(u1, v1).setColor(color);
        consumer.addVertex(matrix, (float) topRight.x, (float) topRight.y, (float) topRight.z).setUv(u1, v0).setColor(color);
        consumer.addVertex(matrix, (float) topLeft.x, (float) topLeft.y, (float) topLeft.z).setUv(u0, v0).setColor(color);
    }

    // 顶点绘制辅助
    private static void drawQuad(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            float u0,
            float v0,
            float u1,
            float v1,
            int color
    ) {
        // 左下角 (x0, y0) -> UV (u0, v1)
        consumer.addVertex(matrix, x0, y0, 0.0F).setUv(u0, v1).setColor(color);
        // 右下角 (x1, y0) -> UV (u1, v1)
        consumer.addVertex(matrix, x1, y0, 0.0F).setUv(u1, v1).setColor(color);
        // 右上角 (x1, y1) -> UV (u1, v0)
        consumer.addVertex(matrix, x1, y1, 0.0F).setUv(u1, v0).setColor(color);
        // 左上角 (x0, y1) -> UV (u0, v0)
        consumer.addVertex(matrix, x0, y1, 0.0F).setUv(u0, v0).setColor(color);
    }

    private void requireBoundTexture() {
        if (this.boundTexture == null) {
            throw new IllegalStateException("当前 WorldTextureRenderer 未在构造时绑定默认纹理，请调用传入 Identifier texture 的重载方法！");
        }
    }
}
