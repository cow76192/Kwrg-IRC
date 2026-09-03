package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerFinderManager {

    private static final Set<String> TARGET_PLAYERS = ConcurrentHashMap.newKeySet();

    public static void addPlayer(String username) {
        if (username != null && !username.isBlank()) {
            TARGET_PLAYERS.add(username.trim().toLowerCase());
        }
    }

    public static boolean removePlayer(String username) {
        if (username == null || username.isBlank()) return false;
        return TARGET_PLAYERS.remove(username.trim().toLowerCase());
    }

    public static List<String> getPlayers() {
        return new ArrayList<>(TARGET_PLAYERS);
    }

    public static boolean isTarget(Player player) {
        if (player == null) return false;

        Minecraft mc = Minecraft.getInstance();
        String name = player.getGameProfile().name();

        // 排除本地玩家自己
        if (mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName())) {
            return false;
        }

        return TARGET_PLAYERS.contains(name.toLowerCase());
    }

    public static int getGlowColorRgb() {
        String colorStr = IrcConfig.get().playerFinderColor;
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        return c != null ? (c.getRGB() & 0xFFFFFF) : 0xFF3333; // 默认亮红色
    }

    public static void clear() {
        TARGET_PLAYERS.clear();
    }
}