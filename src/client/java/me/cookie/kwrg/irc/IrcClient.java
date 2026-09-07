package me.cookie.kwrg.irc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.cookie.kwrg.irc.manager.HideArmorManager;
import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
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
            ChatNotifier.send("§e[IRC] 正在连接中，请稍候...");
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
                    ChatNotifier.send("§a[IRC] 重新连接成功！");
                    LOGGER.info("[IRC] 静默重连成功");
                } else {
                    ChatNotifier.send("§a[IRC] 成功连接至 IRC 服务器！");
                    LOGGER.info("[IRC] 手动连接成功");
                }

                JsonObject joinJson = new JsonObject();
                joinJson.addProperty("type", "join");
                joinJson.addProperty("senderId", Minecraft.getInstance().getUser().getProfileId().toString());
                joinJson.addProperty("username", Minecraft.getInstance().getUser().getName());
                joinJson.addProperty("rank", IrcConfig.get().rank);
                sendRaw(GSON.toJson(joinJson));

                sendFunctionSync("hideArmor", IrcConfig.get().hideArmor);
                sendChannelSync(IrcConfig.get().currentChannel, IrcConfig.get().channelEnabled, IrcConfig.get().savedChannels);
                queryOnline();
                startHeartbeat();
                listenLoop();
            } catch (Exception e) {
                LOGGER.warn("[IRC] 连接尝试失败 (isSilent={}): {}", isSilent, e.getMessage());
                if (!isSilent) {
                    ChatNotifier.send("§c[IRC] 无法连接到 IRC 服务器: " + e.getMessage());
                }
                handleDisconnect();
            } finally {
                isConnecting.set(false);
            }
        });
    }

    /**
     * 启动定时心跳包（每 15 秒一次）
     */
    private static void startHeartbeat() {
        stopHeartbeat();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IRC-Heartbeat-Thread");
            t.setDaemon(true);
            return t;
        });

        // ★ initialDelay 设为 0，连上立刻 ping 一次，随后每 15 秒采样一次 ★
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
                    // 收到 pong 心跳处理延迟计算与回调响应
                    if (rawJson.contains("\"type\":\"pong\"") || rawJson.contains("\"type\": \"pong\"")) {
                        if (lastPingTimestamp > 0) {
                            long latency = System.currentTimeMillis() - lastPingTimestamp;
                            lastLatency = latency;
                            lastPingTimestamp = 0L;

                            // 派发到客户端主线程执行回调
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

                    LOGGER.info("[IRC-RECV] 收到服务端广播: {}", rawJson);
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

    /**
     * 统一断开连接及静默重试逻辑
     */
    private static synchronized void handleDisconnect() {
        isRunning = false;
        isIrcConnect = false;
        isIrcChatActive = false;

        // 清理测速状态与待处理回调
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
                // 达到 3 次上限，彻底放弃
                shouldAutoReconnect = false;
                LOGGER.info("[IRC] 自动重连已达 3 次上限，放弃重试。等待手动连接。");
                ChatNotifier.send("§c[IRC] 与服务器连接断开，重连失败(3/3)");
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

        JsonObject json = new JsonObject();
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("rank", config.rank);
        json.addProperty("namestyle", config.namestyle);
        json.addProperty("chatstyle", config.chatstyle);
        json.addProperty("content", content);
        if (config.channelEnabled && config.currentChannel != null && !config.currentChannel.isBlank()) {
            json.addProperty("channel", config.currentChannel);
        }


        String channel = "";
        if (config.channelEnabled && config.currentChannel != null && !config.currentChannel.isBlank()) {
            channel = config.currentChannel;
            json.addProperty("channel", channel);
        }

        IrcMessageHandler.displayFormattedMessage(channel, config.rank, config.namestyle, username, config.chatstyle, content);

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

        JsonObject json = new JsonObject();
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("rank", config.rank);
        json.addProperty("namestyle", config.namestyle);
        json.addProperty("chatstyle", config.chatstyle);
        json.addProperty("content", content);
        json.addProperty("channel", "");

        IrcMessageHandler.displayFormattedMessage("", config.rank, config.namestyle, username, config.chatstyle, content);

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

        String senderId = mc.player != null ? mc.player.getStringUUID() : mc.getUser().getProfileId().toString();
        String username = mc.getUser().getName();

        JsonObject json = new JsonObject();
        json.addProperty("type", "function_sync");
        json.addProperty("function", functionName);
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("enabled", enabled);

        sendRaw(GSON.toJson(json));
    }

    public static void sendChannelSync(String channel, boolean enabled, java.util.List<String> channels) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;

        String senderId = mc.player != null ? mc.player.getStringUUID() : mc.getUser().getProfileId().toString();
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

        // 清理测速状态
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
        ChatNotifier.send("§c[IRC] 已断开连接");
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