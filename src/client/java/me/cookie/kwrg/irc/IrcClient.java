package me.cookie.kwrg.irc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.manager.HideArmorManager;
import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.module.modules.functions.ChatSuffixModule;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class IrcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc");
    private static final Gson GSON = new Gson();
    private static final String HOST = "47.96.141.237";
    private static final int PORT = 13140;

    private static Socket socket;
    private static PrintWriter writer;
    private static BufferedReader reader;
    private static volatile boolean isRunning = false;

    public static boolean isIrcChatActive = false;
    public static boolean isIrcConnect = false;

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private static volatile boolean shouldAutoReconnect = false;
    private static final AtomicBoolean isConnecting = new AtomicBoolean(false);

    private static final Object SEND_LOCK = new Object();

    private static ScheduledExecutorService heartbeatExecutor;
    private static final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "IRC-Reconnect-Thread");
        t.setDaemon(true);
        return t;
    });

    private static volatile long lastPingTimestamp = 0L;
    private static volatile long lastLatency = -1L;
    private static final List<Consumer<Long>> pingCallbacks = new CopyOnWriteArrayList<>();

    public static long getLastLatency() {
        return lastLatency;
    }

    public static void sendPing(Consumer<Long> onResult) {
        if (!isRunning || socket == null || socket.isClosed()) {
            if (onResult != null) {
                Minecraft.getInstance().execute(() -> onResult.accept(-1L));
            }
            return;
        }

        if (onResult != null) {
            pingCallbacks.add(onResult);
        }

        lastPingTimestamp = System.currentTimeMillis();
        JsonObject ping = new JsonObject();
        ping.addProperty("type", "ping");
        sendRaw(GSON.toJson(ping));
    }

    public static void connect() {
        if (isRunning) {
            ChatNotifier.sendInChatScreen("§c[IRC] 已经连接至 IRC 服务器了喵~");
            return;
        }

        if (isConnecting.get()) {
            ChatNotifier.send("§e[IRC] 正在连接中，请稍候...", NotificationType.INFO);
            return;
        }

        reconnectAttempts.set(0);
        shouldAutoReconnect = true;

        doConnect(false);
    }

    private static void doConnect(boolean isSilent) {
        if (!isConnecting.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                closeResources();

                socket = new Socket();
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(HOST, PORT), 5000);

                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                isRunning = true;
                isIrcConnect = true;

                reconnectAttempts.set(0);

                if (isSilent) {
                    ChatNotifier.send("§a[IRC] 重新连接成功！", NotificationType.SUCCESS);
                    LOGGER.info("[IRC] 静默重连成功");
                } else {
                    ChatNotifier.send("§a[IRC] 成功连接至 IRC 服务器！", NotificationType.SUCCESS);
                    LOGGER.info("[IRC] 手动连接成功");
                }

                JsonObject joinJson = new JsonObject();
                joinJson.addProperty("type", "join");
                joinJson.addProperty("senderId", Minecraft.getInstance().getUser().getProfileId().toString());
                joinJson.addProperty("username", Minecraft.getInstance().getUser().getName());
                joinJson.addProperty("rank", IrcConfig.get().rank);
                sendRaw(GSON.toJson(joinJson));

                sendHideArmorSync();
                sendScaleSync(IrcConfig.get().myScale);
                me.cookie.kwrg.irc.manager.IrcCapeManager.syncMyCape();
                sendChannelSync(IrcConfig.get().currentChannel, IrcConfig.get().channelEnabled, IrcConfig.get().savedChannels);
                queryOnline();
                startHeartbeat();
                listenLoop();
            } catch (Exception e) {
                LOGGER.warn("[IRC] 连接尝试失败 (isSilent={}): {}", isSilent, e.getMessage());
                if (!isSilent) {
                    ChatNotifier.send("§c[IRC] 无法连接到 IRC 服务器: " + e.getMessage(), NotificationType.ERR);
                }
                handleDisconnect();
            } finally {
                isConnecting.set(false);
            }
        });
    }

    private static void startHeartbeat() {
        stopHeartbeat();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IRC-Heartbeat-Thread");
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (isRunning && socket != null && !socket.isClosed()) {
                sendPing(null);
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private static void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }

    private static void listenLoop() {
        try {
            String line;
            while (isRunning && reader != null && (line = reader.readLine()) != null) {
                String rawJson = line.trim();
                if (!rawJson.isEmpty()) {
                    if (rawJson.contains("\"type\":\"pong\"") || rawJson.contains("\"type\": \"pong\"")) {
                        if (lastPingTimestamp > 0) {
                            long latency = System.currentTimeMillis() - lastPingTimestamp;
                            lastLatency = latency;
                            lastPingTimestamp = 0L;

                            Minecraft.getInstance().execute(() -> {
                                for (Consumer<Long> cb : pingCallbacks) {
                                    try {
                                        cb.accept(latency);
                                    } catch (Exception ignored) {
                                    }
                                }
                                pingCallbacks.clear();
                            });
                        }
                        continue;
                    }

                    if (LOGGER.isInfoEnabled()) {
                        String logText = rawJson;
                        if (logText.contains("\"data\"")) {
                            logText = logText.replaceAll("\"data\"\\s*:\\s*\"[^\"]+\"", "\"data\":\"<Base64 Image Omitted>\"");
                        }
                        if (logText.length() > 250) {
                            logText = logText.substring(0, 250) + "... [总长 " + rawJson.length() + " 字节]";
                        }
                        LOGGER.info("[IRC-RECV] 收到服务端广播: {}", logText);
                    }

                    Minecraft.getInstance().execute(() -> IrcMessageHandler.handleIncomingJson(rawJson));
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                LOGGER.warn("[IRC] 接收线程中断: {}", e.getMessage());
            }
        } finally {
            handleDisconnect();
        }
    }

    private static synchronized void handleDisconnect() {
        isRunning = false;
        isIrcConnect = false;
        isIrcChatActive = false;

        lastLatency = -1L;
        lastPingTimestamp = 0L;
        Minecraft.getInstance().execute(() -> {
            for (Consumer<Long> cb : pingCallbacks) {
                try {
                    cb.accept(-1L);
                } catch (Exception ignored) {
                }
            }
            pingCallbacks.clear();
        });

        stopHeartbeat();
        closeResources();

        HideArmorManager.clear();
        IrcGlowManager.clear();

        if (shouldAutoReconnect) {
            int currentAttempt = reconnectAttempts.incrementAndGet();
            if (currentAttempt > MAX_RECONNECT_ATTEMPTS) {
                shouldAutoReconnect = false;
                LOGGER.info("[IRC] 自动重连已达 3 次上限，放弃重试。等待手动连接。");
                ChatNotifier.send("§c[IRC] 与服务器连接断开，重连失败(3/3)", NotificationType.ERR);
            } else {
                LOGGER.info("[IRC] 连接断开，执行第 {}/{} 次重连...", currentAttempt, MAX_RECONNECT_ATTEMPTS);
                reconnectExecutor.schedule(() -> {
                    if (!isRunning && shouldAutoReconnect) {
                        doConnect(true);
                    }
                }, 5, TimeUnit.SECONDS);
            }
        }
    }

    private static void sendRaw(String jsonStr) {
        CompletableFuture.runAsync(() -> {
            synchronized (SEND_LOCK) {
                if (writer != null && isRunning) {
                    writer.println(jsonStr);
                }
            }
        });
    }

    public static void sendMessage(String content) {
        if (content == null || content.isBlank()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String username = mc.getUser().getName();
        String senderId = mc.player.getStringUUID();

        IrcConfig config = IrcConfig.get();

        // 语气词装饰统一在此处理，覆盖聊天框输入与 /ic、/icall 等所有 IRC 发送路径
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

        if (isRunning) {
            sendRaw(GSON.toJson(json));
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

        if (isRunning) {
            sendRaw(GSON.toJson(json));
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

        if (isRunning) {
            sendRaw(GSON.toJson(json));
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

        sendRaw(GSON.toJson(json));
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

        sendRaw(GSON.toJson(json));
    }

    public static void sendHideArmorSync() {
        IrcConfig config = IrcConfig.get();

        sendFunctionSync("hideArmor", config.hideArmor && HideArmorManager.hasAnyPartHidden());
        sendFunctionSync("hideArmorHead", config.hideArmor && HideArmorManager.isPartHidden(EquipmentSlot.HEAD));
        sendFunctionSync("hideArmorChest", config.hideArmor && HideArmorManager.isPartHidden(EquipmentSlot.CHEST));
        sendFunctionSync("hideArmorLegs", config.hideArmor && HideArmorManager.isPartHidden(EquipmentSlot.LEGS));
        sendFunctionSync("hideArmorFeet", config.hideArmor && HideArmorManager.isPartHidden(EquipmentSlot.FEET));
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

        sendRaw(GSON.toJson(json));
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

        if (isRunning) {
            sendRaw(GSON.toJson(json));
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

        sendRaw(GSON.toJson(json));
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
        sendRaw(GSON.toJson(json));
    }

    // 向服务端上传披风的完整 Base64
    public static void sendCapeUpload(String hash, String fileName, String base64Data) {
        sendRaw(GSON.toJson(capeUploadJson(hash, fileName, base64Data)));
    }

    // 广播佩戴指定哈希的披风
    public static void sendCapeSync(String hash, String fileName) {
        sendRaw(GSON.toJson(capeSyncJson(hash, fileName)));
    }

    public static void sendCapeUploadAndSync(String hash, String fileName, String base64Data) {
        String upload = GSON.toJson(capeUploadJson(hash, fileName, base64Data));
        String sync = GSON.toJson(capeSyncJson(hash, fileName));
        CompletableFuture.runAsync(() -> {
            synchronized (SEND_LOCK) {
                if (writer != null && isRunning) {
                    writer.println(upload);
                    writer.println(sync);
                }
            }
        });
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
        sendRaw(GSON.toJson(json));
    }

    public static void queryOnline() {
        if (isRunning) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "query_online");
            sendRaw(GSON.toJson(json));
        } else {
            IrcMessageHandler.printMessage("§c[IRC] 未连接至 IRC 服务器，正在尝试连接...");
            connect();
        }
    }

    public static void disconnect() {
        shouldAutoReconnect = false;
        reconnectAttempts.set(0);

        lastLatency = -1L;
        lastPingTimestamp = 0L;
        Minecraft.getInstance().execute(() -> {
            for (Consumer<Long> cb : pingCallbacks) {
                try {
                    cb.accept(-1L);
                } catch (Exception ignored) {
                }
            }
            pingCallbacks.clear();
        });

        stopHeartbeat();
        closeResources();

        isRunning = false;
        isIrcChatActive = false;
        isIrcConnect = false;

        HideArmorManager.clear();
        IrcGlowManager.clear();
        LOGGER.info("[IRC] 已手动断开连接");
        ChatNotifier.send("§c[IRC] 已断开连接", NotificationType.INFO);
    }

    private static void closeResources() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {
        }
        writer = null;
        reader = null;
        socket = null;
    }
}