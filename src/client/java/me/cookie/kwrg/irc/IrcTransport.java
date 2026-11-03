package me.cookie.kwrg.irc;

import com.google.gson.JsonObject;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.irc.cosmetic.HideArmorManager;
import me.cookie.kwrg.irc.cosmetic.IrcGlowManager;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class IrcTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc");

    private static Socket socket;
    private static PrintWriter writer;
    private static BufferedReader reader;

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    static final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    static volatile boolean shouldAutoReconnect = false;
    static final AtomicBoolean isConnecting = new AtomicBoolean(false);

    private static final Object SEND_LOCK = new Object();
    private static ExecutorService sendExecutor;

    private static ScheduledExecutorService heartbeatExecutor;
    private static final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "IRC-Reconnect-Thread");
        t.setDaemon(true);
        return t;
    });

    private IrcTransport() {
    }

    static boolean isAlive() {
        Socket current = socket;
        return IrcClient.isRunning && current != null && !current.isClosed();
    }

    static void doConnect(boolean isSilent) {
        if (!isConnecting.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                closeResources();

                socket = new Socket();
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(IrcClient.HOST, IrcClient.PORT), 5000);

                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                IrcClient.isRunning = true;
                IrcClient.isIrcConnect = true;

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
                sendRaw(IrcClient.GSON.toJson(joinJson));

                IrcClient.sendHideArmorSync();
                IrcClient.sendScaleSync(IrcConfig.get().myScale);
                me.cookie.kwrg.irc.cosmetic.IrcCapeManager.syncMyCape();
                IrcClient.sendChannelSync(IrcConfig.get().currentChannel, IrcConfig.get().channelEnabled, IrcConfig.get().savedChannels);
                IrcClient.queryOnline();
                startHeartbeat();
                IrcClient.sendImportantPlayersSync();
                listenLoop();
            } catch (IOException | RuntimeException e) {
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
            try {
                Socket current = socket;
                if (IrcClient.isRunning && current != null && !current.isClosed()) {
                    IrcClient.sendPing(null);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("[IRC] 心跳发送异常: {}", e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    static void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }

    private static void listenLoop() {
        try {
            String line;
            while (IrcClient.isRunning && reader != null && (line = reader.readLine()) != null) {
                String rawJson = line.trim();
                if (!rawJson.isEmpty()) {
                    if (rawJson.contains("\"type\":\"pong\"") || rawJson.contains("\"type\": \"pong\"")) {
                        if (IrcClient.lastPingTimestamp > 0) {
                            long latency = System.currentTimeMillis() - IrcClient.lastPingTimestamp;
                            IrcClient.lastLatency = latency;
                            IrcClient.lastPingTimestamp = 0L;

                            Minecraft.getInstance().execute(() -> {
                                for (Consumer<Long> cb : IrcClient.pingCallbacks) {
                                    try {
                                        cb.accept(latency);
                                    } catch (RuntimeException ignored) {
                                    }
                                }
                                IrcClient.pingCallbacks.clear();
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
        } catch (IOException | RuntimeException e) {
            if (IrcClient.isRunning) {
                LOGGER.warn("[IRC] 接收线程中断: {}", e.getMessage());
            }
        } finally {
            handleDisconnect();
        }
    }

    private static synchronized void handleDisconnect() {
        IrcClient.isRunning = false;
        IrcClient.isIrcConnect = false;
        IrcClient.isIrcChatActive = false;

        IrcClient.lastLatency = -1L;
        IrcClient.lastPingTimestamp = 0L;
        Minecraft.getInstance().execute(() -> {
            for (Consumer<Long> cb : IrcClient.pingCallbacks) {
                try {
                    cb.accept(-1L);
                } catch (RuntimeException ignored) {
                }
            }
            IrcClient.pingCallbacks.clear();
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
                    if (!IrcClient.isRunning && shouldAutoReconnect) {
                        doConnect(true);
                    }
                }, 5, TimeUnit.SECONDS);
            }
        }
    }

    static void sendRaw(String jsonStr) {
        sendExecutor().execute(() -> {
            synchronized (SEND_LOCK) {
                if (writer != null && IrcClient.isRunning) {
                    writer.println(jsonStr);
                }
            }
        });
    }

    static void sendRawAsyncPair(String first, String second) {
        CompletableFuture.runAsync(() -> {
            synchronized (SEND_LOCK) {
                if (writer != null && IrcClient.isRunning) {
                    writer.println(first);
                    writer.println(second);
                }
            }
        });
    }

    private static ExecutorService sendExecutor() {
        synchronized (SEND_LOCK) {
            if (sendExecutor == null || sendExecutor.isShutdown()) {
                sendExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "IRC-Send-Thread");
                    t.setDaemon(true);
                    return t;
                });
            }
            return sendExecutor;
        }
    }

    static void closeResources() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException | RuntimeException ignored) {
        }
        writer = null;
        reader = null;
        socket = null;
        synchronized (SEND_LOCK) {
            if (sendExecutor != null && !sendExecutor.isShutdown()) {
                sendExecutor.shutdownNow();
            }
            sendExecutor = null;
        }
    }
}
