package me.cookie.kwrg.irc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cookie.kwrg.irc.manager.HideArmorManager;
import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.module.modules.style.ChatModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;

import java.util.Objects;
import java.util.UUID;

public class IrcMessageHandler {

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
            String strippedLeft = str.replaceFirst("^((?:§[0-9a-fk-orA-FK-OR])*)\\[", "$1");
            if (!strippedLeft.equals(str)) {
                str = strippedLeft.trim();
                changed = true;
            }
            String strippedRight = str.replaceFirst("\\]((?:§[0-9a-fk-orA-FK-OR])*)$", "$1");
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
            String senderId = obj.has("senderId") ? obj.get("senderId").getAsString() : "";
            String type = obj.has("type") ? obj.get("type").getAsString() : "chat";
            Minecraft mc = Minecraft.getInstance();

            if ("function_sync".equalsIgnoreCase(type)) {
                String function = obj.has("function") ? obj.get("function").getAsString() : "";
                String user = obj.has("username") ? obj.get("username").getAsString() : "";
                boolean enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();

                if ("hideArmor".equalsIgnoreCase(function)) {
                    HideArmorManager.setPlayerHideArmor(user, enabled);
                } else if ("modelScale".equalsIgnoreCase(function)) {
                    float scale = obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f;
                    me.cookie.kwrg.irc.manager.PlayerCustomizationManager.setRemotePlayerScale(user, scale);
                }
                return;
            }

            if ("player_join".equalsIgnoreCase(type)) {
                String user = obj.has("username") ? obj.get("username").getAsString() : "Unknown";
                String rank = obj.has("rank") ? obj.get("rank").getAsString() : "";
                String formattedRank = formatRankWithBrackets(rank);
                IrcClient.sendFunctionSync("hideArmor", IrcConfig.get().hideArmor);
                me.cookie.kwrg.irc.manager.IrcGlowManager.addPlayer(user);
                IrcMessageHandler.printMessage("§e[IRC] " + (formattedRank.isEmpty() ? "" : formattedRank + " ") + "§f" + user + " §a加入了 IRC!");
                return;
            }

