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

    public static void reloadFromConfig() {
        TARGET_PLAYERS.clear();
        IrcConfig config = IrcConfig.get();
        if (config.playerFinderTargets != null) {
            for (String name : config.playerFinderTargets) {
                if (name != null && !name.isBlank()) {
                    TARGET_PLAYERS.add(name.trim().toLowerCase());
                }
            }
        }
    }

    public static void addPlayer(String username) {
        if (username == null || username.isBlank()) return;
        String clean = username.trim();

        IrcConfig config = IrcConfig.get();
        if (config.playerFinderTargets == null) {
            config.playerFinderTargets = new ArrayList<>();
        }

        boolean exists = config.playerFinderTargets.stream().anyMatch(s -> s.equalsIgnoreCase(clean));
        if (!exists) {
            config.playerFinderTargets.add(clean);
            TARGET_PLAYERS.add(clean.toLowerCase());
            IrcConfig.save();
        }
    }

    public static boolean removePlayer(String username) {
        if (username == null || username.isBlank()) return false;
        String clean = username.trim();

        TARGET_PLAYERS.remove(clean.toLowerCase());

        IrcConfig config = IrcConfig.get();
        if (config.playerFinderTargets != null) {
            boolean removed = config.playerFinderTargets.removeIf(s -> s.equalsIgnoreCase(clean));
            if (removed) {
                IrcConfig.save();
                return true;
            }
        }
        return false;
    }

    public static List<String> getPlayers() {
        IrcConfig config = IrcConfig.get();
        if (config.playerFinderTargets == null) {
            config.playerFinderTargets = new ArrayList<>();
        }

        if (TARGET_PLAYERS.isEmpty() && !config.playerFinderTargets.isEmpty()) {
            reloadFromConfig();
        }

        return new ArrayList<>(config.playerFinderTargets);
    }

    public static boolean isTarget(Player player) {
        if (player == null) return false;

        Minecraft mc = Minecraft.getInstance();
        String name = player.getGameProfile().name();

        if (mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName())) {
            return false;
        }

        IrcConfig config = IrcConfig.get();
        if (TARGET_PLAYERS.isEmpty() && config.playerFinderTargets != null && !config.playerFinderTargets.isEmpty()) {
            reloadFromConfig();
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
        IrcConfig config = IrcConfig.get();
        if (config.playerFinderTargets != null && !config.playerFinderTargets.isEmpty()) {
            config.playerFinderTargets.clear();
            IrcConfig.save();
        }
    }
}