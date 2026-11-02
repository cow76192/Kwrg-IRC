package me.cookie.kwrg.irc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.manager.HideArmorManager;
import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.module.modules.style.ChatModule;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import me.cookie.kwrg.skyblock.route.ChatWaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.*;
import java.util.concurrent.*;

public class IrcMessageHandler {
    private static final ScheduledExecutorService TYPING_TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "IRC-Typing-Timeout");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, ScheduledFuture<?>> TYPING_TIMEOUT_TASKS = new ConcurrentHashMap<>();

    private static String optStr(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }

    private static boolean optBool(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[^A-Za-z0-9_]", "");
        return clean.length() > 32 ? clean.substring(0, 32) : clean;
    }

    private static String noFormatting(String value) {
        if (value == null) return "";
        return value.replace("\u00A7", "");
    }

    public static String extractFirstColor(String text) {
        if (text == null || text.isEmpty()) return "§7";

        for (int i = 0; i < text.length() - 1; i++) {
            char c1 = text.charAt(i);
            char c2 = text.charAt(i + 1);
            if ((c1 == '§' || c1 == '&') && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c2) >= 0) {
                StringBuilder colorBuilder = new StringBuilder();
                colorBuilder.append('§').append(Character.toLowerCase(c2));

                int j = i + 2;
                while (j < text.length() - 1) {
                    char nextC1 = text.charAt(j);
                    char nextC2 = text.charAt(j + 1);
                    if ((nextC1 == '§' || nextC1 == '&') && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(nextC2) >= 0) {
                        colorBuilder.append('§').append(Character.toLowerCase(nextC2));
                        j += 2;
                    } else {
                        break;
                    }
                }
                return colorBuilder.toString();
            }
        }
        return "§7";
    }

    public static String formatRankWithBrackets(String rank) {
        if (rank == null || rank.isBlank()) return "";

        String clean = rank.replace('&', '§').trim();
        String bracketColor = extractFirstColor(clean);

        clean = stripOuterBrackets(clean);
        if (clean.isBlank()) return "";

        return bracketColor + "[" + clean + "§r" + bracketColor + "]§r";
    }

    private static String stripOuterBrackets(String s) {
        if (s == null) return "";
        String str = s.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            String strippedLeft = ChatNotifier.LEADING_BRACKET_PATTERN.matcher(str).replaceFirst("$1");
            if (!strippedLeft.equals(str)) {
                str = strippedLeft.trim();
                changed = true;
            }
            String strippedRight = ChatNotifier.TRAILING_BRACKET_PATTERN.matcher(str).replaceFirst("$1");
            if (!strippedRight.equals(str)) {
                str = strippedRight.trim();
                changed = true;
            }
        }
        return str;
    }

    public static void handleIncomingJson(String jsonStr) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            String senderId = optStr(obj, "senderId", "");
            String type = optStr(obj, "type", "chat");
            Minecraft mc = Minecraft.getInstance();

            if ("typing".equalsIgnoreCase(type) || "player_typing".equalsIgnoreCase(type)) {
                String user = optStr(obj, "username", "");
                if (user.isBlank() || user.equalsIgnoreCase(mc.getUser().getName())) {
                    return;
                }

                boolean isTyping = false;
                if (obj.has("typing")) {
                    isTyping = obj.get("typing").getAsBoolean();
                } else if (obj.has("action")) {
                    isTyping = "start".equalsIgnoreCase(obj.get("action").getAsString());
                }

                handlePlayerTyping(user, isTyping);
                return;
            }

            if ("function_sync".equalsIgnoreCase(type)) {
                String function = optStr(obj, "function", "");
                String user = optStr(obj, "username", "");
                if ("hideArmor".equalsIgnoreCase(function)) {
                    if (!obj.has("parts")) {
                        boolean enabled = optBool(obj, "enabled", true);
                        HideArmorManager.setRemotePlayerHideArmor(user, enabled);
                        return;
                    }
                    if (!obj.get("parts").isJsonArray()) return;
                    Set<EquipmentSlot> parts = EnumSet.noneOf(EquipmentSlot.class);
                    for (JsonElement part : obj.getAsJsonArray("parts")) {
                        if (!part.isJsonPrimitive() || !part.getAsJsonPrimitive().isString()) continue;
                        EquipmentSlot slot = switch (part.getAsString().toLowerCase(Locale.ROOT)) {
                            case "head" -> EquipmentSlot.HEAD;
                            case "chest" -> EquipmentSlot.CHEST;
                            case "legs" -> EquipmentSlot.LEGS;
                            case "feet" -> EquipmentSlot.FEET;
                            default -> null;
                        };
                        if (slot != null) parts.add(slot);
                    }
                    HideArmorManager.setRemotePlayerHideArmor(user, parts);
                } else if ("hideArmorHead".equalsIgnoreCase(function)
                        || "hideArmorChest".equalsIgnoreCase(function)
                        || "hideArmorLegs".equalsIgnoreCase(function)
                        || "hideArmorFeet".equalsIgnoreCase(function)) {
                    EquipmentSlot slot = switch (function.toLowerCase(Locale.ROOT)) {
                        case "hidearmorhead" -> EquipmentSlot.HEAD;
                        case "hidearmorchest" -> EquipmentSlot.CHEST;
                        case "hidearmorlegs" -> EquipmentSlot.LEGS;
                        default -> EquipmentSlot.FEET;
                    };
                    boolean enabled = optBool(obj, "enabled", false);
                    HideArmorManager.setRemotePlayerHideArmor(user, slot, enabled);
                } else if ("modelScale".equalsIgnoreCase(function)) {
                    float scale = obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f;
                    me.cookie.kwrg.irc.manager.PlayerCustomizationManager.setRemotePlayerScale(user, scale);
                }
                return;
            }

            if ("player_join".equalsIgnoreCase(type)) {
                String user = optStr(obj, "username", "Unknown");
                String rank = optStr(obj, "rank", "");
                String formattedRank = formatRankWithBrackets(rank);
                IrcClient.sendHideArmorSync();
                me.cookie.kwrg.irc.manager.IrcGlowManager.addPlayer(user);
                IrcMessageHandler.printMessage("§e[IRC] " + (formattedRank.isEmpty() ? "" : formattedRank + " ") + "§f" + safe(user) + " §a加入了 IRC!");
                return;
            }

            if ("player_leave".equalsIgnoreCase(type)) {
                String user = optStr(obj, "username", "Unknown");
                String senderUuid = optStr(obj, "senderId", "");

                HideArmorManager.setRemotePlayerHideArmor(user, false);
                me.cookie.kwrg.irc.manager.PlayerCustomizationManager.setRemotePlayerScale(user, 1.0f);
                me.cookie.kwrg.irc.manager.IrcGlowManager.removePlayer(user);
                me.cookie.kwrg.irc.manager.IrcCapeManager.handlePlayerLeave(senderUuid, user);

                IrcMessageHandler.printMessage("§7[IRC] 玩家 §f" + safe(user) + " §7退出了 IRC");
                return;
            }

            if ("online_list".equalsIgnoreCase(type)) {
                int count = obj.has("count") ? obj.get("count").getAsInt() : 0;
                com.google.gson.JsonArray array = obj.getAsJsonArray("players");
                java.util.List<String> list = new java.util.ArrayList<>();
                if (array != null) {
                    for (com.google.gson.JsonElement el : array) {
                        list.add(el.getAsString());
                    }
                }

                boolean isFromGui = me.cookie.kwrg.module.ModuleManager.updateOnlinePlayers(list);
                if (isFromGui) {
                    return;
                }
                IrcGlowManager.setOnlinePlayers(list);
                IrcMessageHandler.printMessage("§6===== [IRC 在线玩家 (" + count + "人)] =====");
                IrcMessageHandler.printMessage("§f" + (list.isEmpty() ? "§7暂无其他在线玩家" : String.join("§7, §f", list)));
                return;
            }


            if ("cape_check_result".equalsIgnoreCase(type)) {
                String hash = optStr(obj, "hash", "");
                boolean exists = optBool(obj, "exists", false);
                me.cookie.kwrg.irc.manager.IrcCapeManager.handleCheckResult(hash, exists);
                return;
            }

            if ("cape_sync".equalsIgnoreCase(type)) {
                String senderUuid = optStr(obj, "senderId", "");
                String username = optStr(obj, "username", "");
                String hash = optStr(obj, "hash", "");
                String fileName = optStr(obj, "fileName", "");

                if (username.isBlank() || username.equalsIgnoreCase(mc.getUser().getName())) {
                    return;
                }

                me.cookie.kwrg.irc.manager.IrcCapeManager.handleRemoteCapeSync(senderUuid, username, hash, fileName);
                return;
            }
            if ("cape_data".equalsIgnoreCase(type)) {
                String hash = optStr(obj, "hash", "");
                String fileName = optStr(obj, "fileName", "");
                String data = optStr(obj, "data", "");
                me.cookie.kwrg.irc.manager.IrcCapeManager.handleIncomingCapeData(hash, fileName, data);
                return;
            }

            if ("share".equalsIgnoreCase(type) || "invite".equalsIgnoreCase(type)) {
                String target = optStr(obj, "target", "");
                String currentName = mc.getUser().getName();

                if (target.equalsIgnoreCase(currentName)) {
                    String sender = optStr(obj, "username", "某玩家");
                    String shareType = optStr(obj, "shareType", "custom");

                    String rawData = "";
                    if (obj.has("data")) {
                        com.google.gson.JsonElement dataElem = obj.get("data");
                        if (dataElem.isJsonObject()) {
                            JsonObject dataObj = dataElem.getAsJsonObject();
                            if ("config".equalsIgnoreCase(shareType)) {
                                rawData = dataObj.toString();
                            } else if (dataObj.has("content")) {
                                rawData = dataObj.get("content").getAsString();
                            } else {
                                rawData = dataObj.toString();
                            }
                        } else {
                            rawData = dataElem.getAsString();
                        }
                    }

                    me.cookie.kwrg.module.modules.config.ShareModule.PendingShare pending = new me.cookie.kwrg.module.modules.config.ShareModule.PendingShare();
                    pending.sender = sender;
                    pending.type = type;
                    pending.shareType = shareType;
                    pending.rawData = rawData;
                    me.cookie.kwrg.module.modules.config.ShareModule.currentPending = pending;

                    String typeDesc = switch (shareType.toLowerCase()) {
                        case "config" -> "config";
                        case "ns" -> "NameStyle";
                        case "ms" -> "MessageStyle";
                        case "channel" -> "channel invite";
                        default -> "分享内容";
                    };

                    MutableComponent chatMsg = Component.literal("§6[IRC] 收到来自 §e" + sender + " §6的 §b[" + typeDesc + "]§6！ ");

                    MutableComponent clickActionComp = Component.literal("§e§l§n[点击查看 / 导入]§r")
                            .setStyle(Style.EMPTY
                                    .withColor(0xFFE082)
                                    .withUnderlined(true)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent.RunCommand("/irc share open"))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§a点击打开导入菜单"))));

                    chatMsg.append(clickActionComp);
                    ChatNotifier.sendInChatScreen(chatMsg);
                }
                return;
            }

            if (obj.has("type") && "play".equalsIgnoreCase(obj.get("type").getAsString())) {
                String target = optStr(obj, "target", "");
                String currentName = mc.getUser().getName();

                if (target.equalsIgnoreCase(currentName) || target.equalsIgnoreCase("@all")) {
                    String sender = optStr(obj, "username", "Unknown");
                    String item = optStr(obj, "item", "minecraft:totem_of_undying");
                    triggerTotemEffect(sender, item);
                }
                return;
            }

            if (obj.has("type") && "play_fail".equalsIgnoreCase(obj.get("type").getAsString())) {
                String target = optStr(obj, "target", "");
                String currentName = mc.getUser().getName();
                if (target.equalsIgnoreCase(currentName)) {
                    String receiver = optStr(obj, "username", "对方");
                    String reason = optStr(obj, "reason", "未知错误");
                    EffectDispatcher.handlePlayFail(safe(receiver), noFormatting(reason));
                }
                return;
            }

            if (mc.player != null && senderId.equals(mc.player.getStringUUID())) {
                return;
            }

            if (!"chat".equals(type)) {
                return;
            }

            String username = optStr(obj, "username", "Unknown");
            String rank = optStr(obj, "rank", "");
            String nameStyle = optStr(obj, "namestyle", "");
            String chatStyle = optStr(obj, "chatstyle", "");
            String content = optStr(obj, "content", "");
            String channel = optStr(obj, "channel", "");
            String prefix = optStr(obj, "prefix", "");
            String suffix = optStr(obj, "suffix", "");

            displayFormattedMessage(channel, rank, nameStyle, username, chatStyle, content, prefix, suffix);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handlePlayerTyping(String username, boolean isTyping) {
        String noticeKey = "typing_" + username.toLowerCase(Locale.ROOT);

        ScheduledFuture<?> oldTask = TYPING_TIMEOUT_TASKS.remove(noticeKey);
        if (oldTask != null && !oldTask.isDone()) {
            oldTask.cancel(false);
        }

        if (isTyping) {
            DynamicIslandRenderer.setCustomNotice(noticeKey, "§b" + username + " §7正在输入...");

            ScheduledFuture<?> timeoutTask = TYPING_TIMEOUT_EXECUTOR.schedule(() -> {
                DynamicIslandRenderer.removeCustomNotice(noticeKey);
                TYPING_TIMEOUT_TASKS.remove(noticeKey);
            }, 25, TimeUnit.SECONDS);

            TYPING_TIMEOUT_TASKS.put(noticeKey, timeoutTask);
        } else {
            DynamicIslandRenderer.removeCustomNotice(noticeKey);
        }
    }

    public static void displayFormattedMessage(String channel, String rank, String nameStyle, String username, String chatStyle, String content, String prefix, String suffix) {
        java.util.List<ChatNotifier.AnimatedSegment> segments = new java.util.ArrayList<>();

        boolean hasChannel = channel != null && !channel.isBlank();
        if (hasChannel) {
            String cleanChannel = channel.trim();
            if (cleanChannel.startsWith("#")) {
                cleanChannel = cleanChannel.substring(1);
            }
            cleanChannel = noFormatting(cleanChannel);
            segments.add(new ChatNotifier.AnimatedSegment("§b#" + cleanChannel + "§r", "", true));
        }

        String formattedRank = formatRankWithBrackets(rank);
        if (!formattedRank.isEmpty()) {
            segments.add(new ChatNotifier.AnimatedSegment(formattedRank + " ", "", true));
        } else if (hasChannel) {
            segments.add(new ChatNotifier.AnimatedSegment(" ", "", true));
        }

        segments.add(new ChatNotifier.AnimatedSegment(username, nameStyle, false));
        segments.add(new ChatNotifier.AnimatedSegment(" §7: §r", "", true));

        String body = content != null ? content : "";

        if (prefix != null && !prefix.isEmpty()) {
            segments.add(new ChatNotifier.AnimatedSegment(prefix, "", true));
        }
        segments.add(new ChatNotifier.AnimatedSegment(body, chatStyle, false));
        if (suffix != null && !suffix.isEmpty()) {
            segments.add(new ChatNotifier.AnimatedSegment(suffix, "", true));
        }

        ChatWaypointManager.appendButton(segments, body, rank, username);

        ChatNotifier.sendAnimatedInChatScreen(segments);
        ChatModule.playConfiguredSound();

        for (String target : IrcConfig.get().importantPlayers) {
            if (target.equalsIgnoreCase(username)) {
                showImportantTitle(rank, nameStyle, username, chatStyle, body);
                break;
            }
        }
    }

    private static void triggerTotemEffect(String sender, String rawItemId) {
        EffectDispatcher.triggerTotemEffect(sender, rawItemId);
    }

    private static void showImportantTitle(String rank, String nameStyle, String username, String chatStyle, String content) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            mc.gui.setTimes(8, 35, 8);

            MutableComponent title = Component.empty();
            String formattedRank = formatRankWithBrackets(rank);
            if (!formattedRank.isEmpty()) {
                title.append(Component.literal(formattedRank + " "));
            }
            title.append(ColorUtil.applyStyle(username, nameStyle));
            mc.gui.setTitle(title);

            Component subtitle = ColorUtil.applyStyle(content, chatStyle);
            mc.gui.setSubtitle(subtitle);

            ImportantTitleManager.start(rank, nameStyle, username, chatStyle, content);
        });
    }

    public static class ImportantTitleManager {
        private static String rank = "";
        private static String nameStyle = "";
        private static String username = "";
        private static String chatStyle = "";
        private static String content = "";
        private static boolean isShowing = false;

        public static void start(String r, String ns, String u, String cs, String c) {
            rank = r;
            nameStyle = ns;
            username = u;
            chatStyle = cs;
            content = c;
            isShowing = true;
        }

        public static void stop() {
            isShowing = false;
        }

        public static boolean isShowing() {
            return isShowing;
        }

        public static Component getAnimatedTitle() {
            MutableComponent title = Component.empty();
            String formattedRank = formatRankWithBrackets(rank);
            if (!formattedRank.isEmpty()) {
                title.append(Component.literal(formattedRank + " "));
            }
            title.append(ColorUtil.applyStyle(username, nameStyle));
            return title;
        }

        public static Component getAnimatedSubtitle() {
            return ColorUtil.applyStyle(content, chatStyle);
        }
    }

    public static void printMessage(String text) {
        ChatNotifier.sendInChatScreen(text);
    }

    public static void printMessage(Component component) {
        ChatNotifier.sendInChatScreen(component);
    }
}
