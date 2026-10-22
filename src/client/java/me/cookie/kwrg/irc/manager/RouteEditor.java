package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class RouteEditor {
    private static boolean enabled;

    private RouteEditor() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int setEnabled(boolean value) {
        enabled = value;
        ChatNotifier.sendInChatScreen(enabled
                ? "§a[Route] 快速编辑已开启，按住 Alt＋左键方块添加节点（请先在 Route 中选择路线）"
                : "§e[Route] 快速编辑已关闭");
        return 1;
    }

    public static int addNode(String name, BlockPos pos, int position) {
        return addNode(name, pos.getX(), pos.getY(), pos.getZ(), position);
    }

    public static int addNode(String name, double x, double y, double z, int position) {
        if (!WaypointManager.isValidCoord(x, y, z)) {
            ChatNotifier.send("§c[Route] 坐标无效或超出范围", NotificationType.ERR);
            return 0;
        }

        String formattedX = WaypointManager.formatCoord(x);
        String formattedY = WaypointManager.formatCoord(y);
        String formattedZ = WaypointManager.formatCoord(z);

        int index = RouteManager.add(name, formattedX, formattedY, formattedZ, position);
        if (index < 0) return 0;

        ChatNotifier.send("§a[Route] 已添加 | " + (index + 1) + ".["
                + RouteManager.displayName(RouteManager.entries().get(index), index) + "] 坐标: ("
                + formattedX + ", " + formattedY + ", " + formattedZ + ")", NotificationType.SUCCESS);
        return 1;
    }

    public static void onMouseButton(long window, MouseButtonInfo button, int action) {
        if (!enabled || action != GLFW.GLFW_PRESS || button.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !button.hasAltDown()) return;

        Minecraft client = Minecraft.getInstance();
        if (window != client.getWindow().handle() || client.player == null || client.level == null
                || client.screen != null || client.getOverlay() != null || !client.isWindowActive()
                || !client.mouseHandler.isMouseGrabbed()) return;

        if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            int position = RouteManager.entries().size() + 1;
            addNode("", hit.getBlockPos(), position);
        }
    }
}
