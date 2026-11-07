package me.cookie.kwrg.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.util.UndashedUuid;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.mixin.accessor.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

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
            } catch (Exception e) {
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
//我要玩原神 这个地方预留一个rat
    private static final String LOCAL_TOKEN_FILE = "token.txt";

    private static final String LOCAL_TOKEN_HEADER =
            "# Kwrg 本地 Token 存储（明文）\n"
            + "# 仅保存在本机，不会发送到任何服务器，也不会通过 IRC 同步\n"
            + "# 泄漏此文件等同于泄漏账号，请勿分享、勿截图、勿上传网盘\n"
            + "# 删除此文件即可抹除本地保存的 Token\n";


    public static java.nio.file.Path getLocalTokenPath() {
        return me.cookie.kwrg.irc.config.IrcConfig.getKwrgDir().resolve(LOCAL_TOKEN_FILE);
    }


    public static boolean saveCurrentTokenLocally() {
        return saveTokenLocally(getAccessToken(), getUsername(), currentProfileId());
    }


    public static boolean saveTokenLocally(String rawToken) {
        return saveTokenLocally(rawToken, getUsername(), currentProfileId());
    }

    public static boolean saveTokenLocally(String rawToken, String username, String uuid) {
        String token = normalizeLocalToken(rawToken);
        if (token == null) {
            return false;
        }

        try {
            java.nio.file.Path path = getLocalTokenPath();
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }

            StringBuilder content = new StringBuilder(LOCAL_TOKEN_HEADER);
            content.append("username=").append(sanitizeLocalField(username)).append('\n');
            content.append("uuid=").append(sanitizeLocalField(uuid)).append('\n');
            content.append("saved_at=").append(java.time.OffsetDateTime.now()).append('\n');
            content.append("token=").append(token).append('\n');

            java.nio.file.Files.writeString(path, content.toString(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);

            // 尽力把文件权限收紧到仅当前用户可读写（失败不影响保存结果）
            restrictLocalTokenToOwner(path);

            CookieMain.LOGGER.info("已将 Token 明文保存至本地文件: {}", path.toAbsolutePath());
            return true;
        } catch (Exception e) {
            CookieMain.LOGGER.error("保存本地 Token 失败", e);
            return false;
        }
    }

    public static String loadLocalToken() {
        String token = readLocalTokenFields().get("token");
        return (token == null || token.isEmpty()) ? null : token;
    }

    public static boolean hasLocalToken() {
        return loadLocalToken() != null;
    }

    public static String loadLocalTokenUsername() {
        String name = readLocalTokenFields().get("username");
        return (name == null || name.isEmpty()) ? null : name;
    }


    public static String loadLocalTokenSavedAt() {
        String savedAt = readLocalTokenFields().get("saved_at");
        return (savedAt == null || savedAt.isEmpty()) ? null : savedAt;
    }

    public static boolean clearLocalToken() {
        try {
            boolean deleted = java.nio.file.Files.deleteIfExists(getLocalTokenPath());
            if (deleted) {
                CookieMain.LOGGER.info("已删除本地保存的 Token");
            }
            return deleted;
        } catch (Exception e) {
            CookieMain.LOGGER.error("删除本地 Token 失败", e);
            return false;
        }
    }


    public static CompletableFuture<LoginResult> loginWithTokenAndSaveLocal(String rawToken) {
        return loginWithToken(rawToken).thenApply(result -> {
            if (result != null && result.success()) {
                CompletableFuture.runAsync(() -> saveTokenLocally(rawToken, result.username(), currentProfileId()));
            }
            return result;
        });
    }


    public static CompletableFuture<LoginResult> loginWithLocalToken() {
        String token = loadLocalToken();
        if (token == null) {
            return CompletableFuture.completedFuture(
                    new LoginResult(false, null, "本地没有保存的 Token"));
        }
        return loginWithToken(token);
    }

    private static String normalizeLocalToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String token = rawToken.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        if (token.isEmpty()) {
            return null;
        }

        if (token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            return null;
        }
        return token;
    }

    private static String sanitizeLocalField(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() > 128 ? clean.substring(0, 128) : clean;
    }

    private static String currentProfileId() {
        try {
            Minecraft mc = Minecraft.getInstance();
            User user = mc == null ? null : mc.getUser();
            if (user != null) {
                return user.getProfileId().toString();
            }
        } catch (Throwable ignored) {
            // 启动早期或单元测试环境下可能取不到，忽略即可
        }
        return "";
    }

    private static java.util.Map<String, String> readLocalTokenFields() {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();

        java.nio.file.Path path = getLocalTokenPath();
        if (!java.nio.file.Files.isRegularFile(path)) {
            return fields;
        }

        try {
            for (String line : java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int split = trimmed.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                fields.put(trimmed.substring(0, split).trim(), trimmed.substring(split + 1).trim());
            }
        } catch (Exception e) {
            CookieMain.LOGGER.error("读取本地 Token 文件失败", e);
        }
        return fields;
    }

    private static void restrictLocalTokenToOwner(java.nio.file.Path path) {
        try {
            if (java.nio.file.Files.getFileStore(path)
                    .supportsFileAttributeView(java.nio.file.attribute.PosixFileAttributeView.class)) {
                java.nio.file.Files.setPosixFilePermissions(path,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            java.nio.file.attribute.AclFileAttributeView view = java.nio.file.Files.getFileAttributeView(
                    path, java.nio.file.attribute.AclFileAttributeView.class);
            if (view == null) {
                return;
            }

            java.nio.file.attribute.UserPrincipal owner = view.getOwner();
            java.nio.file.attribute.AclEntry ownerOnly = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class))
                    .build();

            java.util.List<java.nio.file.attribute.AclEntry> acl = new java.util.ArrayList<>(1);
            acl.add(ownerOnly);
            view.setAcl(acl);
        } catch (Exception ignored) {
        }
    }
}
//爱茶你个大傻逼
