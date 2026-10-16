package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IrcCapeManager {

    public record CapeMeta(String hash, String fileName) {}

    private static final Map<UUID, CapeMeta> UUID_TO_CAPE = new ConcurrentHashMap<>();
    private static final Map<String, CapeMeta> NAME_TO_CAPE = new ConcurrentHashMap<>();

    private static final Set<String> DOWNLOADING_HASHES = ConcurrentHashMap.newKeySet();

    private static final Map<String, Set<UUID>> PENDING_ENTITIES = new ConcurrentHashMap<>();

    public static Path getCacheDir() {
        Path cacheDir = IrcConfig.getKwrgDir().resolve("capes").resolve("cache");
        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
        } catch (Exception ignored) {}
        return cacheDir;
    }

    public static String computeFileHash(Path file) {
        if (!Files.exists(file)) return null;
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xFF & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void syncMyCape() {
        String myCape = PlayerCustomizationManager.getMyCape();
        if (myCape == null || myCape.isBlank()) {
            IrcClient.sendCapeSync("", "");
            return;
        }

        Path capeFile = PlayerCustomizationManager.getCapeDir().resolve(myCape);
        if (!Files.exists(capeFile)) return;

        String hash = computeFileHash(capeFile);
        if (hash != null) {
            IrcClient.sendCapeCheck(hash, myCape);
        }
    }

    public static void handleCheckResult(String hash, boolean exists) {
        String myCape = PlayerCustomizationManager.getMyCape();
        if (myCape == null || myCape.isBlank()) return;

        Path capeFile = PlayerCustomizationManager.getCapeDir().resolve(myCape);
        String currentHash = computeFileHash(capeFile);
        if (currentHash == null || !currentHash.equalsIgnoreCase(hash)) return;

        if (!exists) {
            try {
                byte[] bytes = Files.readAllBytes(capeFile);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                IrcClient.sendCapeUploadAndSync(hash, myCape, base64);
            } catch (Exception ignored) {}
        } else {
            IrcClient.sendCapeSync(hash, myCape);
        }
    }

    public static void handleRemoteCapeSync(String uuidStr, String username, String hash, String fileName) {
        UUID uuid = null;
        try {
            if (uuidStr != null && !uuidStr.isBlank()) {
                uuid = UUID.fromString(uuidStr);
            }
        } catch (Exception ignored) {}

        if (hash == null || hash.isBlank()) {
            if (uuid != null) UUID_TO_CAPE.remove(uuid);
            if (username != null) NAME_TO_CAPE.remove(username.toLowerCase(Locale.ROOT));

            PlayerCustomizationManager.setIrcPlayerCustomization(username, null, 1.0f);
            return;
        }

        CapeMeta meta = new CapeMeta(hash.toLowerCase(Locale.ROOT), fileName != null ? fileName : "");
        if (uuid != null) UUID_TO_CAPE.put(uuid, meta);
        if (username != null && !username.isBlank()) {
            NAME_TO_CAPE.put(username.toLowerCase(Locale.ROOT), meta);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Player loadedPlayer = uuid != null ? mc.level.getPlayerByUUID(uuid) : null;
            if (loadedPlayer != null && loadedPlayer != mc.player) {
                applyCapeForPlayer(loadedPlayer, meta);
            }
        }
    }

    public static void handlePlayerLeave(String uuidStr, String username) {
        try {
            if (uuidStr != null && !uuidStr.isBlank()) {
                UUID_TO_CAPE.remove(UUID.fromString(uuidStr));
            }
        } catch (Exception ignored) {}
        if (username != null) {
            NAME_TO_CAPE.remove(username.toLowerCase(Locale.ROOT));
        }
    }

    public static void onEntityLoad(Player player) {
        if (player == null || player == Minecraft.getInstance().player) return;

        CapeMeta meta = getCapeMeta(player);
        if (meta == null || meta.hash().isBlank()) {
            return;
        }

        applyCapeForPlayer(player, meta);
    }

    public static void onEntityUnload(Player player) {
        if (player == null || player == Minecraft.getInstance().player) return;

        String name = player.getGameProfile().name();
        PlayerCustomizationManager.unbindPlayer(name);

        UUID uuid = player.getUUID();
        for (Set<UUID> waiters : PENDING_ENTITIES.values()) {
            waiters.remove(uuid);
        }
    }

    private static CapeMeta getCapeMeta(Player player) {
        CapeMeta meta = UUID_TO_CAPE.get(player.getUUID());
        if (meta == null) {
            meta = NAME_TO_CAPE.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
        }
        return meta;
    }

    private static void applyCapeForPlayer(Player player, CapeMeta meta) {
        String cacheFileName = resolveCacheFileName(meta.hash(), meta.fileName());
        Path cachedFile = getCacheDir().resolve(cacheFileName);

        if (Files.exists(cachedFile)) {
            PlayerCustomizationManager.setIrcPlayerCustomization(
                    player.getGameProfile().name(),
                    "cache/" + cacheFileName,
                    1.0f
            );
        } else {
            PENDING_ENTITIES.computeIfAbsent(meta.hash(), k -> ConcurrentHashMap.newKeySet()).add(player.getUUID());

            if (DOWNLOADING_HASHES.add(meta.hash())) {
                IrcClient.sendCapeDownload(meta.hash());
            }
        }
    }

    public static void handleIncomingCapeData(String hash, String fileName, String base64Data) {
        if (hash == null || base64Data == null || base64Data.isBlank()) return;

        String lowerHash = hash.toLowerCase(Locale.ROOT);
        DOWNLOADING_HASHES.remove(lowerHash);

        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Data);
                String cacheFileName = resolveCacheFileName(lowerHash, fileName);
                Path targetPath = getCacheDir().resolve(cacheFileName);
                Files.write(targetPath, bytes);

                Set<UUID> waitingUuids = PENDING_ENTITIES.remove(lowerHash);
                if (waitingUuids != null && !waitingUuids.isEmpty()) {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level == null) return;

                        for (UUID uuid : waitingUuids) {
                            Player p = mc.level.getPlayerByUUID(uuid);
                            // 实体仍然存活在客户端世界内，立即绑定贴图
                            if (p != null) {
                                PlayerCustomizationManager.setIrcPlayerCustomization(
                                        p.getGameProfile().name(),
                                        "cache/" + cacheFileName,
                                        1.0f
                                );
                            }
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    public static void clearAll() {
        UUID_TO_CAPE.clear();
        NAME_TO_CAPE.clear();
        DOWNLOADING_HASHES.clear();
        PENDING_ENTITIES.clear();
    }

    private static String resolveCacheFileName(String hash, String originalName) {
        StringBuilder suffix = new StringBuilder();

        int frames = PlayerCustomizationManager.getCapeFrameCount(originalName);
        if (frames >= 2) suffix.append('@').append(frames);

        int presetFps = PlayerCustomizationManager.getCapePresetFps(originalName);
        if (presetFps > 0) suffix.append('#').append(presetFps);

        String extension = originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith(".gif") ? ".gif" : ".png";
        return hash + suffix + extension;
    }
}