package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** Frame-local waypoint overlays, independent of chunk distance and world fog. */
public final class WaypointRenderer {
    private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
    // The vanilla line vertex shader shrinks view-space coordinates by this factor.
    private static final float LINE_VIEW_SHRINK = 1.0f - 1.0f / 256.0f;
    private static final double OVERLAY_DISTANCE = 64.0;
    private static final float BEAM_HEIGHT = 2048.0f;
    private static final Identifier BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");
    private static final RenderPipeline BEAM_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/waypoint_beam"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private final LevelRenderContext context;
    private final Vec3 cameraPosition;
    private final Vec3 projectionOffset;
    private final Vec3 projectionOrigin;
    private final Matrix4f viewMatrix;
    private final Matrix4f projectionMatrix;
    private final Matrix4f inverseViewProjection;
    private final Vec3 tracerStart;
    private final float animationTime;
    private final RouteLineRenderer tracers = new RouteLineRenderer();
    private final List<Beam> beams = new ArrayList<>();

    public static void captureWorldProjection(Matrix4fc projection) {
        WORLD_PROJECTION.set(projection);
    }

    public WaypointRenderer(LevelRenderContext context, float animationTime) {
        this.context = context;
        this.cameraPosition = context.levelState().cameraRenderState.pos;
        this.animationTime = animationTime;
        // Camera rotation is applied globally by LevelRenderer; the callback pose
        // stack contains only local transforms. Both are needed for view-space tests.
        this.viewMatrix = new Matrix4f().scaling(LINE_VIEW_SHRINK)
                .mul(context.levelState().cameraRenderState.viewRotationMatrix)
                .mul(context.poseStack().last().pose());
        // GameRenderer adds walking/hurt bobbing to the uploaded projection, rather
        // than cameraRenderState.projectionMatrix or the callback pose stack.
        this.projectionMatrix = new Matrix4f(WORLD_PROJECTION);
        this.inverseViewProjection = new Matrix4f(projectionMatrix).mul(viewMatrix).invert();
        Matrix4f inverseView = new Matrix4f(viewMatrix).invert();
        Vector3f eye = inverseView.transformPosition(new Vector3f());
        this.projectionOffset = new Vec3(eye.x(), eye.y(), eye.z());
        this.projectionOrigin = cameraPosition.add(projectionOffset);
        // Unproject the crosshair using the exact matrices used by the line shader.
        // Keep it far enough from the eye to survive vanilla's near-plane clipping.
        Vector3f origin = inverseViewProjection.transformProject(new Vector3f(0, 0, 0.9f));
        this.tracerStart = cameraPosition.add(origin.x(), origin.y(), origin.z());
    }

    public void waypoint(BlockPos pos, int color, boolean showBeam) {
        Vec3 base = Vec3.atBottomCenterOf(pos).subtract(projectionOrigin);
        Vec3 target = base.add(0, 0.5, 0);
        double distance = Math.max(base.length(), target.length());
        // Uniform camera-relative scaling preserves the perspective projection,
        // while keeping distant markers in front of the world's far clipping plane.
        double scale = Math.min(1.0, OVERLAY_DISTANCE / Math.max(1.0, distance));
        Vec3 projectedTarget = projectionOffset.add(target.scale(scale));
        Vector3f viewTarget = viewMatrix.transformPosition(
                new Vector3f((float) projectedTarget.x(), (float) projectedTarget.y(), (float) projectedTarget.z()));
        Vector4f clip = projectionMatrix.transform(new Vector4f(viewTarget, 1));
        if (clip.w() > 0 && clip.z() >= -clip.w()
                && Math.abs(clip.x()) <= clip.w() && Math.abs(clip.y()) <= clip.w()) {
            tracers.line(tracerStart, cameraPosition.add(projectedTarget), color);
        } else if (viewTarget.lengthSquared() > 1.0E-12f) {
            // Do not divide by a negative W: that would reverse the direction of
            // targets behind the camera. Point to the corresponding screen edge.
            float x = clip.x();
            float y = clip.y();
            float extent = Math.max(Math.abs(x), Math.abs(y));
            if (extent < 1.0E-6f) {
                // Directly behind has no unique left/right direction; use the bottom edge.
                x = 0;
                y = -1;
                extent = 1;
            }
            Vector3f edge = inverseViewProjection.transformProject(
                    new Vector3f(x / extent * 0.96f, y / extent * 0.96f, 0));
            tracers.line(tracerStart, cameraPosition.add(edge.x(), edge.y(), edge.z()), color);
        }
        if (showBeam) {
            float radius = (float) Math.max(1.0, distance / 96.0);
            beams.add(new Beam(projectionOffset.add(base.scale(scale)), (float) scale, radius, color));
        }
    }

    public void draw() {
        if (!beams.isEmpty()) {
            var buffers = context.bufferSource();
            var vertices = buffers.getBuffer(BeamRenderType.TYPE);
            for (Beam beam : beams) drawBeam(context.poseStack(), vertices, beam);
            buffers.endBatch(BeamRenderType.TYPE);
        }
        tracers.draw(context);
    }

    private void drawBeam(PoseStack poses, VertexConsumer vertices, Beam beam) {
        poses.pushPose();
        try {
            poses.translate(beam.offset.x(), beam.offset.y(), beam.offset.z());
            poses.scale(beam.scale, beam.scale, beam.scale);
            float scroll = -animationTime * 0.2f;
            scroll -= (float) Math.floor(scroll);

            poses.pushPose();
            try {
                poses.mulPose(Axis.YP.rotationDegrees(animationTime * 2.25f - 45.0f));
                column(poses.last(), vertices, beam.radius * 0.2f, beam.color, scroll);
            } finally {
                poses.popPose();
            }
            column(poses.last(), vertices, beam.radius * 0.25f, (beam.color & 0x00FFFFFF) | 0x20000000, scroll);
        } finally {
            poses.popPose();
        }
    }

    private static void column(PoseStack.Pose pose, VertexConsumer vertices, float radius, int color, float scroll) {
        float topV = BEAM_HEIGHT * 0.5f / radius + scroll;
        side(pose, vertices, -radius, -radius, radius, -radius, color, scroll, topV);
        side(pose, vertices, radius, -radius, radius, radius, color, scroll, topV);
        side(pose, vertices, radius, radius, -radius, radius, color, scroll, topV);
        side(pose, vertices, -radius, radius, -radius, -radius, color, scroll, topV);
    }

    private static void side(PoseStack.Pose pose, VertexConsumer vertices, float x1, float z1,
                             float x2, float z2, int color, float bottomV, float topV) {
        vertices.addVertex(pose, x1, 0, z1).setUv(0, bottomV).setColor(color);
        vertices.addVertex(pose, x2, 0, z2).setUv(1, bottomV).setColor(color);
        vertices.addVertex(pose, x2, BEAM_HEIGHT, z2).setUv(1, topV).setColor(color);
        vertices.addVertex(pose, x1, BEAM_HEIGHT, z1).setUv(0, topV).setColor(color);
    }

    private record Beam(Vec3 offset, float scale, float radius, int color) {}

    private static final class BeamRenderType {
        private static final RenderType TYPE = RenderType.create("kwrg_waypoint_beam",
                RenderSetup.builder(BEAM_PIPELINE).withTexture("Sampler0", BEAM_TEXTURE).createRenderSetup());
    }
}
