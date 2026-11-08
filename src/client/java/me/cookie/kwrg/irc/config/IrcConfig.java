package me.cookie.kwrg.irc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IrcConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ModuleConfig<?>, Runnable> MODULE_CONFIGS = new LinkedHashMap<>();
    private static final Set<String> KNOWN_KEYS = new HashSet<>();
    private static final Set<String> RESERVED_PROFILE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private static boolean suppressSelectorWrite;

    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kwrg-config-saver");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> pendingSaveTask;
    private static long saveGeneration;
    private static boolean dirty;

    private static volatile IrcConfig instance = new IrcConfig(null);
    private static volatile String currentProfileName = "default";
    private static boolean profileLoaded;

    private final JsonObject source;
    private final Map<ModuleConfig<?>, Object> settings = new ConcurrentHashMap<>();

    private IrcConfig(JsonObject source) {
        this.source = source;
    }

    public static synchronized void register(ModuleConfig<?> config, Runnable onLoad) {
        if (!MODULE_CONFIGS.containsKey(config)) {
            Set<String> keys = config.keys();
            for (String key : keys) {
                if (KNOWN_KEYS.contains(key)) throw new IllegalArgumentException("Duplicate config key: " + key);
            }
            instance.settings(config);
            KNOWN_KEYS.addAll(keys);
        }
        MODULE_CONFIGS.put(config, onLoad);
        if (profileLoaded) notifyLoaded(onLoad);
    }

    static synchronized boolean isKnownKey(String key) {
        return KNOWN_KEYS.contains(key);
    }

    public static IrcConfig current() {
        return instance;
    }

    public <T> T settings(ModuleConfig<T> config) {
        return config.cast(settings.computeIfAbsent(config, key -> key.read(source)));
    }

    public static synchronized IrcConfig parse(JsonObject json) {
        JsonObject source = json.deepCopy();
        ConfigMigrator.migrate(source);
        return readSettings(source);
    }

    private static IrcConfig readSettings(JsonObject source) {
        IrcConfig profile = new IrcConfig(source);
        // Validate every module's settings before replacing the active profile.
        MODULE_CONFIGS.keySet().forEach(profile::settings);
        return profile;
    }

    public String toJson() {
        synchronized (IrcConfig.class) {
            return GSON.toJson(toJsonObject());
        }
    }

    private JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        for (ModuleConfig<?> config : MODULE_CONFIGS.keySet()) {
            GSON.toJsonTree(settings(config)).getAsJsonObject().asMap().forEach(json::add);
        }
        return json;
    }

    public static String getCurrentProfileName() {
        return currentProfileName;
    }

    public static Path getKwrgDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("kwrg");
    }

    private static Path getMetaPath() {
        return getKwrgDir().resolve("config.json");
    }

    private static Path getProfilePath(String profileName) {
        return getKwrgDir().resolve(profileName + ".json");
    }

    private static String sanitizeProfileName(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.strip();
        if (clean.endsWith(".json")) {
            clean = clean.substring(0, clean.length() - 5).strip();
        }
        if (clean.isEmpty() || clean.length() > 64) {
            return null;
        }
        if (clean.equals(".") || clean.equals("..") || clean.contains("..")) {
            return null;
        }
        if (clean.endsWith(".")) {
            return null;
        }
        if (RESERVED_PROFILE_NAMES.contains(clean.toUpperCase(Locale.ROOT))) {
            return null;
        }
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c < 0x20 || "<>:\"|?*/\\".indexOf(c) >= 0) {
                return null;
            }
        }
        return clean;
    }

    private static boolean isValidProfileName(String clean) {
        if (clean == null || clean.isEmpty() || clean.length() > 64) {
            return false;
        }
        if (clean.equals(".") || clean.equals("..") || clean.contains("..")) {
            return false;
        }
        if (clean.endsWith(".")) {
            return false;
        }
        if (RESERVED_PROFILE_NAMES.contains(clean.toUpperCase(Locale.ROOT))
                || me.cookie.kwrg.util.SafePaths.isReservedDeviceName(clean)) {
            return false;
        }
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c < 0x20 || c == '<' || c == '>' || c == ':' || c == '"'
                    || c == '|' || c == '?' || c == '*' || c == '/' || c == '\\') {
                return false;
            }
        }
        return true;
    }

    private static class ProfileSelector {
        String selected = "default";
    }

    public static synchronized void load() {
        try {
            Path metaPath = getMetaPath();
            String selected = "default";

            if (Files.exists(metaPath)) {
                ProfileSelector selector = GSON.fromJson(Files.readString(metaPath), ProfileSelector.class);
                if (selector != null && selector.selected != null && !selector.selected.isBlank()) {
                    String candidate = selector.selected.strip();
                    if (isValidProfileName(candidate)) {
                        selected = candidate;
                    } else {
                        suppressSelectorWrite = true;
                        LOGGER.warn("[Kwrg] config.json 记录的配置文件名非法，已忽略且不覆盖原记录: {}", selector.selected);
                    }
                }
            }

            if (loadProfile(selected, true)) {
                suppressSelectorWrite = false;
                return;
            }
            suppressSelectorWrite = false;
        } catch (IOException | RuntimeException e) {
            suppressSelectorWrite = false;
            LOGGER.error("[Kwrg] 初始化读取配置文件失败，启用内存默认配置", e);
        }
        activate(new IrcConfig(null), "default");
    }

    public static synchronized boolean loadProfile(String profileName, boolean createIfMissing) {
        String cleanName = profileName == null ? null : profileName.strip();
        if (cleanName == null || !isValidProfileName(cleanName)) {
            LOGGER.warn("[Kwrg] 非法配置文件名，已拒绝加载: {}", profileName);
            return false;
        }
        try {
            Path profilePath = getProfilePath(cleanName);
            if (!Files.exists(profilePath) && !createIfMissing) return false;
            if (!flushPendingSave()) return false;

            if (Files.exists(profilePath)) {
                JsonObject rawConfig = GSON.fromJson(Files.readString(profilePath), JsonObject.class);
                IrcConfig loaded = parse(rawConfig);
                activate(loaded, cleanName);
                if (!suppressSelectorWrite) saveSelectorMeta(cleanName);
                if (rawConfig != null && !loaded.source.equals(rawConfig)) {
                    try {
                        writeProfile(cleanName, GSON.toJson(loaded.source));
                    } catch (IOException | SecurityException e) {
                        LOGGER.error("[Kwrg] 写入迁移后的配置失败: {}.json", cleanName, e);
                    }
                }
                LOGGER.info("[Kwrg] 已加载配置文件: {}.json", cleanName);
                return true;
            }
            if (createIfMissing) {
                IrcConfig profile = new IrcConfig(null);
                writeProfile(cleanName, profile.toJson());
                activate(profile, cleanName);
                if (!suppressSelectorWrite) saveSelectorMeta(cleanName);
                return true;
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[Kwrg] 加载配置文件 {}.json 失败", cleanName, e);
        }
        return false;
    }

    public static synchronized boolean createProfile(String profileName) {
        String cleanName = sanitizeProfileName(profileName);
        if (cleanName == null) {
            LOGGER.warn("[Kwrg] 非法配置文件名，已拒绝新建: {}", profileName);
            return false;
        }
        if (!currentProfileName.equalsIgnoreCase(cleanName) && !flushPendingSave()) return false;

        activate(new IrcConfig(null), cleanName);
        LOGGER.info("[Kwrg] 已在内存中新建配置 [{}]（尚未保存到磁盘）", cleanName);
        return true;
    }

    public static synchronized void importProfile(String profileName, IrcConfig profile) throws IOException {
        String cleanName = sanitizeProfileName(profileName);
        if (cleanName == null) {
            throw new IOException("非法配置文件名: " + profileName);
        }
        JsonObject json = profile.toJsonObject();
        // Activate the same values a later reload will see, including defaults for omitted null fields.
        IrcConfig imported = readSettings(json);
        if (!currentProfileName.equalsIgnoreCase(cleanName) && !flushPendingSave()) {
            throw new IOException("当前配置保存失败: " + currentProfileName + ".json");
        }
        writeProfile(cleanName, GSON.toJson(json));
        activate(imported, cleanName);
        saveSelectorMeta(cleanName);
    }

    public static synchronized boolean deleteProfile(String profileName) throws IOException {
        String cleanName = profileName == null ? null : profileName.strip();
        if (cleanName == null || !isValidProfileName(cleanName)) {
            LOGGER.warn("[Kwrg] 非法配置文件名，已拒绝删除: {}", profileName);
            return false;
        }
        Files.deleteIfExists(getProfilePath(cleanName));
        if (cleanName.equalsIgnoreCase(currentProfileName)) {
            cancelPendingSave();
            dirty = false;
        }
        return true;
    }

    private static void activate(IrcConfig profile, String name) {
        cancelPendingSave();
        dirty = false;
        instance = profile;
        currentProfileName = name;
        profileLoaded = true;
        MODULE_CONFIGS.values().forEach(IrcConfig::notifyLoaded);
    }

    private static void notifyLoaded(Runnable onLoad) {
        try {
            onLoad.run();
        } catch (RuntimeException e) {
            LOGGER.error("[Kwrg] 同步模块配置失败", e);
        }
    }

    public static synchronized boolean save() {
        cancelPendingSave();
        String cleanName = currentProfileName == null ? null : currentProfileName.strip();
        if (cleanName == null || !isValidProfileName(cleanName)) {
            dirty = true;
            LOGGER.error("[Kwrg] 当前配置文件名非法，已拒绝保存: {}", currentProfileName);
            return false;
        }
        try {
            writeProfile(cleanName, instance.toJson());
            dirty = false;
            saveSelectorMeta(cleanName);
            LOGGER.info("[Kwrg] 配置已成功保存至: {}.json", cleanName);
            return true;
        } catch (IOException | RuntimeException e) {
            dirty = true;
            LOGGER.error("[Kwrg] 保存配置文件失败", e);
            return false;
        }
    }

    private static void writeProfile(String name, String json) throws IOException {
        Path dir = getKwrgDir();
        Files.createDirectories(dir);
        Path target = getProfilePath(name);
        Path temp = Files.createTempFile(dir, "." + name, ".tmp");
        try {
            Files.writeString(temp, json);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean flushPendingSave() {
        return !dirty || save();
    }

    private static void cancelPendingSave() {
        saveGeneration++;
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel(false);
            pendingSaveTask = null;
        }
    }

    public static synchronized void saveDelayed(long delayMs) {
        cancelPendingSave();
        dirty = true;
        long generation = saveGeneration;
        String scheduledProfile = currentProfileName;
        pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
            synchronized (IrcConfig.class) {
                if (generation == saveGeneration && scheduledProfile.equals(currentProfileName)) save();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static void saveSelectorMeta(String selected) {
        try {
            Path dir = getKwrgDir();
            Files.createDirectories(dir);

            ProfileSelector selector = new ProfileSelector();
            selector.selected = selected;
            Files.writeString(getMetaPath(), GSON.toJson(selector));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[Kwrg] 更新 config.json 失败", e);
        }
    }

    public static List<String> listExistingProfiles() {
        List<String> list = new ArrayList<>();
        Path dir = getKwrgDir();
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path p : stream) {
                    String fileName = p.getFileName().toString();
                    if (!fileName.equalsIgnoreCase("config.json")) {
                        list.add(fileName.substring(0, fileName.length() - 5));
                    }
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return list;
    }

    public static Path getFontDir() {
        return getKwrgDir().resolve("font");
    }
}
