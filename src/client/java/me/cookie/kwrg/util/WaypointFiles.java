package me.cookie.kwrg.util;

import me.cookie.kwrg.irc.manager.WaypointManager;
import me.cookie.kwrg.irc.manager.RouteManager;

import com.google.gson.*;
import me.cookie.kwrg.irc.IrcConfig;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Independent enabled collections; editing one never unloads the others. */
public final class WaypointFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Path, Collection> FILES = new LinkedHashMap<>();
    private static Path current;
    private static Path directory, routeDirectory;
    private static boolean initialized;
    private WaypointFiles() {}

    public static Path getDir() { return directory == null ? IrcConfig.getKwrgDir().resolve("wayPoints") : directory; }

    public static Path defaultFile() { return getDir().resolve("default.json"); }

    public static Path currentFile() {
        ensure();
        return current;
    }

    public static boolean isDefault(Path path) { return defaultFile().equals(path); }

    public static boolean external(Path path) { return !path.toAbsolutePath().normalize().getParent().equals(getDir().toAbsolutePath().normalize()); }

    public static boolean initialize(Map<String, IrcConfig.WaypointEntry> legacy) {
        return initialize(IrcConfig.getKwrgDir().resolve("wayPoints"), RouteManager.getDir(), legacy);
    }

    static boolean initialize(Path pointDirectory, Path routeDirectory, Map<String, IrcConfig.WaypointEntry> legacy) {
        directory = pointDirectory;
        WaypointFiles.routeDirectory = routeDirectory;
        FILES.clear();
        initialized = true;
        try {
            Files.createDirectories(getDir());
            if (!Files.exists(defaultFile())) {
                Collection defaults = new Collection("默认点位");
                write(defaultFile(), defaults);
            }
            refresh();
            Collection defaults = FILES.get(defaultFile());
            if (defaults == null) {
                throw new IOException("默认点位文件无法读取");
            }
            String profile = IrcConfig.getCurrentProfileName();
            if (defaults.migratedProfiles.add(profile)) {
                legacy.forEach((id, saved) -> {
                    PointEntry entry = fromSaved(id, saved);
                    boolean same = defaults.points.stream().anyMatch(existing ->
                            existing.name.equals(entry.name)
                                    && existing.x.equals(entry.x)
                                    && existing.y.equals(entry.y)
                                    && existing.z.equals(entry.z)
                                    && existing.visible == entry.visible
                    );
                    if (!same) {
                        if (defaults.points.stream().anyMatch(existing -> existing.id.equals(id))) {
                            entry.id = UUID.randomUUID().toString();
                        }

                        defaults.points.add(entry);
                    }
                });
                write(defaultFile(), defaults);
            }
            current = defaultFile();
            syncDefault();
            return true;
        } catch (IOException | RuntimeException e) {
            LoggerFactory.getLogger(WaypointFiles.class).warn("无法初始化点位存储，保留配置中的旧点位并使用内存副本", e);
            if (!FILES.containsKey(defaultFile())) {
                Collection fallback = new Collection("默认点位");
                legacy.forEach((id, saved) -> fallback.points.add(fromSaved(id, saved)));
                // Block writes over an unreadable/corrupt default file until explicitly recovered.
                fallback.savedPath = defaultFile();
                FILES.put(defaultFile(), fallback);
            }
            current = defaultFile();
            syncDefault();
            return false;
        }
    }

    private static void ensure() {
        if (!initialized) {
            initialize(Map.of());
        }
    }

    public static void refresh() {
        ensure();
        try (var stream = Files.list(getDir())) {
            List<Path> paths = stream.filter(WaypointFiles::isJson).sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
            Set<Path> present = new HashSet<>(paths);
            FILES.entrySet().removeIf(item -> !isDefault(item.getKey()) && !present.contains(item.getKey()) && !item.getValue().hasUnsavedChanges());
            for (Path path : paths) {
                try {
                    Collection cached = FILES.get(path);
                    var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
                    if (cached != null && cached.sameFile(attributes)) {
                        continue;
                    }
                    // Keep the objects referenced by live input boxes until their edits are saved.
                    if (cached != null && cached.hasUnsavedChanges()) {
                        continue;
                    }
                    String text = readText(path);
                    if (cached != null && text.equals(cached.diskContent)) {
                        cached.rememberAttributes(attributes);
                        continue;
                    }

                    FILES.put(path, loaded(path, text));
                    if (isDefault(path)) {
                        syncDefault();
                    }
                }
                catch (Exception e) {
                    LoggerFactory.getLogger(WaypointFiles.class).warn("无法加载点位文件 {}", path, e);
                }
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(WaypointFiles.class).warn("无法读取点位目录", e);
        }
    }

    public static List<Path> listFiles() {
        refresh();
        List<Path> paths = new ArrayList<>();
        if (FILES.containsKey(defaultFile())) {
            paths.add(defaultFile());
        }
        FILES.keySet().stream().filter(path -> !isDefault(path)).sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).forEach(paths::add);
        try (var routes = Files.exists(routeDirectory) ? Files.list(routeDirectory) : java.util.stream.Stream.<Path>empty()) {
            routes.filter(WaypointFiles::isJson).sorted().forEach(paths::add);
        } catch (IOException e) {
            LoggerFactory.getLogger(WaypointFiles.class).warn("无法读取待导入路线", e);
        }
        return paths;
    }

    public static Collection collection(Path path) {
        ensure();
        return FILES.get(path);
    }

    public static List<Collection> collections() {
        ensure();
        return List.copyOf(FILES.values());
    }

    public static boolean select(Path path) {
        ensure();
        if (external(path) || !FILES.containsKey(path)) {
            return false;
        }
        current = path;
        return true;
    }

    public static List<PointEntry> entries() {
        ensure();
        Collection file = FILES.get(current);
        if (file == null) {
            return List.of();
        }
        if (!isDefault(current)) {
            return file.points;
        }
        List<PointEntry> combined = new ArrayList<>(file.points);
        combined.addAll(WaypointManager.temporaryEntries());
        return combined;
    }

    public static boolean saveCurrent() { return save(currentFile()); }

    public static void discardCurrentEdits() {
        ensure();
        try {
            FILES.put(current, read(current));
        } catch (IOException | RuntimeException e) {
            LoggerFactory.getLogger(WaypointFiles.class).warn("放弃编辑后无法重载点位文件，保留磁盘内容: {}", current, e);
            Collection empty = new Collection(isDefault(current) ? "默认点位" : stem(current));
            empty.savedPath = current;
            FILES.put(current, empty);
        }
        if (isDefault(current)) {
            syncDefault();
        }
    }

    public static boolean save(Path path) {
        Collection file = collection(path);
        if (file == null) {
            return false;
        }
        try {
            write(path, file);
            if (isDefault(path)) {
                syncDefault();
            }
            return true;
        } catch (IOException e) {
            LoggerFactory.getLogger(WaypointFiles.class).warn("点位保存失败 {}", path, e);
            return false;
        }
    }

    public static boolean saveDefault(Map<String, IrcConfig.WaypointEntry> saved) {
        ensure();
        Collection defaults = FILES.get(defaultFile());
        // Incomplete rows cannot be represented by the legacy numeric editor; preserve them.
        defaults.points.removeIf(PointEntry::valid);
        saved.forEach((id, entry) -> defaults.points.add(fromSaved(id, entry)));
        return save(defaultFile());
    }

    private static PointEntry fromSaved(String id, IrcConfig.WaypointEntry saved) {
        PointEntry entry = new PointEntry(saved.name, WaypointManager.formatCoord(saved.x), WaypointManager.formatCoord(saved.y), WaypointManager.formatCoord(saved.z));
        entry.id = id;
        entry.visible = saved.enabled;
        return entry;
    }

    private static void syncDefault() {
        Collection defaults = FILES.get(defaultFile());
        if (defaults != null) {
            WaypointManager.replaceSavedEntries(defaults.points);
        }
    }

    public static void addEmpty() {
        Collection file = collection(currentFile());
        if (file == null) {
            return;
        }
        if (isDefault(current) && file.points.size() >= WaypointManager.MAX_WAYPOINTS) {
            return;
        }
        file.points.add(new PointEntry("", "", "", ""));
        saveCurrent();
    }

    public static boolean deleteAt(int index) {
        List<PointEntry> entries = entries();
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        PointEntry entry = entries.get(index);
        if (entry.temporary) {
            return WaypointManager.removeTemporary(entry);
        }
        List<PointEntry> points = collection(current).points;
        points.remove(index);
        if (saveCurrent()) {
            return true;
        }

        points.add(index, entry);
        return false;
    }

    public static Path create(String name) throws IOException {
        ensure();
        Path path = newPath(name, null);
        Collection file = new Collection(stem(path));
        write(path, file);
        FILES.put(path, file);
        return path;
    }

    public static Path rename(Path path, String name) throws IOException {
        if (external(path) || isDefault(path)) {
            throw new IllegalArgumentException("默认点位集不能改名");
        }
        Path target = newPath(name, path);
        Collection file = collection(path);
        if (file == null) {
            throw new IllegalArgumentException("点位集不存在");
        }
        requireUnchanged(path, file);
        Collection renamed = copy(file);
        renamed.name = stem(target);
        renamed.savedPath = file.savedPath;
        renamed.diskContent = file.diskContent;
        write(target, renamed);
        if (!target.equals(path)) {
            try {
                // The source may have changed while the new file was being written.
                requireUnchanged(path, file);
                Files.delete(path);
            } catch (IOException e) {
                try { Files.deleteIfExists(target); }
                catch (IOException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }
        file.name = renamed.name;
        file.rememberSaved(target, renamed.diskContent);
        FILES.remove(path);
        FILES.put(target, file);
        if (path.equals(current)) {
            current = target;
        }
        return target;
    }

    private static Path newPath(String name, Path original) {
        String clean = name == null ? "" : name.strip().replaceFirst("(?i)\\.json$", "").strip();
        if (clean.isEmpty()
                || clean.length() > 64
                || clean.endsWith(".")
                || clean.equals(".")
                || clean.equals("..")
                || clean.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0)
                || clean.split("\\.", 2)[0].toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new IllegalArgumentException("点位集名称无效");
        }

        Path target = getDir().resolve(clean + ".json");
        for (Path existing : FILES.keySet()) {
            if (!existing.equals(original) && existing.getFileName().toString().equalsIgnoreCase(target.getFileName().toString())) {
                throw new DuplicateNameException("已存在同名点位集");
            }
        }

        if (Files.exists(target) && !target.equals(original)) {
            throw new DuplicateNameException("已存在同名文件");
        }

        return target;
    }

    private static final class DuplicateNameException extends IllegalArgumentException {
        private DuplicateNameException(String message) { super(message); }
    }

    public static boolean delete(Path path) {
        if (external(path) || isDefault(path)) {
            return false;
        }
        try {
            Files.deleteIfExists(path);
            FILES.remove(path);
            if (path.equals(current)) {
                current = defaultFile();
            }
            return true;
        } catch (IOException e) {
            LoggerFactory.getLogger(WaypointFiles.class).warn("点位删除失败 {}", path, e);
            return false;
        }
    }

    public static Path importRoute(Path path) throws IOException {
        ensure();
        if (!external(path) || !isJson(path)
                || !path.toAbsolutePath().normalize().getParent().equals(routeDirectory.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("请选择 routes 目录中的 JSON");
        }

        Collection imported = read(path);
        if (importedFile(path) != null) {
            throw new IllegalArgumentException("该路线已导入，请使用更新");
        }
        imported.sourceRoute = path.getFileName().toString();
        imported.sourceNodes.clear();
        for (PointEntry point : imported.points) {
            imported.sourceNodes.add(WaypointRouteUpdate.snapshot(point, point));
        }

        String base = stem(path), name = base;
        for (int i = 2; ; i++) {
            try {
                Path target = newPath(name, null);
                imported.name = stem(target);
                write(target, imported);
                FILES.put(target, imported);
                return target;
            } catch (DuplicateNameException duplicate) {
                if (base.length() > 55) {
                    base = base.substring(0, 55);
                }

                name = base + " (" + i + ")";
            }
        }
    }

    /** Historical imports had no source metadata; adopt a matching name and overlapping nodes conservatively. */
    public static Path importedFile(Path route) {
        ensure();
        String source = route.getFileName().toString();
        for (var item : FILES.entrySet()) {
            if (source.equalsIgnoreCase(item.getValue().sourceRoute)) {
                return item.getKey();
            }
        }

        try {
            Collection incoming = read(route);
            String base = stem(route);
            Path candidate = null;
            int best = -1;
            for (var item : FILES.entrySet()) {
                Collection file = item.getValue();
                if (isDefault(item.getKey()) || !file.sourceRoute.isEmpty()) {
                    continue;
                }

                String name = stem(item.getKey());
                boolean sameName = name.equalsIgnoreCase(base) || name.matches("(?i)" + java.util.regex.Pattern.quote(base) + " \\(\\d+\\)");
                if (!sameName) {
                    continue;
                }
                int overlap = (int) file.points.stream()
                        .filter(local -> incoming.points.stream().anyMatch(point -> point.name.equals(local.name)))
                        .count();
                if ((overlap > 0 || incoming.points.isEmpty()) && overlap > best) {
                    candidate = item.getKey();
                    best = overlap;
                }
            }
            return candidate;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static WaypointRouteUpdate prepareUpdate(Path route) throws IOException {
        refresh();
        Path target = importedFile(route);
        if (target == null) {
            throw new IllegalArgumentException("该路线尚未导入");
        }

        if (FILES.get(target).hasUnsavedChanges()) {
            throw new IOException("点位集有未保存的编辑，请先保存后再更新");
        }
        Collection latest = read(target);
        if (!latest.sourceRoute.isEmpty()
                && !latest.sourceRoute.equalsIgnoreCase(route.getFileName().toString())) {
            throw new IOException("来源关联已变化，请重新加载列表");
        }

        FILES.put(target, latest);
        return new WaypointRouteUpdate(route, target, latest);
    }

    static void commitUpdate(Path path, Collection expected, Collection updated) throws IOException {
        if (FILES.get(path) != expected || !Files.exists(path)) {
            throw new IOException("点位文件已变化，请重新更新");
        }
        write(path, updated);
        FILES.put(path, updated);
    }

    static Collection copy(Collection file) {
        Collection result = new Collection(file.name);
        result.enabled = file.enabled;
        result.visible = file.visible;
        result.regions.clear();
        result.regions.addAll(file.regions);
        result.migratedProfiles.addAll(file.migratedProfiles);
        result.sourceRoute = file.sourceRoute;
        result.sourceNodes.addAll(file.sourceNodes);
        for (PointEntry point : file.points) result.points.add(WaypointRouteUpdate.copy(point));
        return result;
    }

    static Collection read(Path path) throws IOException {
        return loaded(path, readText(path));
    }

    private static String readText(Path path) throws IOException {
        if (Files.size(path) > 2 * 1024 * 1024) {
            throw new IOException("点位文件过大");
        }
        return Files.readString(path);
    }

    private static Collection loaded(Path path, String text) throws IOException {
        Collection file = parse(path, text);
        file.rememberSaved(path, text);
        return file;
    }

    static Collection parse(Path path, String text) throws IOException {
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject() && !parsed.isJsonArray()) {
            throw new IOException("无效的点位 JSON");
        }
        JsonObject root = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        Collection file = new Collection(root.has("name") ? root.get("name").getAsString() : stem(path));
        file.enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
        file.visible = !root.has("visible") || root.get("visible").getAsBoolean();
        if (root.has("regions")) {
            file.regions.clear();
            for (JsonElement region : root.getAsJsonArray("regions")) {
                file.regions.add(region.getAsString());
            }
        }
        if (file.regions.isEmpty()) {
            file.regions.add("all");
        }
        if (root.has("migratedProfiles")) {
            for (JsonElement profile : root.getAsJsonArray("migratedProfiles")) {
                file.migratedProfiles.add(profile.getAsString());
            }
        }
        if (root.has("sourceRoute")) {
            file.sourceRoute = root.get("sourceRoute").getAsString();
        }
        if (root.has("sourceNodes")) {
            file.sourceNodes.addAll(Arrays.asList(GSON.fromJson(root.get("sourceNodes"), WaypointRouteUpdate.SourceNode[].class)));
        }
        JsonArray source = parsed.isJsonArray() ? parsed.getAsJsonArray() : root.has("waypoints") ? root.getAsJsonArray("waypoints") : new JsonArray();
        readPoints(source, file.points);
        if (root.has("areas")) {
            for (JsonElement element : root.getAsJsonArray("areas")) {
                JsonObject area = element.getAsJsonObject();
                if (area.has("waypoints")) {
                    readPoints(area.getAsJsonArray("waypoints"), file.points);
                }
            }
        }
        return file;
    }

    private static void readPoints(JsonArray source, List<PointEntry> result) {
        for (JsonElement element : source) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject point = element.getAsJsonObject();
            JsonArray coords = null;
            for (String key : List.of("coords", "target", "pos")) {
                if (point.has(key) && point.get(key).isJsonArray()) {
                    coords = point.getAsJsonArray(key);
                    break;
                }
            }

            String name = "";
            for (String key : List.of("name", "label", "_comment")) {
                if (point.has(key)) {
                    name = point.get(key).getAsString();
                    break;
                }
            }

            String[] xyz = new String[3];
            for (int axis = 0; axis < 3; axis++) {
                String key = "xyz".substring(axis, axis + 1);
                xyz[axis] = point.has(key) ? point.get(key).getAsString() : coords != null && coords.size() > axis ? coords.get(axis).getAsString() : "";
            }
            PointEntry entry = new PointEntry(name, xyz[0], xyz[1], xyz[2]);
            entry.visible = point.has("visible") ? point.get("visible").getAsBoolean() : !point.has("enabled") || point.get("enabled").getAsBoolean();
            if (point.has("id")) {
                entry.id = point.get("id").getAsString();
                entry.explicitId = true;
            }
            result.add(entry);
            if (result.size() >= 10000) {
                break;
            }
        }
    }

    static void write(Path path, Collection file) throws IOException {
        // Do not overwrite external edits made since this collection was loaded/saved.
        if (path.equals(file.savedPath)) {
            requireUnchanged(path, file);
        }
        String text = serialize(file);
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".waypoint-", ".tmp");
        try {
            Files.writeString(temp, text);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            file.rememberSaved(path, text);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void requireUnchanged(Path path, Collection file) throws IOException {
        if (!Files.exists(path) || !readText(path).equals(file.diskContent)) {
            throw new IOException("点位文件已被外部修改，未保存的编辑已保留，请先处理文件冲突");
        }
    }

    private static String serialize(Collection file) {
        JsonObject root = new JsonObject();
        root.addProperty("name", file.name);
        root.addProperty("enabled", file.enabled);
        root.addProperty("visible", file.visible);
        root.add("regions", GSON.toJsonTree(file.regions));
        if (!file.migratedProfiles.isEmpty()) {
            root.add("migratedProfiles", GSON.toJsonTree(file.migratedProfiles));
        }
        if (!file.sourceRoute.isEmpty()) {
            root.addProperty("sourceRoute", file.sourceRoute);
            root.add("sourceNodes", GSON.toJsonTree(file.sourceNodes));
        }
        root.add("waypoints", GSON.toJsonTree(file.points.stream().filter(point -> !point.temporary).toList()));
        return GSON.toJson(root);
    }

    private static boolean isJson(Path path) { return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"); }

    private static String stem(Path path) { return path.getFileName().toString().replaceFirst("(?i)\\.json$", ""); }

    public static final class Collection {
        private transient Path savedPath;
        private transient String diskContent, savedState;
        private transient java.nio.file.attribute.FileTime modifiedTime;
        private transient long diskSize;
        private transient Object diskKey;
        public String name;
        public boolean enabled = true, visible = true;
        public final Set<String> regions = new LinkedHashSet<>(Set.of("all"));
        public final List<PointEntry> points = new ArrayList<>();
        private final Set<String> migratedProfiles = new LinkedHashSet<>();
        public String sourceRoute = "";
        public final List<WaypointRouteUpdate.SourceNode> sourceNodes = new ArrayList<>();
        public Collection(String name) { this.name = name; }

        private boolean hasUnsavedChanges() { return savedState != null && !savedState.equals(serialize(this)); }

        private void rememberSaved(Path path, String text) {
            savedPath = path;
            diskContent = text;
            savedState = serialize(this);
            try {
                rememberAttributes(Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class));
            } catch (IOException e) {
                modifiedTime = null;
            }
        }

        private boolean sameFile(java.nio.file.attribute.BasicFileAttributes attributes) {
            return modifiedTime != null && modifiedTime.equals(attributes.lastModifiedTime()) && diskSize == attributes.size()
                    && Objects.equals(diskKey, attributes.fileKey());
        }
        private void rememberAttributes(java.nio.file.attribute.BasicFileAttributes attributes) {
            modifiedTime = attributes.lastModifiedTime();
            diskSize = attributes.size();
            diskKey = attributes.fileKey();
        }

        public boolean activeIn(String island) { return enabled && visible && (regions.contains("all") || !island.isEmpty() && regions.contains(island)); }
    }
}
