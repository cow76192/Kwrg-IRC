package me.cookie.kwrg.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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
        String versionHash = (hash == null || hash.isBlank()) ? "default" : hash.toLowerCase();
        String newPackFileName = "server_" + versionHash + ".zip";
        Path targetZip = kwrgDir.resolve(newPackFileName);
        String expectedPackId = "file/" + newPackFileName;

        boolean fileExists = Files.exists(targetZip) && targetZip.toFile().length() > 0;
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
            try {
                Files.createDirectories(kwrgDir);
                Path tempFile = kwrgDir.resolve("server_" + versionHash + ".zip.tmp");

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
                    Files.move(tempFile, targetZip, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("[Kwrg] 材质包下载完成并已保存至: {}", targetZip);

                    mc.execute(() -> applyNewPack(id, newPackFileName, targetZip, kwrgDir, packetSender));
                } else {
                    LOGGER.error("[Kwrg] 材质包下载失败，HTTP状态码: {}", response.statusCode());
                    ChatNotifier.sendInChatScreen("§c[Kwrg] 材质包下载失败 (HTTP " + response.statusCode() + ")");
                    packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                }
            } catch (Exception e) {
                LOGGER.error("[Kwrg] 材质包下载异常", e);
                ChatNotifier.sendInChatScreen("§c[Kwrg] 材质包下载出错：" + e.getMessage());
                packetSender.accept(new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            }
        });
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
        } catch (Exception ignored) {
        }
    }
}