package me.cookie.kwrg.irc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.cookie.kwrg.irc.functions.HideArmorManager;
import me.cookie.kwrg.irc.functions.IrcGlowManager;
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
import java.util.concurrent.CompletableFuture;

public class IrcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc");
    private static final Gson GSON = new Gson();
    private static final String HOST = "47.96.141.237";
    private static final int PORT = 13140;

    private static Socket socket;
    private static PrintWriter writer;
    private static BufferedReader reader;
    private static boolean isRunning = false;

    public static boolean isIrcChatActive = false;
    public static boolean isIrcConnect = false;

    public static void connect() {
        if (isRunning) {
            ChatNotifier.sendInChatScreen("§c[IRC] 已经连接至 IRC 服务器了喵~");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(HOST, PORT), 5000);
                socket.setTcpNoDelay(true);

                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                isRunning = true;

                ChatNotifier.send("§a[IRC] 成功连接至 IRC 服务器！");
                isIrcConnect = true;

                JsonObject joinJson = new JsonObject();
                joinJson.addProperty("type", "join");
                joinJson.addProperty("senderId", Minecraft.getInstance().getUser().getProfileId().toString());
                joinJson.addProperty("username", Minecraft.getInstance().getUser().getName());
                joinJson.addProperty("rank", IrcConfig.get().rank);
                writer.println(GSON.toJson(joinJson));

                sendFunctionSync("hideArmor", IrcConfig.get().hideArmor);
                queryOnline();

                listenLoop();
            } catch (Exception e) {
                LOGGER.error("[IRC] 连接失败: {}", e.getMessage());
                ChatNotifier.send("§c[IRC] 无法连接到 IRC 服务器: " + e.getMessage());
                disconnect();
            }
        });
    }

    private static void listenLoop() {
        try {
            String line;
            while (isRunning && reader != null && (line = reader.readLine()) != null) {
                String rawJson = line.trim();
                if (!rawJson.isEmpty()) {
                    LOGGER.info("[IRC-RECV] 收到服务端广播: {}", rawJson);
                    Minecraft.getInstance().execute(() -> IrcMessageHandler.handleIncomingJson(rawJson));
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                LOGGER.warn("[IRC] 接收中断: {}", e.getMessage());
                IrcMessageHandler.printMessage("§c[IRC] 与服务器的连接已断开");
                ChatNotifier.send("§c[IRC] 与服务器的连接已断开");
                isIrcConnect = false;
            }
        } finally {
            disconnect();
        }
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

        String jsonStr = GSON.toJson(json);

        IrcMessageHandler.displayFormattedMessage(config.rank, config.namestyle, username, config.chatstyle, content);

        CompletableFuture.runAsync(() -> {
            if (writer != null && isRunning) {
                writer.println(jsonStr);
            } else {
                IrcMessageHandler.printMessage("§c[IRC] 发送失败：未连接至 IRC 服务器！正在尝试重连...");
                connect();
            }
        });
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

        String jsonStr = GSON.toJson(json);
        CompletableFuture.runAsync(() -> {
            if (writer != null && isRunning) {
                writer.println(jsonStr);
            } else {
                IrcMessageHandler.printMessage("§c[IRC] 发送失败：未连接至 IRC 服务器,正在尝试重连...");
                connect();
            }
        });
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

        String jsonStr = GSON.toJson(json);
        CompletableFuture.runAsync(() -> {
            if (writer != null && isRunning) {
                writer.println(jsonStr);
            }
        });
    }

    public static void sendFunctionSync(String functionName, boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;

        String senderId = mc.player != null ? mc.player.getStringUUID() : mc.getUser().getProfileId().toString();
        String username = mc.getUser().getName();

        JsonObject json = new JsonObject();
        json.addProperty("type", "function_sync");
        json.addProperty("function", functionName);
        json.addProperty("senderId", senderId);
        json.addProperty("username", username);
        json.addProperty("enabled", enabled);

        String jsonStr = GSON.toJson(json);
        CompletableFuture.runAsync(() -> {
            if (writer != null && isRunning) {
                writer.println(jsonStr);
            }
        });
    }

    public static void queryOnline() {
        CompletableFuture.runAsync(() -> {
            if (writer != null && isRunning) {
                JsonObject json = new JsonObject();
                json.addProperty("type", "query_online");
                writer.println(GSON.toJson(json));
            } else {
                IrcMessageHandler.printMessage("§c[IRC] 未连接至 IRC 服务器,正在尝试重连...");
                connect();
            }
        });
    }

    public static void disconnect() {
        isRunning = false;
        isIrcChatActive = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {
        }
        writer = null;
        reader = null;
        socket = null;
        HideArmorManager.clear();
        IrcGlowManager.clear();
        LOGGER.info("[IRC] 已断开连接");
        ChatNotifier.send("§c[IRC] 已断开连接");
        isIrcConnect = false;
    }
}