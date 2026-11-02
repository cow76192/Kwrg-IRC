package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ServerPackManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public static void handlePush(UUID id, String url, String hash, Consumer<Packet<?>> packetSender) {
        packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.ACCEPTED));

        Minecraft mc = Minecraft.getInstance();
        Path kwrgDir = mc.getResourcePackDirectory().resolve("kwrg");
        String rawHash = (hash == null || hash.isBlank()) ? "default" : hash.toLowerCase();
        String safeHash = (rawHash == null || rawHash.isBlank()) ? "default" : SafePaths.sanitizeFileName(rawHash, 120);
        Path targetZip = SafePaths.resolveUnder(kwrgDir, "server_" + safeHash, ".zip");
        if (targetZip == null) {
            LOGGER.error("[Kwrg] 材质包文件名非法，已放弃本次下载: server_{}.zip", safeHash);
            packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            return;
        }
        String newPackFileName = targetZip.getFileName().toString();
        String expectedPackId = "file/" + newPackFileName;

        boolean fileExists = Files.exists(targetZip) && targetZip.toFile().length() > 0;
        boolean isAlreadyActive = mc.options.resourcePacks.contains(expectedPackId);

        if (fileExists && !verify(targetZip, hash)) {
            LOGGER.warn("[Kwrg] 材质包缓存哈希校验失败，删除并重新下载: {} (hash={})", newPackFileName, hash);
            try {
                Files.deleteIfExists(targetZip);
            } catch (IOException | RuntimeException ignored) {}
            fileExists = false;
        }

        if (fileExists && isAlreadyActive) {
            LOGGER.info("[Kwrg] 材质包已缓存且已在启用状态: {}", newPackFileName);
            packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            return;
        }

        if (fileExists) {
            LOGGER.info("[Kwrg] 本地已有缓存但未加载，启用: {}", newPackFileName);
            ChatNotifier.sendInChatScreen("§e[Kwrg] 本地已有材质包缓存，正在应用...");
            mc.execute(() -> applyNewPack(id, newPackFileName, targetZip, kwrgDir, packetSender));
            return;
        }

        LOGGER.info("[Kwrg] 正在下载服务器新材质包: URL={}, Hash={}", url, safeHash);
        ChatNotifier.sendInChatScreen("§e[Kwrg] 检测到服务器材质包更新，正在下载...");

        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(kwrgDir);
                Path tempFile = targetZip.resolveSibling(targetZip.getFileName() + ".tmp");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(3))
                        .header("User-Agent", "Minecraft-Java-Client/Fabric")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    try (InputStream in = response.body()) {
                        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (!verify(tempFile, hash)) {
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (IOException | RuntimeException ignored) {}
                        LOGGER.warn("[Kwrg] 材质包内容哈希校验失败，已丢弃本次下载: hash={}, URL={}", hash, url);
                        packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD));
                        return;
                    }
                    Files.move(tempFile, targetZip, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("[Kwrg] 材质包下载完成并已保存至: {}", targetZip);

                    mc.execute(() -> applyNewPack(id, newPackFileName, targetZip, kwrgDir, packetSender));
                } else {
                    LOGGER.error("[Kwrg] 材质包下载失败，HTTP状态码: {}", response.statusCode());
                    ChatNotifier.sendInChatScreen("§c[Kwrg] 材质包下载失败 (HTTP " + response.statusCode() + ")");
                    packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD));
                }
            } catch (IOException | InterruptedException | RuntimeException e) {
                LOGGER.error("[Kwrg] 材质包下载异常", e);
                ChatNotifier.sendInChatScreen("§c[Kwrg] 材质包下载出错：" + e.getMessage());
                packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD));
            }
        });
    }

    private static boolean verify(Path file, String expectedHex) {
        if (expectedHex == null) {
            LOGGER.warn("[Kwrg] 材质包哈希缺失，跳过内容校验: {}", file);
            return true;
        }

        String algorithm;
        if (expectedHex.length() == 40) {
            algorithm = "SHA-1";
        } else if (expectedHex.length() == 64) {
            algorithm = "SHA-256";
        } else {
            LOGGER.warn("[Kwrg] 无法判定材质包哈希算法，跳过内容校验: {}", expectedHex);
            return true;
        }

        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return expectedHex.equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException | IOException | RuntimeException e) {
            return false;
        }
    }

    private static void applyNewPack(UUID packId, String newPackFileName, Path targetZip, Path kwrgDir, Consumer<Packet<?>> packetSender) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repository = mc.getResourcePackRepository();

        repository.reload();

        List<String> selectedPacks = new ArrayList<>();
        for (String packName : mc.options.resourcePacks) {
            if (!packName.contains("server_")) {
                selectedPacks.add(packName);
            }
        }

        String newPackId = "file/" + newPackFileName;
        if (!selectedPacks.contains(newPackId)) {
            selectedPacks.add(newPackId);
        }

        mc.options.resourcePacks.clear();
        mc.options.resourcePacks.addAll(selectedPacks);
        mc.options.save();

        deleteOldPacks(kwrgDir, targetZip);

        repository.setSelected(selectedPacks);

        mc.reloadResourcePacks().thenRun(() -> {
            packetSender.accept(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            ChatNotifier.sendInChatScreen("§a[Kwrg] 服务器材质包已成功加载");
            LOGGER.info("[Kwrg] 服务器材质包加载完成");
        });
    }

    private static void deleteOldPacks(Path kwrgDir, Path currentPackPath) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(kwrgDir, "server_*.zip")) {
            for (Path file : stream) {
                if (!file.equals(currentPackPath)) {
                    Files.deleteIfExists(file);
                    LOGGER.info("[Kwrg] 已清理旧版本材质包: {}", file.getFileName());
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }
}