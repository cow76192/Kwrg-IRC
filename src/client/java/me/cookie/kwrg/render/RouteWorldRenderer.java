package me.cookie.kwrg.render;

import java.util.List;
import me.cookie.kwrg.skyblock.route.RouteManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class RouteWorldRenderer {

    private static final GizmoStyle BLOCK_STYLE = GizmoStyle.strokeAndFill(0xFF00E5FF, 1.0f, 0x8000E5FF);
    private static final int TEXT_COLOR = 0xFF00E5FF;
    private static final int LINE_COLOR = 0xFF00E5FF;

    private RouteWorldRenderer() {
    }

    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(RouteWorldRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        List<RouteManager.Entry> entries = RouteManager.entries();
        if (!RouteManager.isVisible() || entries.isEmpty()) return;

        var camera = context.levelState().cameraRenderState;
        if (!camera.initialized) return;

        Vec3 cameraPosition = camera.pos;
        var up = camera.orientation.transform(new Vector3f(0, 1, 0));
        Vec3 cameraUp = new Vec3(up.x(), up.y(), up.z());

        RouteLineRenderer lines = RouteManager.isShowLine() ? new RouteLineRenderer() : null;
        Vec3 firstBlockTop = null;
        Vec3 previousNode = null;
        int index = 0;

        for (RouteManager.Entry entry : entries) {
            BlockPos pos = entry.visible ? RouteManager.blockPos(entry) : null;
            if (pos == null) {
                previousNode = null;
                index++;
                continue;
            }
            Gizmos.cuboid(pos, BLOCK_STYLE).setAlwaysOnTop();
            Gizmos.billboardTextOverBlock(RouteManager.displayName(entry, index), pos, 0, TEXT_COLOR, 1.0f);

            if (lines != null) {
                Vec3 node = visibleLinePoint(pos, cameraPosition, cameraUp);
                Vec3 blockTop = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
                if (index == 0) firstBlockTop = blockTop;
                lines.line(blockTop, node, LINE_COLOR);
                if (previousNode != null) lines.line(previousNode, blockTop, LINE_COLOR);
                previousNode = node;
            }

            index++;
        }

        if (lines != null && index > 1 && previousNode != null && firstBlockTop != null) {
            lines.line(previousNode, firstBlockTop, LINE_COLOR);
        }

        if (lines != null) lines.draw(context);
    }

    private static Vec3 linePoint(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, 1 + (double) Avatar.DEFAULT_EYE_HEIGHT, 0);
    }

    private static Vec3 visibleLinePoint(BlockPos pos, Vec3 cameraPosition, Vec3 cameraUp) {
        Vec3 node = linePoint(pos);
        double proximity = Math.max(0, 1 - node.distanceTo(cameraPosition) / 0.75);
        double offset = 0.18 * proximity * proximity * (3 - 2 * proximity);
        return node.subtract(cameraUp.scale(offset));
    }
}
