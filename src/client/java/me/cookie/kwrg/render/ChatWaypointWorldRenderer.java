package me.cookie.kwrg.render;

import java.util.List;
import me.cookie.kwrg.skyblock.route.ChatWaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

public final class ChatWaypointWorldRenderer {

    private static final GizmoStyle BLOCK_STYLE = GizmoStyle.strokeAndFill(0xFF1E2836, 1.0f, 0x8011161F);
    private static final int BEAM_COLOR = 0xFF2B3648;
    private static final int COLOR = 0xFF9FB6CC;
    private static final int PATH_COLOR = 0xFFA64DFF;

    private ChatWaypointWorldRenderer() {
    }

    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(ChatWaypointWorldRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        List<ChatWaypointManager.RenderNode> nodes = ChatWaypointManager.renderNodes();
        if (nodes.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !context.levelState().cameraRenderState.initialized) return;

        float animationTime = Math.floorMod(mc.level.getGameTime(), 40)
                + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        WaypointRenderer renderer = new WaypointRenderer(context, animationTime);
        Vec3 cameraPos = context.levelState().cameraRenderState.pos;

        for (ChatWaypointManager.RenderNode node : nodes) {
            Gizmos.cuboid(node.pos(), BLOCK_STYLE).setAlwaysOnTop();
            renderer.beam(node.pos(), BEAM_COLOR);
            Gizmos.billboardTextOverBlock(node.label() + " §b[" + (int) cameraPos.distanceTo(Vec3.atCenterOf(node.pos())) + "m]", node.pos(), 0, COLOR, 1.0f);
            if (!node.path().isEmpty()) renderer.path(node.path(), PATH_COLOR);
        }

        renderer.draw();
    }
}
