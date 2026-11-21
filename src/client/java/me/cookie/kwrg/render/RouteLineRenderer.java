package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
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
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("Globals", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
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
        if (!camera.initialized) return;
        OverlayGizmos.submit(context, lines, RenderTypeHolder.LINES, 1.0f);
    }

    private static final class RenderTypeHolder {
        private static final RenderType LINES = RenderType.create("kwrg_route_lines",
                RenderSetup.builder(PIPELINE).createRenderSetup());
    }
}
