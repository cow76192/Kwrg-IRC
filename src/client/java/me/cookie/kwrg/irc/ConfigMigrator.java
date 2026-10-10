package me.cookie.kwrg.irc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ConfigMigrator {

    private ConfigMigrator() {
    }

    public static void migrate(JsonObject jsonObj) {
        if (jsonObj == null) return;

        if (jsonObj.has("onlyTab") && !jsonObj.has("onlyApplyToTab")) {
            jsonObj.add("onlyApplyToTab", jsonObj.get("onlyTab"));
        }
        jsonObj.remove("onlyTab");
        jsonObj.remove("autoTrackPlayer");
        jsonObj.remove("scoreboardExternalInterop");
        jsonObj.remove("dynamicIslandShadow");

        migrateIslandPositionFields(jsonObj);
        migrateIslandToggle(jsonObj);
        migrateTabOpacity(jsonObj);
        migrateElementAppearance(jsonObj);
    }

    private static void migrateIslandToggle(JsonObject jsonObj) {
        boolean hasToggle = jsonObj.has("dynamicIsland");
        boolean hasTabOnly = jsonObj.has("onlyRenderTab");
        if (!hasToggle && !hasTabOnly) return;

        boolean islandOn = hasToggle && isLegacyTrue(jsonObj, "dynamicIsland");
        boolean tabOnly = hasTabOnly && isLegacyTrue(jsonObj, "onlyRenderTab");

        jsonObj.remove("dynamicIsland");
        jsonObj.remove("onlyRenderTab");

        writeElementPrimitive(jsonObj, "dynamicIsland", "enabled", islandOn && !tabOnly);

        if (!jsonObj.has("dynamicTab")) {
            jsonObj.addProperty("dynamicTab", islandOn || tabOnly);
        }
    }

    private static void migrateTabOpacity(JsonObject jsonObj) {
        if (!jsonObj.has("onlyApplyToTab")) return;

        boolean applyToTabOnly = isLegacyTrue(jsonObj, "onlyApplyToTab");
        jsonObj.remove("onlyApplyToTab");

        if (jsonObj.has("tabOpacity")) return;

        float legacyOpacity = jsonObj.has("islandOpacity") && jsonObj.get("islandOpacity").isJsonPrimitive()
                ? jsonObj.get("islandOpacity").getAsFloat()
                : 0.2f;

        jsonObj.addProperty("tabOpacity", legacyOpacity);
        if (applyToTabOnly) {
            writeElementPrimitive(jsonObj, "dynamicIsland", "opacity", 1.0f);
        }
    }

    private static void migrateElementAppearance(JsonObject jsonObj) {
        boolean legacyShadow = isLegacyTrue(jsonObj, "isShadowEnabled");

        moveIntoElement(jsonObj, "islandOpacity", "dynamicIsland", "opacity");
        moveIntoElement(jsonObj, "isShadowEnabled", "dynamicIsland", "shadow");
        moveIntoElement(jsonObj, "scoreboardOpacity", "scoreboard", "opacity");
        moveIntoElement(jsonObj, "scoreboardPadding", "scoreboard", "padding");

        if (legacyShadow) {
            writeElementPrimitive(jsonObj, "scoreboard", "shadow", true);
        }
    }

    private static void moveIntoElement(JsonObject jsonObj, String legacyKey, String elementId, String stateKey) {
        if (!jsonObj.has(legacyKey)) return;

        JsonElement legacy = jsonObj.get(legacyKey);
        jsonObj.remove(legacyKey);

        if (legacy == null || !legacy.isJsonPrimitive()) return;
        writeElementPrimitive(jsonObj, elementId, stateKey, legacy);
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
        writeElementPrimitive(jsonObj, elementId, stateKey, new com.google.gson.JsonPrimitive(value));
    }

    private static void writeElementPrimitive(JsonObject jsonObj, String elementId, String stateKey, Number value) {
        writeElementPrimitive(jsonObj, elementId, stateKey, new com.google.gson.JsonPrimitive(value));
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
                island.add(pair[1], jsonObj.get(pair[0]));
                hasLegacy = true;
            }
            jsonObj.remove(pair[0]);
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
}
