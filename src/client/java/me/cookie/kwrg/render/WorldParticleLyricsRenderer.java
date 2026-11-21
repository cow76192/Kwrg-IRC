package me.cookie.kwrg.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import me.cookie.kwrg.util.lyrics.LyricTimeline;
import me.cookie.kwrg.module.modules.style.ParticleLyricsModule;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class WorldParticleLyricsRenderer {
    private static final double START_DEPTH = 6;
    private static final double MAX_DEPTH = 16;
    private static final double STILL_DISTANCE_SQUARED = 0.12 * 0.12;
    private static final float MAX_WORLD_PIXEL_RADIUS = 0.035f;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double[] SURFACE_CLEARANCE = {0.12, 0.36, 0.65};
    private static final float[] SIZE_FITS = {1f, 0.8f, 0.64f, 0.5f, 0.4f, 0.32f};
    private static final double[] HORIZONTAL = {0.48, 0.32, 0.64, 0.19, 0.07, 0, -0.24};
    private static final double[] VERTICAL = {0, -0.18, -0.32, -0.44, -0.57, -0.72, 0.16, 0.34, 0.54};

    private record Anchor(Vec3 position, double depth, Vec3 right, float scale, int disperseRadius) {
    }

    private record LineAtAnchor(LyricTimeline.Line line, ParticleLyricsRenderer.Row row, Anchor anchor) {
    }

    record TextPlane(Vec3 left, Vec3 right, double bottom, double top) {
        AABB bounds() {
            return new AABB(left.x, bottom, left.z, right.x, top, right.z);
        }

        boolean intersects(AABB block) {
            AABB padded = block.inflate(0.02);
            double lower = Math.max(bottom, padded.minY);
            double upper = Math.min(top, padded.maxY);
            if (lower > upper) return false;

            double y = (lower + upper) * 0.5;
            Vec3 a = new Vec3(left.x, y, left.z);
            Vec3 b = new Vec3(right.x, y, right.z);
            return padded.contains(a) || padded.contains(b) || padded.clip(a, b).isPresent();
        }

        boolean visible(Predicate<Vec3> clear) {
            int columns = Math.max(1, (int) Math.ceil(left.distanceTo(right) / 0.28));
            int rows = Math.max(1, (int) Math.ceil((top - bottom) / 0.24));
            for (int step = 0; step <= columns; step++) {
                int column = (step & 1) == 0 ? step / 2 : columns - step / 2;
                Vec3 point = left.lerp(right, column / (double) columns);
                for (int row = 0; row <= rows; row++) {
                    double y = bottom + (top - bottom) * row / rows;
                    if (!clear.test(new Vec3(point.x, y, point.z))) return false;
                }
            }
            return true;
        }

        boolean onScreen(Vec3 camera, Matrix4f viewProjection) {
            return inside(left, bottom, camera, viewProjection) && inside(left, top, camera, viewProjection)
                    && inside(right, bottom, camera, viewProjection) && inside(right, top, camera, viewProjection);
        }

        private static boolean inside(Vec3 edge, double y, Vec3 camera, Matrix4f viewProjection) {
            var clip = viewProjection.transform(new Vector4f((float) (edge.x - camera.x),
                    (float) (y - camera.y), (float) (edge.z - camera.z), 1));
            return clip.w > 0 && Math.abs(clip.x) <= clip.w * 0.96f && Math.abs(clip.y) <= clip.w * 0.96f;
        }
    }

    private static ClientLevel level;
    private static List<LyricTimeline.Line> track;
    private static int lineIndex = -1;
    private static int lastPlayingLine = -1;
    private static int sequence;
    private static long lastPosition;
    private static long retryAt;
    private static Vec3 lastPlacementOrigin;
    private static double lastDepth;
    private static boolean moved;
    private static ParticleLyricsRenderer.Row pendingRow;
    private static LineAtAnchor current;
    private static final List<LineAtAnchor> FADING = new ArrayList<>();

    private static final class ParticleRenderType {
        private static final RenderType TYPE = RenderType.create("kwrg_world_lyric_particles",
                RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                                .withLocation(Identifier.fromNamespaceAndPath("kwrg", "pipeline/world_lyric_particles"))
                                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                                .withCull(false)
                                .build()))
                        .sortOnUpload()
                        .createRenderSetup());
    }

    private WorldParticleLyricsRenderer() {
    }

    public static void init() {
        LevelRenderEvents.END_MAIN.register(WorldParticleLyricsRenderer::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> reset(null, null)));
    }

    private static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        var playback = ParticleLyricsModule.worldSpace() ? ParticleLyricsModule.playback() : null;
        if (mc.level == null || mc.player == null || playback == null) {
            if (track != null) reset(null, null);
            return;
        }

        long position = playback.position();
        int gatherMs = ParticleLyricsModule.gatherMs();
        int playingLine = LyricTimeline.lineAt(playback.lines(), position);
        int index = ParticleLyricsRenderer.lastGatheringLine(playback.lines(), playingLine, position, gatherMs);
        if (level != mc.level || track != playback.lines()
                || Math.abs(playingLine - lastPlayingLine) > 1 || position < lastPosition - 300) {
            reset(mc.level, playback.lines());
        }
        // A small playback clock correction must not relocate a line that already has an anchor.
        index = lineIndex < 0 ? Math.max(playingLine, Math.min(index, 0)) : Math.clamp(index, lineIndex, lineIndex + 1);
        lastPlayingLine = playingLine;
        lastPosition = position;
        if (index < 0) return;

        Vec3 playerPosition = mc.player.position();
        if (lastPlacementOrigin != null && lastPlacementOrigin.distanceToSqr(playerPosition) > STILL_DISTANCE_SQUARED) {
            moved = true;
        }

        if (index != lineIndex) {
            if (lineIndex >= 0) sequence++;
            lineIndex = index;
            if (current != null) FADING.add(current);
            current = null;
            pendingRow = null;
            retryAt = 0;
        }

        LyricTimeline.Line line = track.get(index);
        int disperseRadius = ParticleLyricsModule.disperseRadius();
        long now = System.nanoTime();
        if (current == null && position < line.end() && (retryAt == 0 || now - retryAt >= 0)) {
            retryAt = now + 200_000_000;
            double desired = preferredDepth(lastDepth, lastPlacementOrigin != null && !moved);
            if (pendingRow == null) pendingRow = ParticleLyricsRenderer.row(line);

            Anchor anchor = findAnchor(context, mc, pendingRow, sideForLine(sequence), desired, disperseRadius);
            if (anchor != null) {
                current = new LineAtAnchor(line, pendingRow, anchor);
                lastPlacementOrigin = playerPosition;
                lastDepth = anchor.depth();
                moved = false;
            }
        }

        if (context.poseStack() == null || context.bufferSource() == null) return;
        int disperseMs = ParticleLyricsModule.disperseMs();
        FADING.removeIf(anchored -> position - anchored.line().end() >= disperseMs);
        boolean drew = false;
        for (LineAtAnchor anchored : FADING)
            drew |= draw(context, mc, anchored, position, gatherMs, disperseMs, disperseRadius);
        drew |= draw(context, mc, current, position, gatherMs, disperseMs, disperseRadius);
        if (drew) context.bufferSource().endBatch(ParticleRenderType.TYPE);
    }

    private static void reset(ClientLevel nextLevel, List<LyricTimeline.Line> nextTrack) {
        level = nextLevel;
        track = nextTrack;
        lineIndex = -1;
        lastPlayingLine = -1;
        sequence = 0;
        lastPosition = 0;
        lastPlacementOrigin = null;
        lastDepth = 0;
        moved = false;
        pendingRow = null;
        current = null;
        FADING.clear();
    }

    static int sideForLine(int sequence) {
        return (sequence & 1) == 0 ? -1 : 1;
    }

    static double preferredDepth(double lastDepth, boolean stationary) {
        return stationary ? Math.min(MAX_DEPTH, lastDepth + 1) : START_DEPTH;
    }

    private static Anchor findAnchor(LevelRenderContext context, Minecraft mc, ParticleLyricsRenderer.Row row,
                                     int side, double desiredDepth, int disperseRadius) {
        if (row.letters().isEmpty()) return null;
        var cameraState = context.levelState().cameraRenderState;
        var viewProjection = new Matrix4f(cameraState.projectionMatrix).mul(cameraState.viewRotationMatrix);
        float requestedScale = worldScale(row.width());
        Anchor preferred = fitAnchor(mc, row, side, desiredDepth, disperseRadius, requestedScale, viewProjection, true);
        return preferred != null ? preferred
                : fitAnchor(mc, row, side, desiredDepth, disperseRadius, requestedScale, viewProjection, false);
    }

    private static Anchor fitAnchor(Minecraft mc, ParticleLyricsRenderer.Row row, int side, double desiredDepth,
                                    int disperseRadius, float requestedScale, Matrix4f viewProjection, boolean preferred) {
        for (float fit : SIZE_FITS) {
            Anchor anchor = findAnchorAtScale(mc, row, side, desiredDepth, disperseRadius, requestedScale * fit,
                    viewProjection, preferred);
            if (anchor != null) return anchor;
        }
        return null;
    }

    private static Anchor findAnchorAtScale(Minecraft mc, ParticleLyricsRenderer.Row row, int side,
                                            double desiredDepth, int disperseRadius, float scale, Matrix4f viewProjection,
                                            boolean preferred) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;

        float particleRadius = Math.max(ParticleLyricsModule.MAX_PARTICLE_SIZE * 0.5f, MAX_WORLD_PIXEL_RADIUS / scale);
        var footprint = row.footprint(disperseRadius, particleRadius);

        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 eye = player.getEyePosition();
        Vec3 forward = player.getViewVector(1);
        Vec3 right = Vec3.directionFromRotation(0, player.getYRot()).cross(UP).normalize();
        Vec3 up = right.cross(forward).normalize();

        var collisionContext = CollisionContext.of(player);
        double vertical = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5);
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double ground = groundAim(player.getY() - eye.y, forward.y, up.y * vertical, desiredDepth);

        Anchor best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (double horizontal : HORIZONTAL) {
            if ((horizontal > 0) != preferred) continue;

            Vec3 start = forward.add(right.scale(horizontal * side * vertical * aspect));
            for (int i = -1; i < VERTICAL.length; i++) {
                double height = i < 0 ? ground : VERTICAL[i];
                if (!Double.isFinite(height) || Math.abs(height) > 0.76) continue;

                Vec3 ray = start.add(up.scale(height * vertical));
                var hit = level.clip(new ClipContext(eye, eye.add(ray.scale(MAX_DEPTH)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK) continue;

                Vec3 location = hit.getLocation();
                double depth = location.subtract(eye).dot(forward);
                if (depth < 0.7) continue;

                double score = anchorScore(horizontal, height, depth, desiredDepth);
                if (score >= bestScore) continue;

                Direction face = hit.getDirection();
                double lift = switch (face) {
                    case UP -> footprint.bottom() * scale + 0.09;
                    case DOWN -> footprint.top() * scale - 0.09;
                    default -> 0.19;
                };

                Vec3 towardsEye = eye.subtract(location).normalize();
                for (double clearance : SURFACE_CLEARANCE) {
                    Vec3 anchor = location.add(towardsEye.scale(clearance)).add(0, lift, 0);
                    Vec3 textRight = facingRight(anchor, camera, right);

                    TextPlane plane = new TextPlane(anchor.add(textRight.scale(footprint.left() * scale)),
                            anchor.add(textRight.scale(footprint.right() * scale)),
                            anchor.y - footprint.bottom() * scale, anchor.y - footprint.top() * scale);
                    if (!plane.onScreen(camera, viewProjection) || !clearOfBlocks(level, collisionContext, plane)
                            || !plane.visible(point -> level.clip(new ClipContext(camera, point, ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS)) continue;

                    bestScore = score;
                    best = new Anchor(anchor, depth, textRight, scale, disperseRadius);
                    break;
                }
            }
        }
        return best;
    }

    static Vec3 facingRight(Vec3 anchor, Vec3 camera, Vec3 fallback) {
        Vec3 right = UP.cross(camera.subtract(anchor)).normalize();
        return right.lengthSqr() == 0 ? fallback : right;
    }

    private static boolean clearOfBlocks(ClientLevel level, CollisionContext context, TextPlane plane) {
        for (BlockPos pos : BlockPos.betweenClosed(plane.bounds().inflate(0.04, 1, 0.04))) {
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            for (AABB shape : state.getShape(level, pos, context).toAabbs()) {
                if (plane.intersects(shape.move(pos.getX(), pos.getY(), pos.getZ()))) return false;
            }
        }
        return true;
    }

    static double groundAim(double relativeHeight, double forwardY, double verticalUp, double depth) {
        return (relativeHeight / depth - forwardY) / verticalUp;
    }

    static double anchorScore(double horizontal, double height, double depth, double desiredDepth) {
        double sidePenalty = horizontal > 0.08 ? 0 : horizontal >= 0 ? 20 : 40;
        return sidePenalty + Math.abs(depth - desiredDepth)
                + Math.abs(horizontal - 0.48) * 3 + Math.abs(height) * 0.25;
    }

    private static boolean draw(LevelRenderContext context, Minecraft mc, LineAtAnchor line, long position,
                                int gatherMs, int disperseMs, int disperseRadius) {
        if (line == null) return false;

        var animation = ParticleLyricsRenderer.animate(line.row(), line.line(), position, gatherMs, disperseMs,
                Math.min(disperseRadius, line.anchor().disperseRadius()));
        if (animation == null) return false;

        float width = line.row().width();
        float scale = Math.min(line.anchor().scale(), worldScale(width));
        Vec3 right = line.anchor().right();
        var pose = context.poseStack();

        var camera = mc.gameRenderer.getMainCamera();
        double depth = line.anchor().position().subtract(camera.position()).dot(new Vec3(camera.forwardVector()));

        float pixelRadius = pixelRadius(depth, context.levelState().cameraRenderState.projectionMatrix.m11(),
                mc.getWindow().getHeight());
        float particleSize = ParticleLyricsModule.particleSize();

        var quad = ParticleLyricsRenderer.ParticleQuad.forPixel(particleSize * 0.5f * scale, pixelRadius);
        int density = ParticleLyricsRenderer.effectiveDensity(ParticleLyricsModule.particleDensity(), particleSize);
        WorldTextRenderer.pushAt(pose, camera, line.anchor().position());
        try {
            var matrix = pose.last().pose();
            var vertices = context.bufferSource().getBuffer(ParticleRenderType.TYPE);
            float radius = quad.radius() / scale;
            animation.draw(density, (x, y, color) -> {
                float centered = x - width * 0.5f;
                float left = (centered - radius) * scale;
                float rightEdge = (centered + radius) * scale;
                float top = -(y - radius) * scale;
                float bottom = -(y + radius) * scale;
                int argb = quad.color(color);
                vertices.addVertex(matrix, (float) (right.x * left), top, (float) (right.z * left)).setColor(argb);
                vertices.addVertex(matrix, (float) (right.x * left), bottom, (float) (right.z * left)).setColor(argb);
                vertices.addVertex(matrix, (float) (right.x * rightEdge), bottom, (float) (right.z * rightEdge)).setColor(argb);
                vertices.addVertex(matrix, (float) (right.x * rightEdge), top, (float) (right.z * rightEdge)).setColor(argb);
            });
        } finally {
            pose.popPose();
        }
        return true;
    }

    static float pixelRadius(double depth, float projectionY, int height) {
        double halfPixel = depth / (Math.max(1, height) * Math.max(0.001f, Math.abs(projectionY)));
        return (float) Math.clamp(halfPixel, 0, MAX_WORLD_PIXEL_RADIUS);
    }

    private static float worldScale(float width) {
        float fontScale = ParticleLyricsRenderer.fontScale();
        return Math.min(WorldTextRenderer.DEFAULT_SCALE * 0.5f * fontScale, worldLineWidth(fontScale) / width);
    }

    static float worldLineWidth(float fontScale) {
        return 4.5f * Math.max(1, fontScale / 2);
    }
}
