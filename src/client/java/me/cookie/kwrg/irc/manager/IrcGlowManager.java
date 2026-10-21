package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.irc.config.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IrcGlowManager {

    private static final Set<String> ONLINE_IRC_PLAYERS = ConcurrentHashMap.newKeySet();

    /**
     * 读取持久化在 IrcConfig 中的开关状态
     */
    public static boolean isGlowEnabled() {
        return IrcConfig.get().glowEnabled;
    }

    public static void setGlowEnabled(boolean enabled) {
        IrcConfig.get().glowEnabled = enabled;
        IrcConfig.save(); // 单次点击切换立即保存
    }

    public static void addPlayer(String username) {
        if (username != null && !username.isBlank()) {
            ONLINE_IRC_PLAYERS.add(username.toLowerCase());
        }
    }

    public static void removePlayer(String username) {
        if (username != null && !username.isBlank()) {
            ONLINE_IRC_PLAYERS.remove(username.toLowerCase());
        }
    }

    public static void setOnlinePlayers(Collection<String> players) {
        ONLINE_IRC_PLAYERS.clear();
        if (players != null) {
            for (String p : players) {
                if (p != null && !p.isBlank()) {
                    ONLINE_IRC_PLAYERS.add(p.toLowerCase());
                }
            }
        }
    }

    public static boolean isIrcPlayerGlowing(Player player) {
        if (!isGlowEnabled() || player == null) return false;

        Minecraft mc = Minecraft.getInstance();
        String name = player.getGameProfile().name();

        // 排除自己
        if (mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName())) {
            return false;
        }

        return ONLINE_IRC_PLAYERS.contains(name.toLowerCase());
    }

    public static int getGlowColorRgb() {
        String colorStr = IrcConfig.get().glowColor;
        java.awt.Color c = me.cookie.kwrg.util.ColorUtil.parseSingleColor(colorStr);
        return c != null ? (c.getRGB() & 0xFFFFFF) : 0x00FF88;
    }

    public static void clear() {
        ONLINE_IRC_PLAYERS.clear();
    }
}