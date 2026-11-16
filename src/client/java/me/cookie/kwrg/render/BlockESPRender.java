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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class BlockESPRender {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-block-esp-renderer");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private record MeshSource(BooleanSupplier enabled, Supplier<CompiledMesh> mesh, IntSupplier opacity) {}
    private static final List<MeshSource> SOURCES = new ArrayList<>();

    /** Register once during client initialization. Meshes must be immutable after publication. */
    public static void register(BooleanSupplier enabled, Supplier<CompiledMesh> mesh, IntSupplier opacity) {
        SOURCES.add(new MeshSource(enabled, mesh, opacity));
        init();
    }

    public static final float LINE_WIDTH = 2.5f;

    // 外轮廓线管线
    private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/block_esp_lines"))
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
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/block_esp_quads"))
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
                "kwrg_block_esp_lines",
                RenderSetup.builder(ESP_LINES_PIPELINE)
                        .bufferSize(RenderType.BIG_BUFFER_SIZE)
                        .createRenderSetup()
        );
        private static final RenderType QUADS = RenderType.create(
                "kwrg_block_esp_quads",
                RenderSetup.builder(ESP_QUADS_PIPELINE)
                        .bufferSize(RenderType.BIG_BUFFER_SIZE)
                        .createRenderSetup()
        );
    }

    private BlockESPRender() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        LevelRenderEvents.BEFORE_GIZMOS.register(BlockESPRender::onWorldRender);
    }

    private static void onWorldRender(LevelRenderContext context) {
        if (!context.levelState().cameraRenderState.initialized) return;
        for (MeshSource source : SOURCES) {
            if (source.enabled().getAsBoolean()) {
                renderMesh(context, source.mesh().get(), source.opacity().getAsInt() / 100.0f);
            }
        }
    }

    public static void renderMesh(LevelRenderContext context, CompiledMesh mesh, float opacity) {
        if (mesh == null || mesh.isEmpty() || opacity <= 0.0f) return;
        var camera = context.levelState().cameraRenderState;
        if (!camera.initialized) return;
        var buffers = context.bufferSource();
        var poseStack = context.poseStack();
        if (!mesh.quadPrimitives().isEmpty()) {
            VertexConsumer vertices = new OpacityVertexConsumer(buffers.getBuffer(RenderTypeHolder.QUADS), opacity);
            mesh.quadPrimitives().render(poseStack, ignored -> vertices, camera, camera.viewRotationMatrix);
            buffers.endBatch(RenderTypeHolder.QUADS);
        }
        if (!mesh.linePrimitives().isEmpty()) {
            VertexConsumer vertices = new OpacityVertexConsumer(buffers.getBuffer(RenderTypeHolder.LINES), opacity);
            mesh.linePrimitives().render(poseStack, ignored -> vertices, camera, camera.viewRotationMatrix);
            buffers.endBatch(RenderTypeHolder.LINES);
        }
    }

    /** Build a block overlay using caller-supplied ARGB outline and fill colors. */
    public static CompiledMesh compileBlocks(LongSet blocks, int lineArgb, int fillArgb) {
        if (blocks.isEmpty()) return CompiledMesh.EMPTY;
        var quads = new DrawableGizmoPrimitives();
        var lines = new DrawableGizmoPrimitives();
        for (long packed : blocks) {
            int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
            for (Direction face : Direction.values()) {
                if (blocks.contains(BlockPos.asLong(x + face.getStepX(), y + face.getStepY(), z + face.getStepZ()))) continue;
                // The four vertices of each outward face, in consistent winding order.
                int[][] corners = switch (face) {
                    case DOWN -> new int[][]{{0,0,0},{1,0,0},{1,0,1},{0,0,1}};
                    case UP -> new int[][]{{0,1,0},{0,1,1},{1,1,1},{1,1,0}};
                    case NORTH -> new int[][]{{0,0,0},{0,1,0},{1,1,0},{1,0,0}};
                    case SOUTH -> new int[][]{{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
                    case WEST -> new int[][]{{0,0,0},{0,0,1},{0,1,1},{0,1,0}};
                    case EAST -> new int[][]{{1,0,0},{1,1,0},{1,1,1},{1,0,1}};
                };
                addQuadFace(quads,
                        x+corners[0][0], y+corners[0][1], z+corners[0][2],
                        x+corners[1][0], y+corners[1][1], z+corners[1][2],
                        x+corners[2][0], y+corners[2][1], z+corners[2][2],
                        x+corners[3][0], y+corners[3][1], z+corners[3][2], fillArgb);
                for (int i = 0; i < 4; i++) {
                    int[] start = corners[i], end = corners[(i + 1) % 4];
                    addLineEdge(lines, x+start[0], y+start[1], z+start[2],
                            x+end[0], y+end[1], z+end[2], lineArgb);
                }
            }
        }
        return new CompiledMesh(quads, lines);
    }

    private record OpacityVertexConsumer(VertexConsumer delegate, float opacity) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return setColor(ARGB.color(alpha, red, green, blue));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            delegate.setColor(ARGB.multiplyAlpha(argb, opacity));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
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
