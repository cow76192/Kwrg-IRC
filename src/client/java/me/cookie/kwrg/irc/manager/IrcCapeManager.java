package me.cookie.kwrg.irc.manager;

import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IrcCapeManager {

    private static final Map<String, Set<String>> PENDING_DOWNLOADS = new ConcurrentHashMap<>();
    private static final Map<String, String> PENDING_CAPE_NAMES = new ConcurrentHashMap<>();

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

    public static void handleRemoteCapeSync(String username, String hash, String fileName) {
        if (hash == null || hash.isBlank()) {
            PlayerCustomizationManager.setIrcPlayerCustomization(username, null, 1.0f);
            return;
        }

        String cacheFileName = resolveCacheFileName(hash, fileName);
        Path cachedFile = getCacheDir().resolve(cacheFileName);

        if (Files.exists(cachedFile)) {
            PlayerCustomizationManager.setIrcPlayerCustomization(username, "cache/" + cacheFileName, 1.0f);
        } else {
            if (fileName != null && !fileName.isBlank()) PENDING_CAPE_NAMES.put(username, fileName);
            PENDING_DOWNLOADS.computeIfAbsent(hash, k -> ConcurrentHashMap.newKeySet()).add(username);
            IrcClient.sendCapeDownload(hash);
        }
    }

    public static void handleIncomingCapeData(String hash, String fileName, String base64Data) {
        if (hash == null || base64Data == null || base64Data.isBlank()) return;

        try {
            byte[] bytes = Base64.getDecoder().decode(base64Data);
            Set<String> waitingPlayers = PENDING_DOWNLOADS.remove(hash);

            Map<String, Set<String>> byCacheName = new LinkedHashMap<>();
            if (waitingPlayers != null) {
                for (String username : waitingPlayers) {
                    String announced = PENDING_CAPE_NAMES.remove(username);
                    String cacheFileName = resolveCacheFileName(hash, announced != null ? announced : fileName);
                    byCacheName.computeIfAbsent(cacheFileName, k -> new LinkedHashSet<>()).add(username);
                }
            }
            if (byCacheName.isEmpty()) {
                byCacheName.put(resolveCacheFileName(hash, fileName), new LinkedHashSet<>());
            }

            for (Map.Entry<String, Set<String>> entry : byCacheName.entrySet()) {
                Files.write(getCacheDir().resolve(entry.getKey()), bytes);
                for (String username : entry.getValue()) {
                    PlayerCustomizationManager.setIrcPlayerCustomization(username, "cache/" + entry.getKey(), 1.0f);
                }
            }
        } catch (Exception ignored) {}
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