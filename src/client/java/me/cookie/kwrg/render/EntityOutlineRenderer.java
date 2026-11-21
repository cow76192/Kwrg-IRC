package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** Draws a frame-local batch of colored, depth-independent entity bounds. */
public final class EntityOutlineRenderer {
    public enum Target {
        PLAYER, MONSTER, ANIMAL, OTHER;

        static Target forEntityType(EntityType<?> type) {
            if (type == EntityTypes.PLAYER) return PLAYER;
            return switch (type.getCategory()) {
                case MONSTER -> MONSTER;
                case MISC -> OTHER;
                default -> ANIMAL;
            };
        }
    }

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/entity_outline"))
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/position_color")
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("Globals", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();
    private static final RenderPipeline FILL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/entity_outline_fill"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    private final DrawableGizmoPrimitives outlines = new DrawableGizmoPrimitives();
    private final DrawableGizmoPrimitives faces = new DrawableGizmoPrimitives();

    public static void init(BooleanSupplier manualEsp, BooleanSupplier automaticEsp, IntSupplier color,
                            Predicate<Target> showsTarget) {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> render(context, manualEsp, automaticEsp, color, showsTarget));
    }

    private static void render(LevelRenderContext context, BooleanSupplier manualEsp, BooleanSupplier automaticEsp,
                               IntSupplier color, Predicate<Target> showsTarget) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !context.levelState().cameraRenderState.initialized) return;

        boolean mineshaft = automaticEsp.getAsBoolean();
        if (!mineshaft && !manualEsp.getAsBoolean()) return;

        var targets = mineshaft ? EnumSet.of(Target.MONSTER, Target.ANIMAL) : EnumSet.allOf(Target.class);
        targets.removeIf(showsTarget.negate());
        if (targets.isEmpty()) return;

        var renderer = new EntityOutlineRenderer();
        int outlineColor = color.getAsInt();
        var tickRate = client.level.tickRateManager();
        var deltaTracker = client.getDeltaTracker();
        for (var entity : client.level.entitiesForRendering()) {
            if (entity == client.player || entity.isRemoved()
                    || !targets.contains(Target.forEntityType(entity.getType()))) continue;
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(!tickRate.isEntityFrozen(entity));
            renderer.outline(entity, partialTick, outlineColor);
        }
        if (targets.contains(Target.OTHER)) renderer.outlineChests(client, outlineColor);
        renderer.draw(context);
    }

    private void outlineChests(Minecraft client, int argb) {
        var level = client.level;
        var center = client.player.chunkPosition();
        var chunks = level.getChunkSource();
        int radius = client.options.getEffectiveRenderDistance();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                var chunk = chunks.getChunk(x, z, false);
                if (chunk == null) continue;
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    var state = blockEntity.getBlockState();
                    if (blockEntity.isRemoved() || !(state.getBlock() instanceof AbstractChestBlock<?>)) continue;
                    var pos = blockEntity.getBlockPos();
                    var shape = state.getShape(level, pos);
                    if (!shape.isEmpty()) outline(shape.bounds().move(pos), argb);
                }
            }
        }
    }

    public void outline(Entity entity, float partialTick, int argb) {
        var offset = entity.getPosition(partialTick).subtract(entity.position());
        outline(entity.getBoundingBox().move(offset), argb);
    }

    private void outline(AABB bounds, int argb) {
        new CuboidGizmo(bounds, GizmoStyle.stroke(argb, 2.5f), false).emit(outlines, 1.0f);
        new CuboidGizmo(bounds, GizmoStyle.fill(ARGB.multiplyAlpha(argb, 0.25f)), false).emit(faces, 1.0f);
    }

    public void draw(LevelRenderContext context) {
        var camera = context.levelState().cameraRenderState;
        if (!camera.initialized) return;
        OverlayGizmos.submit(context, faces, OutlineRenderType.FILL, 1.0f);
        OverlayGizmos.submit(context, outlines, OutlineRenderType.TYPE, 1.0f);
    }

    private static final class OutlineRenderType {
        private static final RenderType TYPE = RenderType.create("kwrg_entity_outline",
                RenderSetup.builder(PIPELINE).createRenderSetup());
        private static final RenderType FILL = RenderType.create("kwrg_entity_outline_fill",
                RenderSetup.builder(FILL_PIPELINE).createRenderSetup());
    }
}
