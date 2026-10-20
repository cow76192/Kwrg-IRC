package me.cookie.kwrg.uil;

import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import me.cookie.kwrg.irc.manager.WaypointManager;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerFinderChatState {

    private PlayerFinderChatState() {}

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

    private static final Pattern PARTY_KICK_OFFLINE_PATTERN = Pattern.compile(
            "^Kicked\\s+(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{2,16})\\s+because\\s+they\\s+were\\s+offline\\.?.*",
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

    private static final Pattern PARTY_FINDER_JOIN_PATTERN = Pattern.compile(
            "^Party Finder >\\s+(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{2,16})\\s+joined\\s+the\\s+dungeon\\s+group!?.*",
            Pattern.CASE_INSENSITIVE
    );

    public static boolean isInSmallCombatScene() {
        return inSmallCombatScene;
    }

    private static void setInSmallCombatScene(boolean value) {
        if (inSmallCombatScene != value) {
            inSmallCombatScene = value;
            if (value) {
                ChatNotifier.send("§c 战斗开始！仅显示星标队友", NotificationType.WARN, 6000L);
            } else {
                ChatNotifier.send("§a离开战斗场景", NotificationType.SUCCESS);
            }
            PlayerFinderManager.notifyTargetsChanged();
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
            WaypointManager.clearTemporary();
            waitingForStart = false;
            synchronized (PARTY_LOCK) {
                expectingPartyList = false;
                silentPartyFetch = false;
                insidePartyList = false;
                collectedPartyLines.clear();
            }
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
            if (waitingForStart && (now - prepareCombatTimestamp <= 10000L)) {
                return false;
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
        if (!PlayerFinderManager.isPartyTracking()) return false;

        if (PARTY_DISBAND_PATTERN.matcher(line).matches() || SELF_LEAVE_PATTERN.matcher(line).matches()) {
            PlayerFinderManager.stopPartyTracking();
            ChatNotifier.send("§e队伍已解散或离开，已关闭队伍追踪并清空临时名单", NotificationType.INFO);
            return true;
        }

        Matcher transferMatcher = PARTY_TRANSFER_LEAVE_PATTERN.matcher(line);
        if (transferMatcher.matches()) {
            String leftPlayer = transferMatcher.group(1);
            PlayerFinderManager.onPlayerLeftParty(leftPlayer);
            return true;
        }

        Matcher leaveMatcher = PARTY_LEAVE_PATTERN.matcher(line);
        if (leaveMatcher.matches()) {
            String leftPlayer = leaveMatcher.group(1);
            PlayerFinderManager.onPlayerLeftParty(leftPlayer);
            return true;
        }

        Matcher kickMatcher = PARTY_KICK_PATTERN.matcher(line);
        if (kickMatcher.matches()) {
            String kickedPlayer = kickMatcher.group(1);
            PlayerFinderManager.onPlayerLeftParty(kickedPlayer);
            return true;
        }

        Matcher offlineMatcher = PARTY_KICK_OFFLINE_PATTERN.matcher(line);
        if (offlineMatcher.matches()) {
            String offlinePlayer = offlineMatcher.group(1);
            PlayerFinderManager.onPlayerLeftParty(offlinePlayer);
            return true;
        }

        Matcher joinMatcher = PARTY_JOIN_PATTERN.matcher(line);
        if (joinMatcher.matches()) {
            String joinedPlayer = joinMatcher.group(1);
            PlayerFinderManager.onPlayerJoinedParty(joinedPlayer);
            return true;
        }

        Matcher finderJoinMatcher = PARTY_FINDER_JOIN_PATTERN.matcher(line);
        if (finderJoinMatcher.matches()) {
            String joinedPlayer = finderJoinMatcher.group(1);
            PlayerFinderManager.onPlayerJoinedParty(joinedPlayer);
            return true;
        }

        return false;
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
                PlayerFinderManager.stopPartyTracking();
                ChatNotifier.send("§e开启失败：你当前不在任何队伍中。", NotificationType.WARN);
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
                    Minecraft.getInstance().execute(() -> PlayerFinderManager.finishPartyListParse(snapshot));
                    return cancel;
                }
            } else if (insidePartyList) {
                collectedPartyLines.add(line);
                return silentPartyFetch;
            }
        }
        return false;
    }
}
