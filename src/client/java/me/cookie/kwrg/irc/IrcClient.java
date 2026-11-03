package me.cookie.kwrg.irc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.cosmetic.HideArmorManager;
import me.cookie.kwrg.irc.cosmetic.IrcGlowManager;
import me.cookie.kwrg.module.modules.functions.ChatSuffixModule;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.util.EmojiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class IrcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc");


    public static volatile boolean isIrcChatActive = false;
    public static volatile boolean isIrcConnect = false;


    public static long getLastLatency() {
        return IrcLink.lastLatency;
    }

    public static void sendPing(Consumer<Long> onResult) {
        if (!IrcTransport.isAlive()) {
            if (onResult != null) {
                Minecraft.getInstance().execute(() -> onResult.accept(-1L));
            }
            return;
        }

        if (onResult != null) {
            IrcLink.pingCallbacks.add(onResult);
        }

        IrcLink.lastPingTimestamp = System.currentTimeMillis();
        JsonObject ping = new JsonObject();
        ping.addProperty("type", "ping");
        IrcTransport.sendRaw(IrcLink.GSON.toJson(ping));
    }

    public static void connect() {
        if (IrcLink.isRunning) {
            ChatNotifier.sendInChatScreen("§c[IRC] 已经连接至 IRC 服务器了喵~");
            return;
        }

        if (IrcLink.isConnecting.get()) {
            ChatNotifier.send("§e[IRC] 正在连接中，请稍候...", NotificationType.INFO);
            return;
        }

        IrcLink.reconnectAttempts.set(0);
        IrcLink.shouldAutoReconnect = true;

        IrcTransport.doConnect(false);
    }

    public static void sendMessage(String content) {
        if (content == null || content.isBlank()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String username = mc.getUser().getName();
        String senderId = mc.player.getStringUUID();

        IrcConfig config = IrcConfig.get();

        // 语气词装饰统一在此处理，覆盖聊天框输入与 /ic、/icall 等所有 IRC 发送路径
        content = EmojiUtil.replaceShortcodes(content);
        content = ChatNotifier.expandCoordsShortcut(content);
        ChatSuffixModule.IrcDecoration decoration = ChatSuffixModule.decorateIrcSafely(content);

        JsonObject json = new JsonObject();
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("rank", config.rank);
        json.addProperty("namestyle", config.namestyle);
        json.addProperty("chatstyle", config.chatstyle);
        json.addProperty("content", decoration.content());
        if (!decoration.prefix().isEmpty()) {
            json.addProperty("prefix", decoration.prefix());
        }
        if (!decoration.suffix().isEmpty()) {
            json.addProperty("suffix", decoration.suffix());
        }

        String channel = "";
        if (config.channelEnabled && config.currentChannel != null && !config.currentChannel.isBlank()) {
            channel = config.currentChannel;
            json.addProperty("channel", channel);
        }

        IrcMessageHandler.displayFormattedMessage(channel, config.rank, config.namestyle, username, config.chatstyle,
                decoration.content(), decoration.prefix(), decoration.suffix());

        if (IrcLink.isRunning) {
            IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 发送失败：未连接至 IRC 服务器！正在尝试连接...");
            connect();
        }
    }

    public static void sendPlayEffect(String targetPlayer, String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        JsonObject json = new JsonObject();
        json.addProperty("type", "play");
        json.addProperty("senderId", mc.player.getStringUUID());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("target", targetPlayer);
        json.addProperty("item", itemId);

        if (IrcLink.isRunning) {
            IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 发送失败：未连接至 IRC 服务器，正在尝试连接...");
            connect();
        }
    }

    public static void sendMessageToPublic(String content) {
        if (content == null || content.isBlank()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String username = mc.getUser().getName();
        String senderId = mc.player.getStringUUID();

        IrcConfig config = IrcConfig.get();

        content = ChatNotifier.expandCoordsShortcut(content);
        ChatSuffixModule.IrcDecoration decoration = ChatSuffixModule.decorateIrcSafely(content);

        JsonObject json = new JsonObject();
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("rank", config.rank);
        json.addProperty("namestyle", config.namestyle);
        json.addProperty("chatstyle", config.chatstyle);
        json.addProperty("content", decoration.content());
        if (!decoration.prefix().isEmpty()) {
            json.addProperty("prefix", decoration.prefix());
        }
        if (!decoration.suffix().isEmpty()) {
            json.addProperty("suffix", decoration.suffix());
        }
        json.addProperty("channel", "");

        IrcMessageHandler.displayFormattedMessage("", config.rank, config.namestyle, username, config.chatstyle,
                decoration.content(), decoration.prefix(), decoration.suffix());

        if (IrcLink.isRunning) {
            IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 发送失败：未连接至 IRC 服务器！正在尝试连接...");
            connect();
        }
    }

    public static void sendPlayFail(String targetPlayer, String rawItem, String reason) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        JsonObject json = new JsonObject();
        json.addProperty("type", "play_fail");
        json.addProperty("senderId", mc.player.getStringUUID());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("target", targetPlayer);
        json.addProperty("item", rawItem);
        json.addProperty("reason", reason);

        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    public static void sendFunctionSync(String functionName, boolean enabled) {
        Minecraft mc = Minecraft.getInstance();

        String senderId = currentSenderId();
        String username = mc.getUser().getName();

        JsonObject json = new JsonObject();
        json.addProperty("type", "function_sync");
        json.addProperty("function", functionName);
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("enabled", enabled);

        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    public static void sendHideArmorSync() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;

        IrcConfig config = IrcConfig.get();
        JsonArray parts = new JsonArray();
        if (config.hideArmor) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                if (HideArmorManager.isPartHidden(slot)) {
                    parts.add(slot.getName());
                }
            }
        }

        JsonObject json = new JsonObject();
        json.addProperty("type", "function_sync");
        json.addProperty("function", "hideArmor");
        json.addProperty("senderId", currentSenderId());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("enabled", config.hideArmor && !parts.isEmpty());
        json.add("parts", parts);
        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    public static void sendChannelSync(String channel, boolean enabled, java.util.List<String> channels) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;

        String senderId = currentSenderId();
        String username = mc.getUser().getName();

        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "channel_sync");
        json.addProperty("channel", channel != null ? channel : "");
        json.addProperty("enabled", enabled);
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);

        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        if (channels != null) {
            for (String ch : channels) {
                if (ch != null && !ch.isBlank()) {
                    array.add(ch.trim());
                }
            }
        }
        json.add("channels", array);

        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    public static void sendChannelSync(String channel, boolean enabled) {
        sendChannelSync(channel, enabled, IrcConfig.get().savedChannels);
    }

    /**
     * @param targetPlayer 接收目标玩家名
     * @param type         大类， "share" 或 "invite"
     * @param shareType    子类（如 "config", "nameStyle", "messageStyle", "channel"）
     * @param data         JSON 载荷数据
     */
    public static void sendShare(String targetPlayer, String type, String shareType, JsonObject data) {
        if (targetPlayer == null || targetPlayer.isBlank()) return;
        if (type == null || (!type.equalsIgnoreCase("share") && !type.equalsIgnoreCase("invite"))) {
            LOGGER.warn("[IRC] sendShare 失败：未知的包类型 [{}]，仅支持 'share' 或 'invite'", type);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getUser() == null) return;

        JsonObject json = new JsonObject();
        json.addProperty("type", type.toLowerCase());
        json.addProperty("senderId", mc.player.getStringUUID());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("target", targetPlayer.trim());
        json.addProperty("shareType", shareType != null ? shareType : "custom");
        json.add("data", data != null ? data : new JsonObject());

        if (IrcLink.isRunning) {
            IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 发送失败：未连接至 IRC 服务器，正在尝试连接...");
            connect();
        }
    }

    public static void sendShare(String targetPlayer, String type, String shareType, String content) {
        JsonObject data = new JsonObject();
        data.addProperty("content", content != null ? content : "");
        sendShare(targetPlayer, type, shareType, data);
    }

    public static void sendShare(String targetPlayer, String shareType, String content) {
        sendShare(targetPlayer, "share", shareType, content);
    }

    public static void sendInvite(String targetPlayer, String inviteType, String content) {
        sendShare(targetPlayer, "invite", inviteType, content);
    }

    public static void sendScaleSync(float scale) {
        Minecraft mc = Minecraft.getInstance();
        String senderId = currentSenderId();
        String username = mc.getUser().getName();

        JsonObject json = new JsonObject();
        json.addProperty("type", "function_sync");
        json.addProperty("function", "modelScale");
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("scale", scale);

        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    // 检查服务端是否已有该披风哈希
    public static void sendCapeCheck(String hash, String fileName) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject json = new JsonObject();
        json.addProperty("type", "cape_check");
        json.addProperty("senderId", currentSenderId());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("hash", hash);
        json.addProperty("fileName", fileName);
        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    // 向服务端上传披风的完整 Base64
    public static void sendCapeUpload(String hash, String fileName, String base64Data) {
        IrcTransport.sendRaw(IrcLink.GSON.toJson(capeUploadJson(hash, fileName, base64Data)));
    }

    // 广播佩戴指定哈希的披风
    public static void sendCapeSync(String hash, String fileName) {
        IrcTransport.sendRaw(IrcLink.GSON.toJson(capeSyncJson(hash, fileName)));
    }

    public static void sendCapeUploadAndSync(String hash, String fileName, String base64Data) {
        String upload = IrcLink.GSON.toJson(capeUploadJson(hash, fileName, base64Data));
        String sync = IrcLink.GSON.toJson(capeSyncJson(hash, fileName));
        IrcTransport.sendRawAsyncPair(upload, sync);
    }

    public static void sendImportantPlayersSync() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;

        JsonObject json = new JsonObject();
        json.addProperty("type", "important_players_sync");
        json.addProperty("senderId", currentSenderId());
        json.addProperty("username", mc.getUser().getName());

        JsonArray array = new JsonArray();
        List<String> list = IrcConfig.get().importantPlayers;
        if (list != null) {
            for (String p : list) {
                if (p != null && !p.isBlank()) {
                    array.add(p.trim());
                }
            }
        }
        json.add("players", array);
        json.add("importantPlayers", array);

        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    public static void sendTypingStatus(boolean isTyping) {
        if (!IrcLink.isRunning) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;

        JsonObject json = new JsonObject();
        json.addProperty("type", "typing");
        json.addProperty("senderId", currentSenderId());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("typing", isTyping);
        json.addProperty("action", isTyping ? "start" : "stop");

        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    private static JsonObject capeUploadJson(String hash, String fileName, String base64Data) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject json = new JsonObject();
        json.addProperty("type", "cape_upload");
        json.addProperty("senderId", currentSenderId());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("hash", hash);
        json.addProperty("fileName", fileName);
        json.addProperty("data", base64Data);
        return json;
    }

    private static JsonObject capeSyncJson(String hash, String fileName) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject json = new JsonObject();
        json.addProperty("type", "cape_sync");
        json.addProperty("senderId", currentSenderId());
        json.addProperty("username", mc.getUser().getName());
        json.addProperty("hash", hash);
        json.addProperty("fileName", fileName);
        return json;
    }

    private static String currentSenderId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getStringUUID() : mc.getUser().getProfileId().toString();
    }

    // 向服务端请求下载未命中的披风
    public static void sendCapeDownload(String hash) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "cape_download");
        json.addProperty("hash", hash);
        IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
    }

    public static void queryOnline() {
        if (IrcLink.isRunning) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "query_online");
            IrcTransport.sendRaw(IrcLink.GSON.toJson(json));
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 未连接至 IRC 服务器，正在尝试连接...");
            connect();
        }
    }

    public static void disconnect() {
        IrcLink.shouldAutoReconnect = false;
        IrcLink.reconnectAttempts.set(0);

        IrcLink.lastLatency = -1L;
        IrcLink.lastPingTimestamp = 0L;
        Minecraft.getInstance().execute(() -> {
            for (Consumer<Long> cb : IrcLink.pingCallbacks) {
                try {
                    cb.accept(-1L);
                } catch (RuntimeException ignored) {
                }
            }
            IrcLink.pingCallbacks.clear();
        });

        IrcTransport.stopHeartbeat();
        IrcTransport.closeResources();

        IrcLink.isRunning = false;
        isIrcChatActive = false;
        isIrcConnect = false;

        HideArmorManager.clear();
        IrcGlowManager.clear();
        LOGGER.info("[IRC] 已手动断开连接");
        ChatNotifier.send("§c[IRC] 已断开连接", NotificationType.INFO);
    }
}
