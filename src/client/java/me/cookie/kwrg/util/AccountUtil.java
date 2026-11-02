package me.cookie.kwrg.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.util.UndashedUuid;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.mixin.accessor.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class AccountUtil {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final AtomicLong REQUEST_ID_GENERATOR = new AtomicLong(0);

    public record LoginResult(boolean success, String username, String message) {}

    public static void cancelPendingLogins() {
        REQUEST_ID_GENERATOR.incrementAndGet();
    }

    public static CompletableFuture<LoginResult> loginWithToken(String rawToken) {
        final long currentRequestId = REQUEST_ID_GENERATOR.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanToken = rawToken.trim();
                if (cleanToken.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    cleanToken = cleanToken.substring(7).trim();
                }

                if (cleanToken.isEmpty()) {
                    return new LoginResult(false, null, "Token 不能为空");
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                        .header("Authorization", "Bearer " + cleanToken)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                // 如果网络请求返回时该请求已被取消或被新请求取代，直接中止
                if (REQUEST_ID_GENERATOR.get() != currentRequestId) {
                    return new LoginResult(false, null, "登录请求已取消");
                }

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (!json.has("id") || !json.has("name")) {
                        return new LoginResult(false, null, "响应数据异常，未找到档案");
                    }

                    String idStr = json.get("id").getAsString();
                    String name = json.get("name").getAsString();
                    UUID uuid = UndashedUuid.fromStringLenient(idStr);

                    final String finalToken = cleanToken;
                    Minecraft mc = Minecraft.getInstance();

                   return CompletableFuture.supplyAsync(() -> {
                        if (REQUEST_ID_GENERATOR.get() != currentRequestId) {
                            return new LoginResult(false, null, "登录请求已取消");
                        }

                        if (mc.level != null || mc.getConnection() != null) {
                            return new LoginResult(false, null, "已连接到服务器，无法切换账号");
                        }

                        User newUser = new User(
                                name,
                                uuid,
                                finalToken,
                                Optional.empty(),
                                Optional.empty()
                        );

                        ((MinecraftAccessor) mc).kwrg$setUser(newUser);
                        CookieMain.LOGGER.info("成功切换账户至: {} ({})", name, uuid);
                        return new LoginResult(true, name, "验证成功！已切换为: " + name);
                    }, mc).join();

                } else if (response.statusCode() == 401) {
                    return new LoginResult(false, null, "Token 无效或已过期 (HTTP 401)");
                } else {
                    return new LoginResult(false, null, "验证失败 (HTTP 状态码: " + response.statusCode() + ")");
                }
            } catch (IOException | InterruptedException | RuntimeException e) {
                CookieMain.LOGGER.error("Token 验证出错", e);
                return new LoginResult(false, null, "网络请求异常: " + e.getMessage());
            }
        });
    }

    public static String getAccessToken() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        if (user != null) {
            return user.getAccessToken();
        }
        return null;
    }

    public static String getUsername() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        if (user != null) {
            return user.getName();
        }
        return null;
    }
}