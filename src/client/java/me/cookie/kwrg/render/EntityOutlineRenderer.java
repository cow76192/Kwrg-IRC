package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

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
    private static final RenderPipeline FILL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/entity_outline_fill"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    private final DrawableGizmoPrimitives outlines = new DrawableGizmoPrimitives();
    private final DrawableGizmoPrimitives faces = new DrawableGizmoPrimitives();

    public static void init(BooleanSupplier manualEsp, BooleanSupplier automaticEsp, IntSupplier color) {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> render(context, manualEsp, automaticEsp, color));
    }

    private static void render(LevelRenderContext context, BooleanSupplier manualEsp, BooleanSupplier automaticEsp,
                               IntSupplier color) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !context.levelState().cameraRenderState.initialized) return;

        boolean renderAllEntities = manualEsp.getAsBoolean();
        if (!renderAllEntities && !automaticEsp.getAsBoolean()) return;

        var renderer = new EntityOutlineRenderer();
        int outlineColor = color.getAsInt();
        var tickRate = client.level.tickRateManager();
        var deltaTracker = client.getDeltaTracker();
        for (var entity : client.level.entitiesForRendering()) {
            if (entity == client.player || entity.isRemoved()
                    || (!renderAllEntities && entity instanceof ArmorStand)) continue;
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(!tickRate.isEntityFrozen(entity));
            renderer.outline(entity, partialTick, outlineColor);
        }
        renderer.draw(context);
    }

    public void outline(Entity entity, float partialTick, int argb) {
        var offset = entity.getPosition(partialTick).subtract(entity.position());
        var bounds = entity.getBoundingBox().move(offset);
        new CuboidGizmo(bounds, GizmoStyle.stroke(argb, 2.5f), false).emit(outlines, 1.0f);
        new CuboidGizmo(bounds, GizmoStyle.fill(ARGB.multiplyAlpha(argb, 0.25f)), false).emit(faces, 1.0f);
    }

    public void draw(LevelRenderContext context) {
        var camera = context.levelState().cameraRenderState;
        if (outlines.isEmpty() || !camera.initialized) return;

        var buffers = context.bufferSource();
        var fillVertices = buffers.getBuffer(OutlineRenderType.FILL);
        faces.render(context.poseStack(), ignored -> fillVertices, camera, camera.viewRotationMatrix);
        buffers.endBatch(OutlineRenderType.FILL);

        var vertices = buffers.getBuffer(OutlineRenderType.TYPE);
        outlines.render(context.poseStack(), ignored -> vertices, camera, camera.viewRotationMatrix);
        buffers.endBatch(OutlineRenderType.TYPE);
    }

    private static final class OutlineRenderType {
        private static final RenderType TYPE = RenderType.create("kwrg_entity_outline",
                RenderSetup.builder(PIPELINE).createRenderSetup());
        private static final RenderType FILL = RenderType.create("kwrg_entity_outline_fill",
                RenderSetup.builder(FILL_PIPELINE).createRenderSetup());
    }
}
