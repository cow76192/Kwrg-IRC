package me.cookie.kwrg.irc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.gui.hud.HudElementRegistry;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ConfigMigrator {

    private static final Set<String> KNOWN_CONFIG_KEYS = new HashSet<>();
    private static final Gson BACKUP_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_BACKUPS = 10;

    static {
        for (Field field : IrcConfig.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isStatic(mods) && !Modifier.isTransient(mods)) {
                KNOWN_CONFIG_KEYS.add(field.getName());
            }
        }
    }

    private ConfigMigrator() {
    }

    public static void migrate(JsonObject jsonObj) {
        if (jsonObj == null) return;
        JsonObject originalConfig = jsonObj.deepCopy();

        // 1. onlyTab -> onlyApplyToTab
        renameField(jsonObj, "onlyTab", "onlyApplyToTab");

        // 2. 远程新增：chatSuffixText -> chatSuffix
        renameField(jsonObj, "chatSuffixText", "chatSuffix");

        // 3. 移除已废弃字段
        removeDeprecatedField(jsonObj, "autoTrackPlayer");
        removeDeprecatedField(jsonObj, "scoreboardExternalInterop");
        removeDeprecatedField(jsonObj, "dynamicIslandShadow");

        // 4. 各模块配置迁移
        migrateIslandPositionFields(jsonObj);
        migrateIslandToggle(jsonObj);
        migrateTabOpacity(jsonObj);
        migrateElementAppearance(jsonObj);
        migrateHideArmorParts(jsonObj);

        // 5. 校验未知字段
        checkUnknownFields(jsonObj, originalConfig);
    }

    private static void renameField(JsonObject jsonObj, String from, String to) {
        if (!jsonObj.has(from)) return;
        JsonElement value = jsonObj.get(from);
        if (!jsonObj.has(to)) {
            jsonObj.add(to, value);
            notifyMigration(from, to, value);
        }
        jsonObj.remove(from);
    }

    private static void removeDeprecatedField(JsonObject jsonObj, String key) {
        if (jsonObj.has(key)) {
            jsonObj.remove(key);
            notifyRemoved(key);
        }
    }

    private static void checkUnknownFields(JsonObject jsonObj, JsonObject originalConfig) {
        Set<String> unknownKeys = new LinkedHashSet<>();
        for (String key : jsonObj.keySet()) {
            if (!KNOWN_CONFIG_KEYS.contains(key)) {
                unknownKeys.add(key);
            }
        }
        if (unknownKeys.isEmpty()) return;

        String unknownFields = String.join(", ", unknownKeys);
        try {
            Path backupDir = IrcConfig.getKwrgDir().resolve("backUp");
            Files.createDirectories(backupDir);
            String backupJson = BACKUP_GSON.toJson(originalConfig);
            String backupHash = hashOf(backupJson);
            if (backupHash != null && hasBackupWithHash(backupDir, backupHash)) return;
            Path backupFile = backupDir.resolve("config-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".json");
            Files.writeString(backupFile, backupJson, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            pruneBackups(backupDir);
            notifyBackup("§e未知配置字段: §f" + unknownFields + "\n§e原始配置已备份至: §f" + backupFile.toAbsolutePath(),
                    NotificationType.WARN);
        } catch (IOException | SecurityException e) {
            notifyBackup("§c未知配置字段: §f" + unknownFields + "\n§c原始配置备份失败: §f" + e.getMessage(),
                    NotificationType.ERR);
        }
    }

    private static String hashOf(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException | RuntimeException e) {
            return null;
        }
    }

    private static boolean hasBackupWithHash(Path backupDir, String expectedHash) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "config-*.json")) {
            for (Path file : stream) {
                try {
                    if (expectedHash.equals(hashOf(Files.readString(file)))) return true;
                } catch (IOException | RuntimeException ignored) {
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return false;
    }

    private static void pruneBackups(Path backupDir) {
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "config-*.json")) {
            for (Path file : stream) {
                if (backupTimestamp(file) >= 0L) backups.add(file);
            }
        } catch (IOException | RuntimeException ignored) {
            return;
        }
        if (backups.size() <= MAX_BACKUPS) return;
        backups.sort(Comparator.comparingLong(ConfigMigrator::backupTimestamp));
        int remove = backups.size() - MAX_BACKUPS;
        for (int i = 0; i < remove; i++) {
            try {
                Files.deleteIfExists(backups.get(i));
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private static long backupTimestamp(Path file) {
        String name = file.getFileName().toString();
        if (!name.startsWith("config-") || !name.endsWith(".json")) return -1L;
        int start = "config-".length();
        int end = name.indexOf('-', start);
        if (end <= start) return -1L;
        try {
            return Long.parseLong(name.substring(start, end));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static void notifyBackup(String message, NotificationType type) {
        // 启动时弹窗可能在进入世界前过期，聊天队列会保留消息直到玩家可用。
        ChatNotifier.sendInChatScreen(message);
        ChatNotifier.send(message, type);
    }

    private static void migrateIslandToggle(JsonObject jsonObj) {
        boolean hasToggle = jsonObj.has("dynamicIsland");
        boolean hasTabOnly = jsonObj.has("onlyRenderTab");
        if (!hasToggle && !hasTabOnly) return;

        boolean islandOn = hasToggle && isLegacyTrue(jsonObj, "dynamicIsland");
        boolean tabOnly = hasTabOnly && isLegacyTrue(jsonObj, "onlyRenderTab");

        if (hasToggle) jsonObj.remove("dynamicIsland");
        if (hasTabOnly) jsonObj.remove("onlyRenderTab");

        boolean newIslandEnabled = islandOn && !tabOnly;
        writeElementPrimitive(jsonObj, "dynamicIsland", "enabled", newIslandEnabled);
        notifyMigration(hasToggle ? "dynamicIsland" : "onlyRenderTab", "hudElements.dynamicIsland.enabled", newIslandEnabled);

        if (!jsonObj.has("dynamicTab")) {
            boolean newDynamicTab = islandOn || tabOnly;
            jsonObj.addProperty("dynamicTab", newDynamicTab);
            notifyMigration(hasToggle ? "dynamicIsland" : "onlyRenderTab", "dynamicTab", newDynamicTab);
        }
    }

    private static void migrateTabOpacity(JsonObject jsonObj) {
        if (!jsonObj.has("onlyApplyToTab")) return;

        boolean applyToTabOnly = isLegacyTrue(jsonObj, "onlyApplyToTab");
        jsonObj.remove("onlyApplyToTab");

        if (!jsonObj.has("tabOpacity")) {
            float legacyOpacity = jsonObj.has("islandOpacity") && jsonObj.get("islandOpacity").isJsonPrimitive()
                    ? jsonObj.get("islandOpacity").getAsFloat()
                    : 0.2f;

            jsonObj.addProperty("tabOpacity", legacyOpacity);
            notifyMigration("onlyApplyToTab", "tabOpacity", legacyOpacity);
        }

        if (applyToTabOnly) {
            writeElementPrimitive(jsonObj, "dynamicIsland", "opacity", 1.0f);
            notifyMigration("onlyApplyToTab", "hudElements.dynamicIsland.opacity", 1.0f);
        }
    }

    private static void migrateElementAppearance(JsonObject jsonObj) {
        boolean hadLegacyShadow = jsonObj.has("isShadowEnabled");
        boolean legacyShadow = isLegacyTrue(jsonObj, "isShadowEnabled");

        moveIntoElement(jsonObj, "islandOpacity", "dynamicIsland", "opacity");
        moveIntoElement(jsonObj, "isShadowEnabled", "dynamicIsland", "shadow");
        moveIntoElement(jsonObj, "scoreboardOpacity", "scoreboard", "opacity");
        moveIntoElement(jsonObj, "scoreboardPadding", "scoreboard", "padding");

        migrateElementScale(jsonObj, "inventoryHudScale", HudElementRegistry.INVENTORY);
        migrateElementScale(jsonObj, "playerDollScale", HudElementRegistry.PLAYER_DOLL);
        if (hadLegacyShadow) {
            writeElementPrimitive(jsonObj, "scoreboard", "shadow", legacyShadow);
            notifyMigration("isShadowEnabled", "hudElements.scoreboard.shadow", legacyShadow);
        }
    }

    private static void migrateHideArmorParts(JsonObject jsonObj) {
        if (!isLegacyTrue(jsonObj, "hideArmor")) return;

        String[] partKeys = {"hideArmorHead", "hideArmorChest", "hideArmorLegs", "hideArmorFeet"};
        for (String partKey : partKeys) {
            if (jsonObj.has(partKey)) continue;
            jsonObj.addProperty(partKey, true);
            notifyMigration("hideArmor", partKey, true);
        }
    }

    private static void moveIntoElement(JsonObject jsonObj, String legacyKey, String elementId, String stateKey) {
        if (!jsonObj.has(legacyKey)) return;

        JsonElement legacy = jsonObj.get(legacyKey);
        jsonObj.remove(legacyKey);

        if (legacy == null || !legacy.isJsonPrimitive()) return;
        writeElementPrimitive(jsonObj, elementId, stateKey, legacy);
        notifyMigration(legacyKey, "hudElements." + elementId + "." + stateKey, legacy);
    }

    private static void migrateElementScale(JsonObject jsonObj, String legacyKey, String elementId) {
        JsonElement legacy = jsonObj.get(legacyKey);
        if (legacy != null && legacy.isJsonPrimitive()) {
            JsonObject hud = hudObject(jsonObj);
            if (!hud.has(elementId) || !hud.get(elementId).isJsonObject()) {
                HudElement element = HudElementRegistry.get(elementId);
                if (element != null) {
                    JsonObject defaults = BACKUP_GSON.toJsonTree(element.defaultState()).getAsJsonObject();
                    defaults.add("scale", legacy);
                    hud.add(elementId, defaults);
                    jsonObj.add("hudElements", hud);
                }
            }
        }
        moveIntoElement(jsonObj, legacyKey, elementId, "scale");
    }

    private static void writeElementPrimitive(JsonObject jsonObj, String elementId, String stateKey, JsonElement value) {
        JsonObject hud = hudObject(jsonObj);
        JsonObject element = elementObject(hud, elementId);

        if (!element.has(stateKey)) {
            element.add(stateKey, value);
        }

        hud.add(elementId, element);
        jsonObj.add("hudElements", hud);
    }

    private static void writeElementPrimitive(JsonObject jsonObj, String elementId, String stateKey, boolean value) {
        writeElementPrimitive(jsonObj, elementId, stateKey, new JsonPrimitive(value));
    }

    private static void writeElementPrimitive(JsonObject jsonObj, String elementId, String stateKey, Number value) {
        writeElementPrimitive(jsonObj, elementId, stateKey, new JsonPrimitive(value));
    }

    private static boolean isLegacyTrue(JsonObject jsonObj, String key) {
        JsonElement element = jsonObj.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static void migrateIslandPositionFields(JsonObject jsonObj) {
        String[][] legacy = {
                {"islandAnchorX", "anchorX"}, {"islandAnchorY", "anchorY"},
                {"islandOffsetX", "offsetX"}, {"islandOffsetY", "offsetY"}
        };

        JsonObject hud = hudObject(jsonObj);
        JsonObject element = elementObject(hud, "dynamicIsland");
        boolean hasLegacy = false;
        for (String[] pair : legacy) {
            if (jsonObj.has(pair[0])) {
                JsonElement val = jsonObj.get(pair[0]);
                if (!element.has(pair[1])) {
                    element.add(pair[1], val);
                }
                hasLegacy = true;
                notifyMigration(pair[0], "hudElements.dynamicIsland." + pair[1], val);
                jsonObj.remove(pair[0]);
            }
        }
        if (!hasLegacy) return;

        if (!hud.has("dynamicIsland")) {
            hud.add("dynamicIsland", element);
        }
        jsonObj.add("hudElements", hud);
    }

    private static JsonObject hudObject(JsonObject jsonObj) {
        return jsonObj.has("hudElements") && jsonObj.get("hudElements").isJsonObject()
                ? jsonObj.getAsJsonObject("hudElements")
                : new JsonObject();
    }

    private static JsonObject elementObject(JsonObject hud, String elementId) {
        return hud.has(elementId) && hud.get(elementId).isJsonObject()
                ? hud.getAsJsonObject(elementId)
                : new JsonObject();
    }


    private static void notifyMigration(String oldKey, String newTarget, Object newValue) {
        Component message = Component.literal("[INFO] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("配置迁移: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(oldKey).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(newTarget + " = " + newValue).withStyle(ChatFormatting.GREEN));
        ChatNotifier.sendInChatScreen(message);
    }

    private static void notifyRemoved(String oldKey) {
        Component message = Component.literal("[INFO] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("已移除废弃配置字段: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(oldKey).withStyle(ChatFormatting.YELLOW));
        ChatNotifier.sendInChatScreen(message);
    }

}
