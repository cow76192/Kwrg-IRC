package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cookie.kwrg.irc.manager.GemstoneESPManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class GemstoneESPRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-gemstone-esp-renderer");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static boolean loggedFirstFrame = false;

    public static final float LINE_WIDTH = 2.5f;

    // 外轮廓线管线
    private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/gemstone_esp_lines"))
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    // 边缘面填充管线
    private static final RenderPipeline ESP_QUADS_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/gemstone_esp_quads"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final class RenderTypeHolder {
        private static final RenderType LINES = RenderType.create(
                "kwrg_gemstone_esp_lines",
                RenderSetup.builder(ESP_LINES_PIPELINE)
                        .bufferSize(RenderType.BIG_BUFFER_SIZE)
                        .createRenderSetup()
        );
        private static final RenderType QUADS = RenderType.create(
                "kwrg_gemstone_esp_quads",
                RenderSetup.builder(ESP_QUADS_PIPELINE)
                        .bufferSize(RenderType.BIG_BUFFER_SIZE)
                        .createRenderSetup()
        );
    }

    private GemstoneESPRenderer() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        LevelRenderEvents.BEFORE_GIZMOS.register(GemstoneESPRenderer::onWorldRender);
//        LOGGER.info("[GemstoneESP] BEFORE_GIZMOS 渲染监听已注册");
    }

    private static void onWorldRender(LevelRenderContext context) {
        if (!GemstoneESPManager.shouldRender()) return;

        CompiledMesh mesh = GemstoneESPManager.getActiveMesh();
        if (mesh == null || mesh.isEmpty()) return;

        var camera = context.levelState().cameraRenderState;
        if (!camera.initialized) return;

        var buffers = context.bufferSource();
        var poseStack = context.poseStack();

        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
//            LOGGER.info("[GemstoneESP] 渲染器已激活");
        }

        // 边缘面半透明淡化填充
        if (!mesh.quadPrimitives().isEmpty()) {
            VertexConsumer quadVertices = buffers.getBuffer(RenderTypeHolder.QUADS);
            mesh.quadPrimitives().render(poseStack, ignored -> quadVertices, camera, camera.viewRotationMatrix);
            buffers.endBatch(RenderTypeHolder.QUADS);
        }

        // 外轮廓线条
        if (!mesh.linePrimitives().isEmpty()) {
            VertexConsumer lineVertices = buffers.getBuffer(RenderTypeHolder.LINES);
            mesh.linePrimitives().render(poseStack, ignored -> lineVertices, camera, camera.viewRotationMatrix);
            buffers.endBatch(RenderTypeHolder.LINES);
        }
    }

    // 渲染网格数据结构
    public record CompiledMesh(
            DrawableGizmoPrimitives quadPrimitives,
            DrawableGizmoPrimitives linePrimitives
    ) {
        public static final CompiledMesh EMPTY = new CompiledMesh(
                new DrawableGizmoPrimitives(),
                new DrawableGizmoPrimitives()
        );

        public boolean isEmpty() {
            return quadPrimitives.isEmpty() && linePrimitives.isEmpty();
        }
    }

    public static void addQuadFace(
            DrawableGizmoPrimitives quadPrimitives,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            int argb
    ) {
        quadPrimitives.addQuad(
                new Vec3(x1, y1, z1),
                new Vec3(x2, y2, z2),
                new Vec3(x3, y3, z3),
                new Vec3(x4, y4, z4),
                argb
        );
    }

    public static void addLineEdge(
            DrawableGizmoPrimitives linePrimitives,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            int argb
    ) {
        linePrimitives.addLine(
                new Vec3(x1, y1, z1),
                new Vec3(x2, y2, z2),
                argb,
                LINE_WIDTH
        );
    }
}