            if ("player_leave".equalsIgnoreCase(type)) {
                String user = obj.has("username") ? obj.get("username").getAsString() : "Unknown";
                HideArmorManager.setRemotePlayerHideArmor(user, false);
                me.cookie.kwrg.irc.manager.PlayerCustomizationManager.setRemotePlayerScale(user, 1.0f);
                me.cookie.kwrg.irc.manager.IrcGlowManager.removePlayer(user);
                IrcMessageHandler.printMessage("§7[IRC] 玩家 §f" + user + " §7退出了 IRC");
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

            if ("function_sync".equalsIgnoreCase(type)) {
                String function = obj.has("function") ? obj.get("function").getAsString() : "";
                String user = obj.has("username") ? obj.get("username").getAsString() : "";
                String senderUuidStr = obj.has("senderId") ? obj.get("senderId").getAsString() : "";

                if ("hideArmor".equalsIgnoreCase(function)) {
                    boolean enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();
                    HideArmorManager.setPlayerHideArmor(user, enabled);
                } else if ("modelScale".equalsIgnoreCase(function)) {
                    float scale = obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f;
                    try {
                        UUID senderUuid = UUID.fromString(senderUuidStr);
                        me.cookie.kwrg.irc.manager.PlayerCustomizationManager.setIrcPlayerCustomization(senderUuid, null, scale);
                    } catch (Exception ignored) {}
                }
                return;
            }

            if ("cape_check_result".equalsIgnoreCase(type)) {
                String hash = obj.has("hash") ? obj.get("hash").getAsString() : "";
                boolean exists = obj.has("exists") && obj.get("exists").getAsBoolean();
                me.cookie.kwrg.irc.manager.IrcCapeManager.handleCheckResult(hash, exists);
                return;
            }

            if ("cape_sync".equalsIgnoreCase(type)) {
                String senderUuidStr = obj.has("senderId") ? obj.get("senderId").getAsString() : "";
                String hash = obj.has("hash") ? obj.get("hash").getAsString() : "";
                String fileName = obj.has("fileName") ? obj.get("fileName").getAsString() : "";

                if (mc.player != null && senderUuidStr.equals(mc.player.getStringUUID())) {
                    return;
                }

                try {
                    UUID uuid = UUID.fromString(senderUuidStr);
                    me.cookie.kwrg.irc.manager.IrcCapeManager.handleRemoteCapeSync(uuid, hash, fileName);
                } catch (Exception ignored) {}
                return;
            }

            if ("cape_data".equalsIgnoreCase(type)) {
                String hash = obj.has("hash") ? obj.get("hash").getAsString() : "";
                String fileName = obj.has("fileName") ? obj.get("fileName").getAsString() : "";
                String data = obj.has("data") ? obj.get("data").getAsString() : "";
                me.cookie.kwrg.irc.manager.IrcCapeManager.handleIncomingCapeData(hash, fileName, data);
                return;
            }

            if ("share".equalsIgnoreCase(type) || "invite".equalsIgnoreCase(type)) {
                String target = obj.has("target") ? obj.get("target").getAsString() : "";
                String currentName = mc.getUser().getName();

                if (target.equalsIgnoreCase(currentName)) {
                    String sender = obj.has("username") ? obj.get("username").getAsString() : "某玩家";
                    String shareType = obj.has("shareType") ? obj.get("shareType").getAsString() : "custom";

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
                String target = obj.has("target") ? obj.get("target").getAsString() : "";
                String currentName = mc.getUser().getName();

                if (target.equalsIgnoreCase(currentName) || target.equalsIgnoreCase("@all")) {
                    String sender = obj.has("username") ? obj.get("username").getAsString() : "Unknown";
                    String item = obj.has("item") ? obj.get("item").getAsString() : "minecraft:totem_of_undying";
                    triggerTotemEffect(sender, item);
                }
                return;
            }

            if (obj.has("type") && "play_fail".equalsIgnoreCase(obj.get("type").getAsString())) {
                String target = obj.has("target") ? obj.get("target").getAsString() : "";
                String currentName = mc.getUser().getName();
                if (target.equalsIgnoreCase(currentName)) {
                    String receiver = obj.has("username") ? obj.get("username").getAsString() : "对方";
                    String reason = obj.has("reason") ? obj.get("reason").getAsString() : "未知错误";
                    IrcMessageHandler.printMessage("§c[IRC] 玩家 §f" + receiver + " §c播放失败: §e" + reason);
                }
                return;
            }

            if (mc.player != null && senderId.equals(mc.player.getStringUUID())) {
                return;
            }

            String username = obj.has("username") ? obj.get("username").getAsString() : "Unknown";
            String rank = obj.has("rank") ? obj.get("rank").getAsString() : "";
            String nameStyle = obj.has("namestyle") ? obj.get("namestyle").getAsString() : "";
            String chatStyle = obj.has("chatstyle") ? obj.get("chatstyle").getAsString() : "";
            String content = obj.has("content") ? obj.get("content").getAsString() : "";
            String channel = obj.has("channel") ? obj.get("channel").getAsString() : "";

            displayFormattedMessage(channel, rank, nameStyle, username, chatStyle, content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void displayFormattedMessage(String channel, String rank, String nameStyle, String username, String chatStyle, String content) {
        java.util.List<ChatNotifier.AnimatedSegment> segments = new java.util.ArrayList<>();

        boolean hasChannel = channel != null && !channel.isBlank();
        if (hasChannel) {
            String cleanChannel = channel.trim();
            if (cleanChannel.startsWith("#")) {
                cleanChannel = cleanChannel.substring(1);
            }
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
        segments.add(new ChatNotifier.AnimatedSegment(content, chatStyle, false));

        ChatNotifier.sendAnimatedInChatScreen(segments);
        ChatModule.playConfiguredSound();

        for (String target : IrcConfig.get().importantPlayers) {
            if (target.equalsIgnoreCase(username)) {
                showImportantTitle(rank, nameStyle, username, chatStyle, content);
                break;
            }
        }
    }

    public static void displayFormattedMessage(String rank, String nameStyle, String username, String chatStyle, String content) {
        displayFormattedMessage("", rank, nameStyle, username, chatStyle, content);
    }

    private static void triggerTotemEffect(String sender, String rawItemId) {
        if (rawItemId.startsWith("http://") || rawItemId.startsWith("https://")) {
            me.cookie.kwrg.util.TotemAnimationManager.playUrlEffect(sender, rawItemId);
            return;
        }

        if (me.cookie.kwrg.util.SkyblockItemHelper.isSkyblockItem(rawItemId)) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null) return;
                net.minecraft.world.item.ItemStack itemStack = me.cookie.kwrg.util.SkyblockItemHelper.resolve(rawItemId);
                mc.gameRenderer.displayItemActivation(itemStack);
                mc.player.playSound(net.minecraft.sounds.SoundEvents.TOTEM_USE, 1.0F, 1.0F);

                if (!Objects.equals(sender, "server ") && !Objects.equals(sender, "控制台管理员")) {
                    IrcMessageHandler.printMessage("§6[IRC] §e" + sender + " §a向你触发了图腾激活效果！(§f" + itemStack.getHoverName().getString() + "§a)");
                }
            });
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null || mc.gameRenderer == null) return;

            String fullId = rawItemId.contains(":") ? rawItemId : "minecraft:" + rawItemId;
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fullId);

            java.util.Optional<net.minecraft.world.item.Item> optionalItem = (id == null)
                    ? java.util.Optional.empty()
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);

            if (optionalItem.isEmpty() || (optionalItem.get() == net.minecraft.world.item.Items.AIR && !fullId.equals("minecraft:air"))) {
                String errorReason = "未知的物品 " + rawItemId;
                IrcMessageHandler.printMessage("§c[IRC] 收到来自 §e" + sender + " §c的未知物品: §e" + rawItemId);
                IrcClient.sendPlayFail(sender, rawItemId, errorReason);
                return;
            }

            net.minecraft.world.item.Item item = optionalItem.get();
            net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item);

            mc.gameRenderer.displayItemActivation(itemStack);
            mc.player.playSound(net.minecraft.sounds.SoundEvents.TOTEM_USE, 1.0F, 1.0F);
            IrcMessageHandler.printMessage("§6[IRC] §e" + sender + " §a向你触发了图腾激活效果！(§f" + item.getName(itemStack).getString() + "§a)");
        });
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