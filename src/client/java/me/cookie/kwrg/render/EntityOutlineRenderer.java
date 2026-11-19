package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/** Draws a frame-local batch of colored, depth-independent entity bounds. */
public final class EntityOutlineRenderer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/entity_outline"))
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    private final DrawableGizmoPrimitives outlines = new DrawableGizmoPrimitives();

    public void outline(Entity entity, float partialTick, int argb) {
        var offset = entity.getPosition(partialTick).subtract(entity.position());
        var bounds = entity.getBoundingBox().move(offset);
        new CuboidGizmo(bounds, GizmoStyle.stroke(argb, 2.5f), false).emit(outlines, 1.0f);
    }

    public void draw(LevelRenderContext context) {
        var camera = context.levelState().cameraRenderState;
        if (outlines.isEmpty() || !camera.initialized) return;

        var buffers = context.bufferSource();
        var vertices = buffers.getBuffer(OutlineRenderType.TYPE);
        outlines.render(context.poseStack(), ignored -> vertices, camera, camera.viewRotationMatrix);
        buffers.endBatch(OutlineRenderType.TYPE);
    }

    private static final class OutlineRenderType {
        private static final RenderType TYPE = RenderType.create("kwrg_entity_outline",
                RenderSetup.builder(PIPELINE).createRenderSetup());
    }
}
