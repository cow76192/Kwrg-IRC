package me.cookie.kwrg.irc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ConfigMigrator {

    private static final Set<String> KNOWN_CONFIG_KEYS = new HashSet<>();
    private static final Gson BACKUP_GSON = new GsonBuilder().setPrettyPrinting().create();

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
        if (jsonObj.has("onlyTab")) {
            JsonElement onlyTabVal = jsonObj.get("onlyTab");
            if (!jsonObj.has("onlyApplyToTab")) {
                jsonObj.add("onlyApplyToTab", onlyTabVal);
                notifyMigration("onlyTab", "onlyApplyToTab", onlyTabVal);
            }
            jsonObj.remove("onlyTab");
        }

        // 2. 远程新增：chatSuffixText -> chatSuffix
        if (jsonObj.has("chatSuffixText")) {
            JsonElement chatSuffixVal = jsonObj.get("chatSuffixText");
            if (!jsonObj.has("chatSuffix")) {
                jsonObj.add("chatSuffix", chatSuffixVal);
                notifyMigration("chatSuffixText", "chatSuffix", chatSuffixVal);
            }
            jsonObj.remove("chatSuffixText");
        }

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
            Path backupFile = backupDir.resolve("config-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".json");
            Files.writeString(backupFile, BACKUP_GSON.toJson(originalConfig), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            ChatNotifier.send("§e未知配置字段: §f" + unknownFields + "\n§e原始配置已备份至: §f" + backupFile.toAbsolutePath(),
                    NotificationType.WARN);
        } catch (IOException | SecurityException e) {
            ChatNotifier.send("§c未知配置字段: §f" + unknownFields + "\n§c原始配置备份失败: §f" + e.getMessage(),
                    NotificationType.ERR);
        }
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

        JsonObject island = new JsonObject();
        boolean hasLegacy = false;
        for (String[] pair : legacy) {
            if (jsonObj.has(pair[0])) {
                JsonElement val = jsonObj.get(pair[0]);
                island.add(pair[1], val);
                hasLegacy = true;
                notifyMigration(pair[0], "hudElements.dynamicIsland." + pair[1], val);
                jsonObj.remove(pair[0]);
            }
        }
        if (!hasLegacy) return;

        JsonObject hud = hudObject(jsonObj);
        if (!hud.has("dynamicIsland")) {
            hud.add("dynamicIsland", island);
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
