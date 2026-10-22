package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** A frame-local line batch with vanilla clipping and a fog-free fragment shader. */
public class RouteLineRenderer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/route_lines"))
            .withVertexShader("core/rendertype_lines")
            // Unlike rendertype_lines.fsh, position_color.fsh never applies fog.
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private final DrawableGizmoPrimitives lines = new DrawableGizmoPrimitives();

    public void line(Vec3 start, Vec3 end, int color) {
        if (start.distanceToSqr(end) < 1.0E-12) return;
        lines.addLine(start, end, color, 3.0f);
    }

    public void draw(LevelRenderContext context) {
        var camera = context.levelState().cameraRenderState;
        if (lines.isEmpty() || !camera.initialized) return;

        var buffers = context.bufferSource();
        var vertices = buffers.getBuffer(RenderTypeHolder.LINES);
        // Only lines are queued here. Reuse vanilla near-plane clipping and camera-relative
        // vertex generation, while directing every line into our own render type.
        lines.render(context.poseStack(), ignored -> vertices, camera, camera.viewRotationMatrix);
        buffers.endBatch(RenderTypeHolder.LINES);
    }

    private static final class RenderTypeHolder {
        private static final RenderType LINES = RenderType.create("kwrg_route_lines",
                RenderSetup.builder(PIPELINE).createRenderSetup());
    }
}
