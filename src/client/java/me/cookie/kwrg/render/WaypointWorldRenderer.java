package me.cookie.kwrg.render;

import java.util.List;
import me.cookie.kwrg.skyblock.route.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

public final class WaypointWorldRenderer {

    private static final GizmoStyle BLOCK_STYLE = GizmoStyle.strokeAndFill(0xFF00FF88, 1.0f, 0x8000FF88);
    private static final int TEXT_COLOR = 0xFF00FF88;

    private WaypointWorldRenderer() {
    }

    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(WaypointWorldRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !context.levelState().cameraRenderState.initialized) return;
        float animationTime = Math.floorMod(mc.level.getGameTime(), 40)
                + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        WaypointRenderer renderer = new WaypointRenderer(context, animationTime);

        List<WaypointManager.RenderNode> nodes = WaypointManager.renderNodes();
        for (WaypointManager.RenderNode node : nodes) {
            Gizmos.cuboid(node.pos(), BLOCK_STYLE).setAlwaysOnTop();
            renderer.waypoint(node.pos(), TEXT_COLOR, false, node.tracer());
            if (node.label() != null) {
                Gizmos.billboardTextOverBlock(node.label(), node.pos(), 0, TEXT_COLOR, 1.0f);
            }
        }

        renderer.draw();
    }
}
