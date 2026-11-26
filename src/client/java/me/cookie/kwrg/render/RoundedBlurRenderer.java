package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wispforest.owo.mixin.ui.access.GuiGraphicsExtractorAccessor;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public final class RoundedBlurRenderer {

    public static final RenderPipeline ROUNDED_BLUR_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/rounded_blur"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("kwrg", "core/rounded_blur"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("kwrg", "core/rounded_blur"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .build()
    );

    private static @Nullable GpuTexture backdropTexture;
    private static @Nullable GpuTextureView backdropTextureView;
    private static int backdropWidth = -1;
    private static int backdropHeight = -1;

    private RoundedBlurRenderer() {}

    public static void submit(OwoUIGraphics graphics, float x, float y, float width, float height,
                              float radius, float directions, float blurSize, float alpha) {
        if (graphics == null || width <= 0.0f || height <= 0.0f || alpha <= 0.005f) return;

        // 提前确认主帧缓冲有效，避免提交无法绑定背景纹理的绘制状态
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        if (mainTarget == null || mainTarget.getColorTexture() == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = ((GuiGraphicsExtractorAccessor) (Object) graphics).owo$getScissorStack().peek();
        ScreenRectangle bounds = computeBounds(pose, x, y, x + width, y + height, scissor);
        if (bounds == null) return;

        RoundedBlurElementRenderState state = new RoundedBlurElementRenderState(
                ROUNDED_BLUR_PIPELINE,
                TextureSetup.noTexture(),
                pose,
                x, y, width, height,
                radius, alpha,
                scissor,
                bounds
        );

        graphics.guiRenderState.addGuiElement(state);
    }

    /** 供 RoundedBlurGuiRendererMixin 在 executeDraw 阶段拷贝当前屏幕画面到独立离屏纹理 */
    public static @Nullable GpuTextureView captureBackdrop(CommandEncoder encoder) {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        if (mainTarget == null) return null;

        GpuTexture mainColor = mainTarget.getColorTexture();
        int width = mainTarget.width;
        int height = mainTarget.height;
        if (mainColor == null || width <= 0 || height <= 0) return null;

        ensureBackdrop(width, height);
        if (backdropTexture == null || backdropTextureView == null) return null;

        encoder.copyTextureToTexture(mainColor, backdropTexture, 0, 0, 0, 0, 0, width, height);
        return backdropTextureView;
    }

    private static void ensureBackdrop(int width, int height) {
        if (backdropTexture != null && backdropTextureView != null
                && backdropWidth == width && backdropHeight == height) {
            return;
        }
        close();
        backdropTexture = RenderSystem.getDevice().createTexture(
                () -> "kwrg:rounded_blur_backdrop",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        backdropTextureView = RenderSystem.getDevice().createTextureView(backdropTexture);
        backdropWidth = width;
        backdropHeight = height;
    }

    public static void close() {
        if (backdropTextureView != null) {
            backdropTextureView.close();
            backdropTextureView = null;
        }
        if (backdropTexture != null) {
            backdropTexture.close();
            backdropTexture = null;
        }
        backdropWidth = -1;
        backdropHeight = -1;
    }

    private static @Nullable ScreenRectangle computeBounds(Matrix3x2fc pose, float x1, float y1, float x2, float y2,
                                                           @Nullable ScreenRectangle scissor) {
        Vector2f p0 = pose.transformPosition(x1, y1, new Vector2f());
        Vector2f p1 = pose.transformPosition(x2, y1, new Vector2f());
        Vector2f p2 = pose.transformPosition(x2, y2, new Vector2f());
        Vector2f p3 = pose.transformPosition(x1, y2, new Vector2f());

        int minX = Mth.floor(Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x)));
        int minY = Mth.floor(Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y)));
        int maxX = Mth.ceil(Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x)));
        int maxY = Mth.ceil(Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y)));

        ScreenRectangle rect = new ScreenRectangle(minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY));
        return scissor != null ? scissor.intersection(rect) : rect;
    }

    private record RoundedBlurElementRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float alpha,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer consumer) {
            int alphaStep = Mth.clamp(Math.round(this.alpha * 100.0f), 0, 100);
            float radiusRatio = Mth.clamp(this.radius / Math.max(this.height, 1.0f), 0.0f, 0.5f);
            int radiusStep = Mth.clamp(Math.round(radiusRatio * 100.0f), 0, 50);

            float u0 = alphaStep * 4.0f + 1.0f;
            float u1 = u0 + 1.0f;
            float v0 = radiusStep * 4.0f + 1.0f;
            float v1 = v0 + 1.0f;

            float x1 = this.x;
            float y1 = this.y;
            float x2 = this.x + this.width;
            float y2 = this.y + this.height;

            consumer.addVertexWith2DPose(this.pose, x1, y1).setUv(u0, v0).setColor(255, 255, 255, 255);
            consumer.addVertexWith2DPose(this.pose, x1, y2).setUv(u0, v1).setColor(255, 255, 255, 255);
            consumer.addVertexWith2DPose(this.pose, x2, y2).setUv(u1, v1).setColor(255, 255, 255, 255);
            consumer.addVertexWith2DPose(this.pose, x2, y1).setUv(u1, v0).setColor(255, 255, 255, 255);
        }
    }
}