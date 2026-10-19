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
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public final class RouteManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_ROUTE = "kuudra_pearl_waypoints.json";
    private static final long MAX_ROUTE_FILE_BYTES = 2L * 1024L * 1024L;
    private static final GizmoStyle BLOCK_STYLE = GizmoStyle.strokeAndFill(0xFF00E5FF, 1.0f, 0x8000E5FF);
    private static final int TEXT_COLOR = 0xFF00E5FF;
    private static final int LINE_COLOR = 0xFF00E5FF;

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static Path currentFile = null;
    private static boolean visible = true;
    private static boolean showLine = false;

    private RouteManager() {
    }

    public static void init() {
        migrateLegacyDir();
        seedDefaultRoute();
        reloadFromConfig();
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> render());
    }

    public static void reloadFromConfig() {
        String savedName = IrcConfig.get().currentRouteFileName;
        if (savedName != null && !savedName.isBlank()) {
            Path savedFile = getDir().resolve(savedName).normalize();
            if (savedFile.getParent().equals(getDir().normalize()) && Files.isRegularFile(savedFile)) {
                importFile(savedFile);
                return;
            }
        }
        ENTRIES.clear();
        currentFile = null;
        visible = true;
        showLine = false;
        if (savedName != null && !savedName.isBlank()) {
            IrcConfig.get().currentRouteFileName = "";
            IrcConfig.save();
        }
    }

    private static void seedDefaultRoute() {
        Path dir = getDir();
        if (Files.exists(dir)) return;
        Path target = dir.resolve(DEFAULT_ROUTE);
        try {
            Files.createDirectories(dir);
            if (!Files.exists(target)) {
                try (InputStream input = RouteManager.class.getResourceAsStream("/assets/kwrg/routes/" + DEFAULT_ROUTE)) {
                    if (input == null) return;
                    Files.copy(input, target);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[Route] 默认路线释放失败", e);
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
        } catch (IOException e) {
            LOGGER.warn("[Route] 旧路线目录迁移失败", e);
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

    private static String validateRouteName(String name) {
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

    private static void ensureRouteNameAvailable(Path directory, String name, Path original) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path file : stream.filter(RouteManager::isRouteFile).toList()) {
                if ((original == null || !file.equals(original)) && baseName(file).equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("已存在同名路线，请换一个名称");
                }
            }
        }
    }

    public static Path createRoute(String name) throws IOException {
        return createRoute(getDir(), name);
    }

    private static Path createRoute(Path directory, String name) throws IOException {
        String clean = validateRouteName(name);
        Files.createDirectories(directory);
        ensureRouteNameAvailable(directory, clean, null);
        Path target = directory.resolve(clean + ".json");
        JsonObject root = new JsonObject();
        root.addProperty("name", clean);
        root.addProperty("visible", true);
        root.addProperty("showLine", false);
        root.add("waypoints", new JsonArray());
        Files.writeString(target, GSON.toJson(root), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return target;
    }

    public static Path renameRoute(Path file, String name) throws IOException {
        String clean = validateRouteName(name);
        Path directory = file.getParent();
        ensureRouteNameAvailable(directory, clean, file);
        Path target = directory.resolve(clean + (isJson(file) ? ".json" : ".txt"));
        String content = readFile(file);
        if (content == null) throw new IOException("无法读取路线文件");
        if (isJson(file)) {
            JsonElement parsed = JsonParser.parseString(content);
            JsonObject root;
            if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
            else if (parsed.isJsonArray()) {
                root = new JsonObject();
                root.add("waypoints", parsed);
            } else throw new IllegalArgumentException("路线 JSON 格式无效");
            root.addProperty("name", clean);
            content = GSON.toJson(root);
        }
        if (file.equals(target)) {
            Files.writeString(file, content);
        } else {
            Files.writeString(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.delete(file);
            } catch (IOException e) {
                Files.deleteIfExists(target);
                throw e;
            }
        }
        if (file.equals(currentFile)) {
            currentFile = target;
            IrcConfig.get().currentRouteFileName = currentFileName();
            IrcConfig.save();
        }
        return target;
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

    public static boolean isVisible() { return visible; }
    public static boolean isShowLine() { return showLine; }

    public record FileOptions(boolean visible, boolean showLine) {}

    public static FileOptions fileOptions(Path path) {
        if (path.equals(currentFile)) return new FileOptions(visible, showLine);
        String text = readFile(path);
        if (text != null && isJson(path)) {
            try {
                JsonElement parsed = JsonParser.parseString(text);
                if (parsed.isJsonObject()) {
                    JsonObject root = parsed.getAsJsonObject();
                    return new FileOptions(!root.has("visible") || root.get("visible").getAsBoolean(),
                            root.has("showLine") && root.get("showLine").getAsBoolean());
                }
            } catch (RuntimeException e) {
                LOGGER.warn("[Route] 无法读取路线设置: {}", path, e);
            }
        }
        return new FileOptions(true, false);
    }

    /** Changes a file's options without switching the route being edited or rendered. */
    public static boolean setFileOptions(Path path, boolean fileVisible, boolean fileShowLine) {
        if (path.equals(currentFile)) {
            visible = fileVisible;
            showLine = fileShowLine;
            return saveCurrent();
        }
        String text = readFile(path);
        if (text == null) return false;
        try {
            JsonObject root;
            Path target = path;
            if (isJson(path)) {
                JsonElement parsed = JsonParser.parseString(text);
                if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
                else if (parsed.isJsonArray()) {
                    root = new JsonObject();
                    root.add("waypoints", parsed);
                } else return false;
            } else {
                root = new JsonObject();
                root.addProperty("name", baseName(path));
                root.add("waypoints", entriesJson(parseTxt(text)));
                target = getDir().resolve(baseName(path) + ".json");
            }
            root.addProperty("visible", fileVisible);
            root.addProperty("showLine", fileShowLine);
            Files.writeString(target, GSON.toJson(root));
            return true;
        } catch (IOException | RuntimeException e) {
            ChatNotifier.send("§c[Route] 设置保存失败 | " + fileName(path), NotificationType.ERR);
            LOGGER.warn("[Route] 设置保存失败: {}", path, e);
            return false;
        }
    }

    public static void updateCoordinate(int index, int axis, String value) {
        if (index < 0 || index >= ENTRIES.size()) return;
        Entry entry = ENTRIES.get(index);
        switch (axis) {
            case 0 -> entry.x = value;
            case 1 -> entry.y = value;
            case 2 -> entry.z = value;
            default -> throw new IllegalArgumentException("axis");
        }
    }

    public static void updateName(int index, String value) {
        if (index < 0 || index >= ENTRIES.size()) return;
        ENTRIES.get(index).name = value;
    }

    public static void setEntryVisible(int index, boolean value) {
        if (index < 0 || index >= ENTRIES.size()) return;
        ENTRIES.get(index).visible = value;
        saveCurrent();
    }

    public static void setVisible(boolean value) {
        if (currentFile == null) return;
        visible = value;
        saveCurrent();
    }

    public static void setShowLine(boolean value) {
        if (currentFile == null) return;
        showLine = value;
        saveCurrent();
    }

    public static boolean importFile(Path path) {
        String text = readFile(path);
        if (text == null) {
            ChatNotifier.send("§c[Route] 读取失败 | " + fileName(path), NotificationType.ERR);
            return false;
        }

        boolean json = isJson(path);
        ENTRIES.clear();
        ENTRIES.addAll(json ? parseJson(text) : parseTxt(text));
        visible = true;
        showLine = false;
        if (json) readSettings(text);
        currentFile = json ? path : getDir().resolve(baseName(path) + ".json");
        if (!json && !saveCurrent()) return false;
        IrcConfig.get().currentRouteFileName = currentFileName();
        IrcConfig.save();
        ChatNotifier.send("§a[Route] 已导入 | " + fileName(path) + " (" + ENTRIES.size() + " 条)", NotificationType.SUCCESS);
        return true;
    }

    public static void clear() {
        ENTRIES.clear();
        currentFile = null;
        IrcConfig.get().currentRouteFileName = "";
        IrcConfig.save();
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
                clear();
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
            Files.writeString(currentFile, formatJson());
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

    private static void readSettings(String text) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("visible")) visible = root.get("visible").getAsBoolean();
            if (root.has("showLine")) showLine = root.get("showLine").getAsBoolean();
        } catch (Exception ignored) {
            visible = true;
            showLine = false;
        }
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

            Entry entry = new Entry(name, x, y, z);
            entry.visible = !waypoint.has("visible") || waypoint.get("visible").getAsBoolean();
            result.add(entry);
        }
    }

    private static String formatJson() {
        JsonObject root = new JsonObject();
        root.addProperty("name", currentSourceName());
        root.addProperty("visible", visible);
        root.addProperty("showLine", showLine);
        root.add("waypoints", entriesJson(ENTRIES));
        return GSON.toJson(root);
    }

    private static JsonArray entriesJson(List<Entry> entries) {
        JsonArray waypoints = new JsonArray();
        for (Entry entry : entries) {
            JsonObject waypoint = new JsonObject();
            waypoint.addProperty("name", entry.name);
            waypoint.addProperty("visible", entry.visible);
            addCoordinate(waypoint, "x", entry.x);
            addCoordinate(waypoint, "y", entry.y);
            addCoordinate(waypoint, "z", entry.z);
            waypoints.add(waypoint);
        }
        return waypoints;
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
        if (!visible || ENTRIES.isEmpty()) return;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();
        var up = camera.upVector();
        Vec3 cameraUp = new Vec3(up.x(), up.y(), up.z());
        BlockPos previous = null;
        int index = 0;
        for (Entry entry : ENTRIES) {
            BlockPos pos = entry.visible ? blockPos(entry) : null;
            if (pos == null) {
                previous = null;
                index++;
                continue;
            }

            Gizmos.cuboid(pos, BLOCK_STYLE).setAlwaysOnTop();
            Gizmos.billboardTextOverBlock(displayName(entry, index), pos, 0, TEXT_COLOR, 1.0f);

            if (showLine) {
                Vec3 node = visibleLinePoint(pos, cameraPosition, cameraUp);
                Vec3 blockTop = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
                Gizmos.line(blockTop, node, LINE_COLOR).setAlwaysOnTop();
                if (previous != null) Gizmos.line(visibleLinePoint(previous, cameraPosition, cameraUp), blockTop, LINE_COLOR).setAlwaysOnTop();
            }
            previous = pos;
            index++;
        }
    }

    private static Vec3 linePoint(BlockPos pos) {
        // A player standing on this block has their feet at its top face.
        return Vec3.atBottomCenterOf(pos).add(0, 1 + (double) Avatar.DEFAULT_EYE_HEIGHT, 0);
    }

    private static Vec3 visibleLinePoint(BlockPos pos, Vec3 cameraPosition, Vec3 cameraUp) {
        Vec3 node = linePoint(pos);
        double proximity = Math.max(0, 1 - node.distanceTo(cameraPosition) / 0.75);
        double offset = 0.18 * proximity * proximity * (3 - 2 * proximity);
        // A line starting at the camera collapses to a screen point. Move it down in view space nearby.
        return node.subtract(cameraUp.scale(offset));
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
            if (Files.size(path) > MAX_ROUTE_FILE_BYTES) return null;
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
        public boolean visible = true;

        public Entry(String name, String x, String y, String z) {
            this.name = name != null ? name : "";
            this.x = x != null ? x : "";
            this.y = y != null ? y : "";
            this.z = z != null ? z : "";
        }
    }
}
