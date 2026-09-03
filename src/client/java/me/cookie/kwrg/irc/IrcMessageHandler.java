package me.cookie.kwrg.irc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cookie.kwrg.irc.manager.HideArmorManager;
import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.module.modules.style.ChatModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;

public class IrcMessageHandler {

    public static String extractFirstColor(String text) {
        if (text == null || text.isEmpty()) return "§7";

        StringBuilder colorBuilder = new StringBuilder();
        int i = 0;
        while (i < text.length() - 1) {
            char c1 = text.charAt(i);
            char c2 = text.charAt(i + 1);
            if ((c1 == '§' || c1 == '&') && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c2) >= 0) {
                colorBuilder.append('§').append(Character.toLowerCase(c2));
                i += 2;
            } else {
                break;
            }
        }
        return !colorBuilder.isEmpty() ? colorBuilder.toString() : "§7";
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
            String strippedLeft = str.replaceFirst("^((§[0-9a-fk-orA-FK-OR])*)\\[", "$1");
            if (!strippedLeft.equals(str)) {
                str = strippedLeft.trim();
                changed = true;
            }
            String strippedRight = str.replaceFirst("\\]((§[0-9a-fk-orA-FK-OR])*)$", "$1");
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

            displayFormattedMessage(rank, nameStyle, username, chatStyle, content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void displayFormattedMessage(String rank, String nameStyle, String username, String chatStyle, String content) {
        java.util.List<ChatNotifier.AnimatedSegment> segments = new java.util.ArrayList<>();

        String formattedRank = formatRankWithBrackets(rank);
        if (!formattedRank.isEmpty()) {
            segments.add(new ChatNotifier.AnimatedSegment(formattedRank + " ", "", true));
        }

        segments.add(new ChatNotifier.AnimatedSegment(username, nameStyle, false));
        segments.add(new ChatNotifier.AnimatedSegment("§7: §r", "", true));
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

    // =========================================================
    // 关注玩家 Title 逻辑
    // =========================================================

    private static void showImportantTitle(String rank, String nameStyle, String username, String chatStyle, String content) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            mc.gui.setTimes(8, 35, 8);

            // 1. 设置初始静态内容，启动原版 51 刻生命周期
            MutableComponent title = Component.empty();
            String formattedRank = formatRankWithBrackets(rank);
            if (!formattedRank.isEmpty()) {
                title.append(Component.literal(formattedRank + " "));
            }
            title.append(ColorUtil.applyStyle(username, nameStyle));
            mc.gui.setTitle(title);

            Component subtitle = ColorUtil.applyStyle(content, chatStyle);
            mc.gui.setSubtitle(subtitle);

            // 2. 启动动态流光管理器 (只更新内存数据供 Mixin 提取，绝不重复调用 setTitle)
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

        /**
         * 供 Mixin 在 @At("HEAD") 直接提取当前帧具有最新流光的 Title
         */
        public static Component getAnimatedTitle() {
            MutableComponent title = Component.empty();
            String formattedRank = formatRankWithBrackets(rank);
            if (!formattedRank.isEmpty()) {
                title.append(Component.literal(formattedRank + " "));
            }
            title.append(ColorUtil.applyStyle(username, nameStyle));
            return title;
        }

        /**
         * 供 Mixin 在 @At("HEAD") 直接提取当前帧具有最新流光的 Subtitle
         */
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