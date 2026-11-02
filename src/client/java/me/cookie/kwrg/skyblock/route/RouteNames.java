package me.cookie.kwrg.skyblock.route;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.Locale;

final class RouteNames {

    private RouteNames() {
    }

    static String validateRouteName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.toLowerCase(Locale.ROOT).endsWith(".json")) clean = clean.substring(0, clean.length() - 5).strip();
        if (clean.isEmpty() || clean.length() > 64) throw new IllegalArgumentException("名称须为 1–64 个字符");
        if (clean.equals(".") || clean.equals("..") || clean.endsWith(".")
                || clean.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0)) {
            throw new IllegalArgumentException("名称不能包含路径或文件名特殊字符");
        }
        String stem = clean.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) throw new IllegalArgumentException("请使用其他路线名称");
        return clean;
    }

    static void addCoordinate(JsonObject object, String key, String value) {
        try {
            double coordinate = Double.parseDouble(value.trim());
            if (Double.isFinite(coordinate)) {
                object.addProperty(key, coordinate);
                return;
            }
        } catch (RuntimeException ignored) {
        }
        object.addProperty(key, value == null ? "" : value);
    }

    static String coordAt(JsonArray array, int index) {
        if (index < 0 || index >= array.size()) return "";
        JsonElement element = array.get(index);
        return element == null || element.isJsonNull() ? "" : element.getAsString().trim();
    }

    static boolean isRouteFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".json");
    }

    static boolean isJson(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String fileName(Path path) {
        return path == null ? "" : path.getFileName().toString();
    }

    static String value(String text) {
        return text == null || text.isBlank() ? "?" : text;
    }
}
