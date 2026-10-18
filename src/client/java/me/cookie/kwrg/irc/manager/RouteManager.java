package me.cookie.kwrg.irc.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.util.ChatNotifier;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class RouteManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int ROUTE_SEED_VERSION = 230;
    private static final String DEFAULT_ROUTE = "kuudra_pearl_waypoints.json";
    private static final GizmoStyle FILL_STYLE = GizmoStyle.fill(0x8000E5FF);
    private static final int TEXT_COLOR = 0xFF00E5FF;
    private static final int LINE_COLOR = 0xFF00E5FF;

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static Path currentFile = null;

    private RouteManager() {
    }

    public static void init() {
        migrateLegacyDir();
        seedDefaultRoute();
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> render());
    }

    private static void seedDefaultRoute() {
        IrcConfig config = IrcConfig.get();
        if (config.routeSeedVersion >= ROUTE_SEED_VERSION) return;

        Path dir = getDir();
        Path target = dir.resolve(DEFAULT_ROUTE);
        try {
            Files.createDirectories(dir);
            if (!Files.exists(target)) {
                try (InputStream input = RouteManager.class.getResourceAsStream("/assets/kwrg/routes/" + DEFAULT_ROUTE)) {
                    if (input == null) return;
                    Files.copy(input, target);
                }
            }
            config.routeSeedVersion = ROUTE_SEED_VERSION;
            IrcConfig.save();
        } catch (IOException ignored) {
        }
    }

    public static Path getDir() {
        return IrcConfig.getKwrgDir().resolve("routes");
    }

    private static void migrateLegacyDir() {
        Path legacy = IrcConfig.getKwrgDir().resolve("coords");
        if (!Files.isDirectory(legacy)) return;

        Path target = getDir();
        try {
            Files.createDirectories(target);
            List<Path> legacyFiles;
            try (Stream<Path> stream = Files.list(legacy)) {
                legacyFiles = stream.toList();
            }
            for (Path path : legacyFiles) {
                if (!Files.isRegularFile(path)) continue;
                Path destination = target.resolve(path.getFileName().toString());
                if (!Files.exists(destination)) Files.move(path, destination);
            }
            try (Stream<Path> stream = Files.list(legacy)) {
                if (stream.findAny().isEmpty()) Files.deleteIfExists(legacy);
            }
        } catch (IOException ignored) {
        }
    }

    public static List<Path> listFiles() {
        try {
            Path dir = getDir();
            Files.createDirectories(dir);
            try (Stream<Path> stream = Files.list(dir)) {
                Map<String, Path> selected = new LinkedHashMap<>();
                for (Path path : stream.filter(RouteManager::isRouteFile).toList()) {
                    String base = baseName(path).toLowerCase(Locale.ROOT);
                    Path existing = selected.get(base);
                    if (existing == null || (isJson(path) && !isJson(existing))) {
                        selected.put(base, path);
                    }
                }
                return selected.values().stream()
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    public static String currentSourceName() {
        return currentFile == null ? "" : baseName(currentFile);
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Path currentFile() {
        return currentFile;
    }

    public static String currentFileName() {
        return currentFile == null ? "" : currentFile.getFileName().toString();
    }

    public static boolean importFile(Path path) {
        String text = readFile(path);
        if (text == null) {
            ChatNotifier.send("§c[Route] 读取失败 | " + fileName(path), NotificationType.ERR);
            return false;
        }

        ENTRIES.clear();
        ENTRIES.addAll(isJson(path) ? parseJson(text) : parseTxt(text));
        currentFile = path;
        IrcConfig config = IrcConfig.get();
        config.routeVisible = true;
        IrcConfig.save();
        ChatNotifier.send("§a[Route] 已导入 | " + fileName(path) + " (" + ENTRIES.size() + " 条)", NotificationType.SUCCESS);
        return true;
    }

    public static void clear() {
        ENTRIES.clear();
        currentFile = null;
    }

    public static boolean copyFile(Path path) {
        String text = readFile(path);
        if (text == null) return false;
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        ChatNotifier.send("§a[Route] 已复制 | " + fileName(path), NotificationType.SUCCESS);
        return true;
    }

    public static boolean deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
            if (path.equals(currentFile)) {
                ENTRIES.clear();
                currentFile = null;
            }
            ChatNotifier.send("§e[Route] 已删除文件 | " + fileName(path), NotificationType.INFO);
            return true;
        } catch (IOException e) {
            ChatNotifier.send("§c[Route] 删除失败 | " + fileName(path), NotificationType.ERR);
            return false;
        }
    }

    public static boolean saveCurrent() {
        if (currentFile == null) return false;
        try {
            Files.createDirectories(getDir());
            Files.writeString(currentFile, isJson(currentFile) ? formatJson() : formatTxt());
            return true;
        } catch (IOException e) {
            ChatNotifier.send("§c[Route] 保存失败 | " + currentFileName(), NotificationType.ERR);
            return false;
        }
    }

    public static int add(String name, String x, String y, String z, int position) {
        if (currentFile == null) {
            ChatNotifier.send("§c[Route] 尚未导入路线", NotificationType.ERR);
            return -1;
        }
        int index = Math.max(1, Math.min(position, ENTRIES.size() + 1)) - 1;
        ENTRIES.add(index, new Entry(name, x, y, z));
        saveCurrent();
        return index;
    }

    public static int addEmpty(int position) {
        return add("", "", "", "", position);
    }

    public static boolean update(int index, String name, String x, String y, String z) {
        if (index < 0 || index >= ENTRIES.size()) return false;
        Entry entry = ENTRIES.get(index);
        entry.name = name != null ? name : "";
        entry.x = x != null ? x : "";
        entry.y = y != null ? y : "";
        entry.z = z != null ? z : "";
        saveCurrent();
        return true;
    }

    public static boolean deleteAt(int index) {
        if (index < 0 || index >= ENTRIES.size()) return false;
        ENTRIES.remove(index);
        saveCurrent();
        return true;
    }

    public static boolean deleteByName(String name) {
        if (name == null) return false;
        String clean = name.trim();
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (entry.name != null && entry.name.equalsIgnoreCase(clean)) {
                ENTRIES.remove(i);
                saveCurrent();
                return true;
            }
        }
        return false;
    }

    public static boolean move(int from, int to) {
        if (from < 0 || from >= ENTRIES.size()) return false;
        int target = Math.max(0, Math.min(to, ENTRIES.size() - 1));
        if (from == target) return false;
        Entry entry = ENTRIES.remove(from);
        ENTRIES.add(target, entry);
        saveCurrent();
        return true;
    }

    public static boolean valid(Entry entry) {
        if (entry == null) return false;
        try {
            double x = Double.parseDouble(entry.x.trim());
            double y = Double.parseDouble(entry.y.trim());
            double z = Double.parseDouble(entry.z.trim());
            return WaypointManager.isValidCoord(x, y, z);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static BlockPos blockPos(Entry entry) {
        if (!valid(entry)) return null;
        return BlockPos.containing(Double.parseDouble(entry.x.trim()), Double.parseDouble(entry.y.trim()), Double.parseDouble(entry.z.trim()));
    }

    public static String displayName(Entry entry, int index) {
        return entry == null || entry.name == null || entry.name.isBlank() ? String.valueOf(index + 1) : entry.name;
    }

    public static void showInChat() {
        if (ENTRIES.isEmpty()) {
            IrcMessageHandler.printMessage("§c[Route] 当前路线为空");
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(i + 1)
                    .append(".[").append(displayName(entry, i))
                    .append("][").append(value(entry.x))
                    .append("] [").append(value(entry.y))
                    .append("] [").append(value(entry.z))
                    .append(']');
        }
        IrcMessageHandler.printMessage("§b[Route] §f" + builder);
    }

    private static List<Entry> parseTxt(String text) {
        List<Entry> result = new ArrayList<>();
        if (text == null) return result;

        int cursor = 0;
        while (true) {
            int start = text.indexOf('[', cursor);
            if (start < 0) break;
            int end = text.indexOf(']', start + 1);
            if (end < 0) break;

            String body = text.substring(start + 1, end);
            String[] parts = body.split(",", 4);
            String name = parts.length > 0 ? parts[0].trim() : "";
            String x = parts.length > 1 ? parts[1].trim() : "";
            String y = parts.length > 2 ? parts[2].trim() : "";
            String z = parts.length > 3 ? parts[3].trim() : "";
            result.add(new Entry(name, x, y, z));
            cursor = end + 1;
        }
        return result;
    }

    private static String formatTxt() {
        StringBuilder builder = new StringBuilder("{");
        for (Entry entry : ENTRIES) {
            builder.append('[')
                    .append(field(entry.name)).append(',')
                    .append(field(entry.x)).append(',')
                    .append(field(entry.y)).append(',')
                    .append(field(entry.z)).append(']');
        }
        builder.append('}');
        return builder.toString();
    }

    private static List<Entry> parseJson(String text) {
        List<Entry> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;

        try {
            JsonElement root = JsonParser.parseString(text);
            if (root.isJsonArray()) {
                readWaypoints(root.getAsJsonArray(), result, "");
                return result;
            }
            if (!root.isJsonObject()) return result;

            JsonObject object = root.getAsJsonObject();
            if (object.has("areas") && object.get("areas").isJsonArray()) {
                for (JsonElement areaElement : object.getAsJsonArray("areas")) {
                    if (!areaElement.isJsonObject()) continue;
                    JsonObject area = areaElement.getAsJsonObject();
                    String areaName = string(area, "name");
                    if (area.has("waypoints") && area.get("waypoints").isJsonArray()) {
                        readWaypoints(area.getAsJsonArray("waypoints"), result, areaName);
                    }
                }
                return result;
            }

            if (object.has("waypoints") && object.get("waypoints").isJsonArray()) {
                readWaypoints(object.getAsJsonArray("waypoints"), result, "");
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void readWaypoints(JsonArray array, List<Entry> result, String areaName) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject waypoint = element.getAsJsonObject();

            String name = firstString(waypoint, "name", "label", "_comment");
            if (name.isEmpty() && !areaName.isEmpty()) name = areaName;

            JsonArray coords = firstArray(waypoint, "coords", "target", "pos");
            String x;
            String y;
            String z;
            if (coords != null) {
                x = coordAt(coords, 0);
                y = coordAt(coords, 1);
                z = coordAt(coords, 2);
            } else {
                x = string(waypoint, "x");
                y = string(waypoint, "y");
                z = string(waypoint, "z");
            }

            result.add(new Entry(name, x, y, z));
        }
    }

    private static String formatJson() {
        JsonObject root = new JsonObject();
        root.addProperty("name", currentSourceName());
        JsonArray waypoints = new JsonArray();
        for (Entry entry : ENTRIES) {
            JsonObject waypoint = new JsonObject();
            waypoint.addProperty("name", entry.name);
            addCoordinate(waypoint, "x", entry.x);
            addCoordinate(waypoint, "y", entry.y);
            addCoordinate(waypoint, "z", entry.z);
            waypoints.add(waypoint);
        }
        root.add("waypoints", waypoints);
        return GSON.toJson(root);
    }

    private static void addCoordinate(JsonObject object, String key, String value) {
        try {
            double coordinate = Double.parseDouble(value.trim());
            if (Double.isFinite(coordinate)) {
                object.addProperty(key, coordinate);
                return;
            }
        } catch (Exception ignored) {
        }
        object.addProperty(key, value == null ? "" : value);
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = string(object, key);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        return element.getAsString().trim();
    }

    private static JsonArray firstArray(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonArray()) return element.getAsJsonArray();
        }
        return null;
    }

    private static String coordAt(JsonArray array, int index) {
        if (index < 0 || index >= array.size()) return "";
        JsonElement element = array.get(index);
        return element == null || element.isJsonNull() ? "" : element.getAsString().trim();
    }

    private static void render() {
        if (ENTRIES.isEmpty() || !IrcConfig.get().routeVisible) return;

        boolean showLine = IrcConfig.get().routeShowLine;
        BlockPos previous = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            BlockPos pos = blockPos(entry);
            if (pos == null) {
                previous = null;
                continue;
            }

            Gizmos.cuboid(pos, FILL_STYLE).setAlwaysOnTop();
            Gizmos.billboardTextOverBlock(displayName(entry, i), pos, 0, TEXT_COLOR, 1.0f);

            if (showLine && previous != null) {
                Gizmos.line(linePoint(previous), linePoint(pos), LINE_COLOR).setAlwaysOnTop();
            }
            previous = pos;
        }
    }

    private static Vec3 linePoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
    }

    private static boolean isRouteFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".json");
    }

    private static boolean isJson(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return null;
        }
    }

    private static String fileName(Path path) {
        return path == null ? "" : path.getFileName().toString();
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? "?" : text;
    }

    private static String field(String text) {
        return text == null ? "" : text.replace(",", " ").replace("[", "").replace("]", "").trim();
    }

    public static final class Entry {
        public String name;
        public String x;
        public String y;
        public String z;

        public Entry(String name, String x, String y, String z) {
            this.name = name != null ? name : "";
            this.x = x != null ? x : "";
            this.y = y != null ? y : "";
            this.z = z != null ? z : "";
        }
    }
}
