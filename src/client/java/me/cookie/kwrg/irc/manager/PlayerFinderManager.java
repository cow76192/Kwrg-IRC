package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerFinderManager {

    private static final Set<String> PERSISTENT_TARGETS = ConcurrentHashMap.newKeySet();
    private static final Set<String> TEMP_TARGETS = ConcurrentHashMap.newKeySet();
    private static final Set<String> STARRED_PLAYERS = ConcurrentHashMap.newKeySet();

    // Party List 抓取状态机
    private static final Object PARTY_LOCK = new Object();
    private static boolean expectingPartyList = false;
    private static boolean silentPartyFetch = false;
    private static boolean insidePartyList = false;
    private static long partyFetchStartTime = 0L;
    private static final List<String> collectedPartyLines = new ArrayList<>();

    // 战斗场景状态机
    private static volatile boolean waitingForStart = false;
    private static volatile long prepareCombatTimestamp = 0L;
    private static volatile boolean inSmallCombatScene = false;

    private static volatile boolean autoTrackPartyChanges = false;

    private static Runnable onTargetsChangedListener = null;

    private static final Pattern DUNGEON_ENTER_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?[a-zA-Z0-9_]{2,16}\\s+entered\\s+[^,]+,\\s*.+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PARTY_JOIN_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{2,16})\\s+(?:has\\s+)?joined\\s+the\\s+party\\.?.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PARTY_LEAVE_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{2,16})\\s+has\\s+left\\s+the\\s+party\\.?.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PARTY_KICK_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{2,16})\\s+(?:has been removed from|was removed from|was kicked from)\\s+(?:the|your)\\s+party.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PARTY_TRANSFER_LEAVE_PATTERN = Pattern.compile(
            "^The party was transferred to .*?\\s+because\\s+(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{2,16})\\s+left\\.?.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PARTY_DISBAND_PATTERN = Pattern.compile(
            ".*(?:has disbanded the party|The party was disbanded).*?!?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SELF_LEAVE_PATTERN = Pattern.compile(
            "^(?:You left the party|You have been kicked from the party).*?\\.?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PARTY_KICK_OFFLINE_PATTERN = Pattern.compile(
            "^Kicked\\s+(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{2,16})\\s+because\\s+they\\s+were\\s+offline\\.?.*",
            Pattern.CASE_INSENSITIVE
    );

    public static void setOnTargetsChangedListener(Runnable listener) {
        onTargetsChangedListener = listener;
    }

    private static void notifyTargetsChanged() {
        if (onTargetsChangedListener != null) {
            Minecraft.getInstance().execute(onTargetsChangedListener);
        }
    }

    public static void reloadFromConfig() {
        PERSISTENT_TARGETS.clear();
        IrcConfig config = IrcConfig.get();
        autoTrackPartyChanges = config.autoTrackPlayer;
        if (config.playerFinderTargets != null) {
            for (String name : config.playerFinderTargets) {
                if (name != null && !name.isBlank()) {
                    PERSISTENT_TARGETS.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private static void saveToConfig() {
        IrcConfig config = IrcConfig.get();
        config.playerFinderTargets = new ArrayList<>(PERSISTENT_TARGETS);
        IrcConfig.saveDelayed(600L);
    }

    public static void addPlayer(String username) {
        if (username == null || username.isBlank()) return;
        String clean = username.trim().toLowerCase(Locale.ROOT);

        TEMP_TARGETS.remove(clean);
        if (PERSISTENT_TARGETS.add(clean)) {
            saveToConfig();
            notifyTargetsChanged();
        }
    }

    public static boolean removePlayer(String username) {
        if (username == null || username.isBlank()) return false;
        String clean = username.trim().toLowerCase(Locale.ROOT);

        boolean removedPersistent = PERSISTENT_TARGETS.remove(clean);
        boolean removedTemp = TEMP_TARGETS.remove(clean);
        STARRED_PLAYERS.remove(clean);

        if (removedPersistent) {
            saveToConfig();
        }
        if (removedPersistent || removedTemp) {
            notifyTargetsChanged();
            return true;
        }
        return false;
    }

    public static void onImportantPlayerChanged(String username, boolean added) {
        if (username == null || username.isBlank()) return;
        String clean = username.trim().toLowerCase(Locale.ROOT);

        if (PERSISTENT_TARGETS.contains(clean) || TEMP_TARGETS.contains(clean)) {
            boolean changed;
            if (added) {
                changed = STARRED_PLAYERS.add(clean);
            } else {
                changed = STARRED_PLAYERS.remove(clean);
            }
            if (changed) {
                notifyTargetsChanged();
            }
        }
    }

    public static void setAutoTrackEnabled(boolean enabled) {
        autoTrackPartyChanges = enabled;
    }

    public static boolean isTarget(Player player) {
        if (player == null) return false;
        Minecraft mc = Minecraft.getInstance();
        String name = player.getGameProfile().name();
        if (mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName())) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return PERSISTENT_TARGETS.contains(lower) || TEMP_TARGETS.contains(lower);
    }

    public static boolean isTemporary(String username) {
        if (username == null) return false;
        return TEMP_TARGETS.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> getPlayers() {
        Set<String> all = new LinkedHashSet<>(PERSISTENT_TARGETS);
        all.addAll(TEMP_TARGETS);
        return new ArrayList<>(all);
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

    public static boolean shouldRenderEffects(Player player) {
        if (!isTarget(player)) return false;
        if (inSmallCombatScene) {
            return isStarred(player);
        }
        return true;
    }

    private static void setInSmallCombatScene(boolean value) {
        if (inSmallCombatScene != value) {
            inSmallCombatScene = value;
            if (value) {
                ChatNotifier.send("§c战斗开始！仅显示星标队友", NotificationType.WARN, 6000L);
            } else {
                ChatNotifier.send("§a已退出战斗", NotificationType.SUCCESS);
            }
            notifyTargetsChanged();
        }
    }

    public static void onCommandSent(String command) {
        if (command == null) return;
        String lower = command.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("party list") || lower.startsWith("party list ")) {
            synchronized (PARTY_LOCK) {
                if (!silentPartyFetch) {
                    expectingPartyList = true;
                    partyFetchStartTime = System.currentTimeMillis();
                }
            }
        }
    }

    public static void beginSilentPartyListFetch() {
        autoTrackPartyChanges = true;
        synchronized (PARTY_LOCK) {
            expectingPartyList = true;
            silentPartyFetch = true;
            insidePartyList = false;
            partyFetchStartTime = System.currentTimeMillis();
            collectedPartyLines.clear();
        }
    }

    public static boolean processSystemChat(String message) {
        if (message == null) return false;
        String clean = message.replaceAll("§[0-9a-fk-or]", "").trim();
        if (clean.isEmpty()) return false;

        boolean shouldCancelPacket = false;
        String[] lines = clean.split("\\R");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (processSingleChatLine(line)) {
                shouldCancelPacket = true;
            }
        }

        return shouldCancelPacket;
    }

    private static boolean processSingleChatLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);

        if (lower.startsWith("sending to server ")) {
            if (inSmallCombatScene) {
                setInSmallCombatScene(false);
            }
            return false;
        }

        if (lower.contains("starting in 1 second")) {
            long now = System.currentTimeMillis();
            if (waitingForStart && (now - prepareCombatTimestamp <= 40000L)) {
                setInSmallCombatScene(true);
            }
            waitingForStart = false;
            return false;
        }

        if (isDungeonEnterLine(line)) {
            long now = System.currentTimeMillis();
            if (waitingForStart && (now - prepareCombatTimestamp <= 3000L)) {
                return false; // 3 秒内已经触发过入场，忽略重复消息
            }
            waitingForStart = true;
            prepareCombatTimestamp = now;
            ChatNotifier.send("§e检测到进入地牢，等待战斗开启...", NotificationType.INFO);
            return false;
        }

        if (handlePartyMemberChanges(line)) {
            return false;
        }

        return handlePartyListLine(line, lower);
    }

    private static boolean handlePartyMemberChanges(String line) {
        if (!IrcConfig.get().autoTrackPlayer && !autoTrackPartyChanges) return false;

        if (PARTY_DISBAND_PATTERN.matcher(line).matches() || SELF_LEAVE_PATTERN.matcher(line).matches()) {
            if (!TEMP_TARGETS.isEmpty()) {
                clearTempTargets();
                ChatNotifier.send("§e队伍已解散或离开，已清空临时名单", NotificationType.INFO);
            }
            return true;
        }

        Matcher transferMatcher = PARTY_TRANSFER_LEAVE_PATTERN.matcher(line);
        if (transferMatcher.matches()) {
            String leftPlayer = transferMatcher.group(1);
            onPlayerLeftParty(leftPlayer);
            return true;
        }

        Matcher leaveMatcher = PARTY_LEAVE_PATTERN.matcher(line);
        if (leaveMatcher.matches()) {
            String leftPlayer = leaveMatcher.group(1);
            onPlayerLeftParty(leftPlayer);
            return true;
        }

        Matcher kickMatcher = PARTY_KICK_PATTERN.matcher(line);
        if (kickMatcher.matches()) {
            String kickedPlayer = kickMatcher.group(1);
            onPlayerLeftParty(kickedPlayer);
            return true;
        }

        Matcher joinMatcher = PARTY_JOIN_PATTERN.matcher(line);
        if (joinMatcher.matches()) {
            String joinedPlayer = joinMatcher.group(1);
            onPlayerJoinedParty(joinedPlayer);
            return true;
        }

        Matcher offlineMatcher = PARTY_KICK_OFFLINE_PATTERN.matcher(line);
        if (offlineMatcher.matches()) {
            String offlinePlayer = offlineMatcher.group(1);
            onPlayerLeftParty(offlinePlayer);
            return true;
        }

        return false;
    }

    private static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName());
    }

    private static void onPlayerJoinedParty(String name) {
        if (isSelf(name)) return;
        String clean = name.toLowerCase(Locale.ROOT);

        if (!PERSISTENT_TARGETS.contains(clean) && TEMP_TARGETS.add(clean)) {
            boolean starred = false;
            IrcConfig config = IrcConfig.get();
            if (config.importantPlayers != null && config.importantPlayers.stream().anyMatch(name::equalsIgnoreCase)) {
                STARRED_PLAYERS.add(clean);
                starred = true;
            }
            notifyTargetsChanged();
            if (starred) {
                ChatNotifier.send("§a" + name + " §a加入队伍，已临时追踪并标星", NotificationType.SUCCESS);
            } else {
                ChatNotifier.send("§a" + name + " §a加入队伍，已加入临时名单", NotificationType.SUCCESS);
            }
        }
    }

    private static void onPlayerLeftParty(String name) {
        if (isSelf(name)) {
            clearTempTargets();
            ChatNotifier.send("§e 你已离开队伍，已清空临时名单", NotificationType.INFO);
            return;
        }

        String clean = name.toLowerCase(Locale.ROOT);
        boolean removedTemp = TEMP_TARGETS.remove(clean);
        if (!PERSISTENT_TARGETS.contains(clean)) {
            STARRED_PLAYERS.remove(clean);
        }

        if (removedTemp) {
            notifyTargetsChanged();
            ChatNotifier.send("§e" + name + " §e离开队伍，已从临时名单移除", NotificationType.INFO);
        }
    }

    public static void clearTempTargets() {
        for (String temp : TEMP_TARGETS) {
            if (!PERSISTENT_TARGETS.contains(temp)) {
                STARRED_PLAYERS.remove(temp);
            }
        }
        TEMP_TARGETS.clear();
        notifyTargetsChanged();
    }

    private static boolean isDungeonEnterLine(String line) {
        if (!line.contains(" entered ") || !line.contains(",")) return false;
        if (DUNGEON_ENTER_PATTERN.matcher(line).matches()) return true;
        return line.contains("The Catacombs") && (line.contains("Floor") || line.contains("Entrance"));
    }

    private static boolean handlePartyListLine(String line, String lower) {
        synchronized (PARTY_LOCK) {
            if (expectingPartyList && (System.currentTimeMillis() - partyFetchStartTime > 4000L)) {
                expectingPartyList = false;
                silentPartyFetch = false;
                insidePartyList = false;
                collectedPartyLines.clear();
            }

            if (!expectingPartyList) return false;

            if (lower.contains("you are not in a party") || lower.contains("you are not currently in a party")) {
                boolean cancel = silentPartyFetch;
                expectingPartyList = false;
                silentPartyFetch = false;
                ChatNotifier.send("§e获取失败：你当前不在任何队伍中。", NotificationType.WARN);
                return cancel;
            }

            if (line.startsWith("---")) {
                if (!insidePartyList) {
                    insidePartyList = true;
                    collectedPartyLines.clear();
                    return silentPartyFetch;
                } else {
                    insidePartyList = false;
                    expectingPartyList = false;
                    boolean cancel = silentPartyFetch;
                    silentPartyFetch = false;

                    List<String> snapshot = new ArrayList<>(collectedPartyLines);
                    collectedPartyLines.clear();
                    Minecraft.getInstance().execute(() -> finishPartyListParse(snapshot));
                    return cancel;
                }
            } else if (insidePartyList) {
                collectedPartyLines.add(line);
                return silentPartyFetch;
            }
        }
        return false;
    }

    private static void finishPartyListParse(List<String> lines) {
        Set<String> members = parsePartyMembers(lines);
        if (members.isEmpty()) {
            ChatNotifier.send("§e未在返回信息中解析到 Party 成员。", NotificationType.WARN);
            return;
        }

        int addedTemp = 0;
        for (String member : members) {
            String clean = member.toLowerCase(Locale.ROOT);
            if (!PERSISTENT_TARGETS.contains(clean)) {
                if (TEMP_TARGETS.add(clean)) {
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
            ChatNotifier.send("§a 已添加 " + addedTemp + " 名临时成员并星标", NotificationType.SUCCESS);
        } else if (addedTemp > 0) {
            ChatNotifier.send("§a 已添加 " + addedTemp + " 名临时成员。", NotificationType.SUCCESS);
        } else if (starredChanged) {
            ChatNotifier.send("§6已将队伍中的关注玩家设为星标队友。", NotificationType.SUCCESS);
        } else {
            ChatNotifier.send("§eParty 成员已都在当前寻找名单中。", NotificationType.INFO);
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
        autoTrackPartyChanges = false;
        saveToConfig();
        notifyTargetsChanged();
    }
}