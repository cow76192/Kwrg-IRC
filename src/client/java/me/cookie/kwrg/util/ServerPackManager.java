package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.ArrayList;
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
        String rawHash = (hash == null || hash.isBlank()) ? "default" : hash.toLowerCase(Locale.ROOT);
        String versionHash = SafePaths.isHexId(rawHash) ? rawHash : SafePaths.sanitizeFileName(rawHash);
        if (versionHash.isEmpty()) {
            failDownload(id, packetSender, "服务器材质包摘要非法，已跳过", null);
            return;
        }
        if (hash != null && !hash.isBlank() && !supportedDigest(hash)) {
            LOGGER.warn("[Kwrg] 材质包摘要不可校验（需 40/64 位十六进制），跳过内容校验: {}", hash);
        }
        String newPackFileName = "server_" + versionHash + ".zip";
        Path targetZip = SafePaths.resolveUnder(kwrgDir, newPackFileName);
        if (targetZip == null) {
            failDownload(id, packetSender, "材质包缓存路径非法，已拒绝", null);
            return;
        }
        String expectedPackId = "file/" + newPackFileName;

        boolean fileExists = Files.exists(targetZip) && targetZip.toFile().length() > 0;
        if (fileExists && supportedDigest(hash) && !matchesDigest(targetZip, hash)) {
            LOGGER.warn("[Kwrg] 材质包缓存摘要不符，删除后重新下载: {}", newPackFileName);
            deleteQuietly(targetZip);
            fileExists = false;
        }
        boolean isAlreadyActive = mc.options.resourcePacks.contains(expectedPackId);

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

        LOGGER.info("[Kwrg] 正在下载服务器新材质包: URL={}, Hash={}", url, versionHash);
        ChatNotifier.sendInChatScreen("§e[Kwrg] 检测到服务器材质包更新，正在下载...");

        CompletableFuture.runAsync(() -> {
            Path tempFile = SafePaths.resolveUnder(kwrgDir, "server_" + versionHash + ".zip.tmp");
            if (tempFile == null) {
                failDownload(id, packetSender, "材质包临时文件路径非法，已拒绝", null);
                return;
            }
            try {
                Files.createDirectories(kwrgDir);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(3))
                        .header("User-Agent", "Minecraft-Java-Client/Fabric")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String expectedDigest = supportedDigest(hash) ? hash.toLowerCase(Locale.ROOT) : null;
                    try (InputStream in = response.body()) {
                        if (!copyChunked(in, tempFile, expectedDigest)) {
                            deleteQuietly(tempFile);
                            failDownload(id, packetSender, "材质包内容校验失败（摘要不符），已拒绝加载", null);
                            return;
                        }
                    }
                    Files.move(tempFile, targetZip, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("[Kwrg] 材质包下载完成并已保存至: {}", targetZip);

                    mc.execute(() -> applyNewPack(id, newPackFileName, targetZip, kwrgDir, packetSender));
                } else {
                    response.body().close();
                    deleteQuietly(tempFile);
                    failDownload(id, packetSender, "材质包下载失败 (HTTP " + response.statusCode() + ")", null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deleteQuietly(tempFile);
                failDownload(id, packetSender, "材质包下载被中断", e);
            } catch (IOException | RuntimeException e) {
                deleteQuietly(tempFile);
                failDownload(id, packetSender, "材质包下载出错：" + e.getMessage(), e);
            }
        });
    }

    private static void failDownload(UUID id, Consumer<Packet<?>> packetSender, String reason, Throwable error) {
        if (error != null) {
            LOGGER.error("[Kwrg] {}", reason, error);
        } else {
            LOGGER.warn("[Kwrg] {}", reason);
        }
        ChatNotifier.sendInChatScreen("§c[Kwrg] " + reason);
        packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD));
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static boolean supportedDigest(String hash) {
        return SafePaths.isHexId(hash) && digestAlgorithm(hash) != null;
    }

    private static String digestAlgorithm(String hash) {
        if (hash.length() == 40) {
            return "SHA-1";
        }
        return hash.length() == 64 ? "SHA-256" : null;
    }

    private static boolean matchesDigest(Path file, String expected) {
        try {
            MessageDigest digest = digestFor(expected);
            try (InputStream in = Files.newInputStream(file);
                 DigestInputStream stream = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (stream.read(buffer) != -1) {
                }
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
        } catch (IOException e) {
            LOGGER.warn("[Kwrg] 材质包缓存摘要计算失败: {}", file, e);
            return false;
        }
    }

    private static boolean copyChunked(InputStream in, Path target, String expected) throws IOException {
        MessageDigest digest = expected == null ? null : digestFor(expected);
        try (InputStream source = digest == null ? in : new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return digest == null || HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
    }

    private static MessageDigest digestFor(String expected) {
        try {
            return MessageDigest.getInstance(digestAlgorithm(expected));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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