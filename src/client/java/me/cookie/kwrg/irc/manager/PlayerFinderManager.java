package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.uil.PlayerFinderChatState;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerFinderManager {

    private static final Map<String, String> PERSISTENT_TARGETS = new ConcurrentHashMap<>();
    private static final Map<String, String> TEMP_TARGETS = new ConcurrentHashMap<>();
    private static final Set<String> STARRED_PLAYERS = ConcurrentHashMap.newKeySet();

    private static volatile boolean partyTracking = false;

    private static Runnable onTargetsChangedListener = null;

    public static void setOnTargetsChangedListener(Runnable listener) {
        onTargetsChangedListener = listener;
    }

    public static void notifyTargetsChanged() {
        if (onTargetsChangedListener != null) {
            Minecraft.getInstance().execute(onTargetsChangedListener);
        }
    }

    public static void reloadFromConfig() {
        PERSISTENT_TARGETS.clear();
        IrcConfig config = IrcConfig.get();
        if (config != null && config.playerFinderTargets != null) {
            for (String name : config.playerFinderTargets) {
                if (name != null && !name.isBlank()) {
                    String clean = name.trim();
                    PERSISTENT_TARGETS.put(clean.toLowerCase(Locale.ROOT), clean);
                }
            }
        }
    }

    private static void saveToConfig() {
        IrcConfig config = IrcConfig.get();
        if (config == null) return;
        config.playerFinderTargets = new ArrayList<>(PERSISTENT_TARGETS.values());
        IrcConfig.saveDelayed(600L);
    }

    public static void addPlayer(String username) {
        if (username == null || username.isBlank()) return;
        String original = username.trim();
        String lower = original.toLowerCase(Locale.ROOT);

        TEMP_TARGETS.remove(lower);
        if (PERSISTENT_TARGETS.put(lower, original) == null || !PERSISTENT_TARGETS.get(lower).equals(original)) {
            saveToConfig();
            notifyTargetsChanged();
        }
    }

    public static boolean removePlayer(String username) {
        if (username == null || username.isBlank()) return false;
        String lower = username.trim().toLowerCase(Locale.ROOT);

        boolean removedPersistent = PERSISTENT_TARGETS.remove(lower) != null;
        boolean removedTemp = TEMP_TARGETS.remove(lower) != null;
        STARRED_PLAYERS.remove(lower);

        if (removedPersistent) {
            saveToConfig();
        }
        if (removedPersistent || removedTemp) {
            notifyTargetsChanged();
            return true;
        }
        return false;
    }

    public static boolean isTarget(Player player) {
        if (player == null) return false;
        Minecraft mc = Minecraft.getInstance();
        String name = player.getGameProfile().name();
        if (mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName())) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return PERSISTENT_TARGETS.containsKey(lower) || TEMP_TARGETS.containsKey(lower);
    }

    public static boolean isTemporary(String username) {
        if (username == null) return false;
        return TEMP_TARGETS.containsKey(username.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> getPlayers() {
        Map<String, String> all = new LinkedHashMap<>(PERSISTENT_TARGETS);
        all.putAll(TEMP_TARGETS);
        return new ArrayList<>(all.values());
    }

    public static void toggleStarred(String username) {
        if (username == null || username.isBlank()) return;
        String clean = username.trim().toLowerCase(Locale.ROOT);
        if (STARRED_PLAYERS.contains(clean)) {
            STARRED_PLAYERS.remove(clean);
        } else {
            STARRED_PLAYERS.add(clean);
        }
        notifyTargetsChanged();
    }

    public static boolean isStarred(Player player) {
        if (player == null) return false;
        return STARRED_PLAYERS.contains(player.getGameProfile().name().toLowerCase(Locale.ROOT));
    }

    public static boolean isStarred(String username) {
        if (username == null) return false;
        return STARRED_PLAYERS.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isPartyTracking() {
        return partyTracking;
    }

    public static void setPartyTracking(boolean active) {
        if (partyTracking == active) return;
        partyTracking = active;

        if (active) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.connection != null) {
                PlayerFinderChatState.beginSilentPartyListFetch();
                mc.player.connection.sendCommand("party list");
                ChatNotifier.send("§a已开启队伍自动追踪，正在获取队伍列表...", NotificationType.INFO);
            } else {
                ChatNotifier.send("§a已开启队伍自动追踪。", NotificationType.SUCCESS);
            }
        } else {
            clearTempTargets();
            ChatNotifier.send("§c已关闭队伍自动追踪，已清空临时成员名单。", NotificationType.INFO);
        }
        notifyTargetsChanged();
    }

    public static void clearTempTargets() {
        for (String tempLower : TEMP_TARGETS.keySet()) {
            if (!PERSISTENT_TARGETS.containsKey(tempLower)) {
                STARRED_PLAYERS.remove(tempLower);
            }
        }
        TEMP_TARGETS.clear();
        notifyTargetsChanged();
    }

    public static boolean shouldRenderEffects(Player player) {
        if (!isTarget(player)) return false;
        if (PlayerFinderChatState.isInSmallCombatScene()) {
            return isStarred(player);
        }
        return true;
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName());
    }

    public static void onPlayerJoinedParty(String name) {
        if (isSelf(name)) return;
        String lower = name.toLowerCase(Locale.ROOT);

        if (!PERSISTENT_TARGETS.containsKey(lower) && TEMP_TARGETS.put(lower, name) == null) {
            boolean starred = false;
            IrcConfig config = IrcConfig.get();
            if (config.importantPlayers != null && config.importantPlayers.stream().anyMatch(name::equalsIgnoreCase)) {
                STARRED_PLAYERS.add(lower);
                starred = true;
            }
            notifyTargetsChanged();
            if (starred) {
                ChatNotifier.send("§a" + name + " §a加入队伍，已临时追踪并标星！", NotificationType.SUCCESS);
            } else {
                ChatNotifier.send("§a" + name + " §a加入队伍，已加入临时名单！", NotificationType.SUCCESS);
            }
        }
    }

    public static void onPlayerLeftParty(String name) {
        if (isSelf(name)) {
            partyTracking = false;
            clearTempTargets();
            ChatNotifier.send("§e你已离开队伍，已关闭队伍追踪并清空临时名单", NotificationType.INFO);
            return;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        boolean removedTemp = TEMP_TARGETS.remove(lower) != null;
        if (!PERSISTENT_TARGETS.containsKey(lower)) {
            STARRED_PLAYERS.remove(lower);
        }

        if (removedTemp) {
            notifyTargetsChanged();
            ChatNotifier.send("§e§f" + name + " §e离开队伍，已从临时名单移除", NotificationType.INFO);
        }
    }

    public static void onCommandSent(String command) {
        PlayerFinderChatState.onCommandSent(command);
    }

    public static void beginSilentPartyListFetch() {
        PlayerFinderChatState.beginSilentPartyListFetch();
    }

    public static boolean processSystemChat(String message) {
        return PlayerFinderChatState.processSystemChat(message);
    }

    public static boolean isInSmallCombatScene() {
        return PlayerFinderChatState.isInSmallCombatScene();
    }

    public static void stopPartyTracking() {
        partyTracking = false;
        clearTempTargets();
    }

    public static void onImportantPlayerChanged(String username, boolean added) {
        if (username == null || username.isBlank()) return;
        String lower = username.trim().toLowerCase(Locale.ROOT);

        if (PERSISTENT_TARGETS.containsKey(lower) || TEMP_TARGETS.containsKey(lower)) {
            boolean changed;
            if (added) {
                changed = STARRED_PLAYERS.add(lower);
            } else {
                changed = STARRED_PLAYERS.remove(lower);
            }
            if (changed) {
                notifyTargetsChanged();
            }
        }
    }



    public static void finishPartyListParse(List<String> lines) {
        Set<String> members = parsePartyMembers(lines);
        if (members.isEmpty()) {
            ChatNotifier.send("§e未在返回信息中解析到 Party 成员。", NotificationType.WARN);
            return;
        }

        int addedTemp = 0;
        for (String member : members) {
            String lower = member.toLowerCase(Locale.ROOT);
            if (!PERSISTENT_TARGETS.containsKey(lower)) {
                if (TEMP_TARGETS.put(lower, member) == null) {
                    addedTemp++;
                }
            }
        }

        boolean starredChanged = false;
        IrcConfig config = IrcConfig.get();
        if (config.importantPlayers != null) {
            for (String member : members) {
                boolean isImportant = config.importantPlayers.stream().anyMatch(imp -> imp.equalsIgnoreCase(member));
                if (isImportant && STARRED_PLAYERS.add(member.toLowerCase(Locale.ROOT))) {
                    starredChanged = true;
                }
            }
        }

        if (addedTemp > 0 || starredChanged) {
            notifyTargetsChanged();
        }

        if (addedTemp > 0 && starredChanged) {
            ChatNotifier.send("§a已添加 " + addedTemp + " 名 Party 临时成员，并星标了关注玩家", NotificationType.SUCCESS);
        } else if (addedTemp > 0) {
            ChatNotifier.send("§a已添加 " + addedTemp + " 名 Party 临时成员", NotificationType.SUCCESS);
        } else if (starredChanged) {
            ChatNotifier.send("§6已将队伍中的关注玩家设为星标队友", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§eParty 成员已都在当前寻找名单中", NotificationType.INFO);
        }
    }

    private static Set<String> parsePartyMembers(List<String> lines) {
        Set<String> members = new HashSet<>();
        Pattern rolePattern = Pattern.compile("(?:Party Leader|Party Moderators|Party Members):\\s*(.*)", Pattern.CASE_INSENSITIVE);
        Pattern namePattern = Pattern.compile("\\b([a-zA-Z0-9_]{2,16})\\b");

        for (String line : lines) {
            Matcher roleMatcher = rolePattern.matcher(line);
            if (roleMatcher.find()) {
                String namesPart = roleMatcher.group(1).replaceAll("\\[[^\\]]*\\]", "").replace("●", "").replace("•", "");
                Matcher nameMatcher = namePattern.matcher(namesPart);
                while (nameMatcher.find()) {
                    String name = nameMatcher.group(1);
                    if (!name.equalsIgnoreCase("Party") && !name.equalsIgnoreCase("Leader") &&
                            !name.equalsIgnoreCase("Moderators") && !name.equalsIgnoreCase("Members")) {
                        members.add(name);
                    }
                }
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null) {
            members.remove(mc.getUser().getName());
        }
        return members;
    }

    public static int getGlowColorRgb() {
        String colorStr = IrcConfig.get().playerFinderColor;
        java.awt.Color c = ColorUtil.parseSingleColor(colorStr);
        return c != null ? (c.getRGB() & 0xFFFFFF) : 0xFF3333;
    }

    public static void clear() {
        PERSISTENT_TARGETS.clear();
        TEMP_TARGETS.clear();
        STARRED_PLAYERS.clear();
        partyTracking = false;
        saveToConfig();
        notifyTargetsChanged();
    }
}
