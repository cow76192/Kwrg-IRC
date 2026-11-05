package me.cookie.kwrg.irc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
        if (name == null || name.isBlank()) return "default";
        String clean = name.trim();
        if (clean.endsWith(".json")) {
            clean = clean.substring(0, clean.length() - 5);
        }
        return clean.isEmpty() ? "default" : clean;
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
                    selected = sanitizeProfileName(selector.selected);
                }
            }

            if (loadProfile(selected, true)) return;
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 初始化读取配置文件失败，启用内存默认配置", e);
        }
        activate(new IrcConfig(null), "default");
    }

    public static synchronized boolean loadProfile(String profileName, boolean createIfMissing) {
        String cleanName = sanitizeProfileName(profileName);
        try {
            Path profilePath = getProfilePath(cleanName);
            if (!Files.exists(profilePath) && !createIfMissing) return false;
            if (!flushPendingSave()) return false;

            if (Files.exists(profilePath)) {
                IrcConfig loaded = parse(GSON.fromJson(Files.readString(profilePath), JsonObject.class));
                activate(loaded, cleanName);
                saveSelectorMeta(cleanName);
                LOGGER.info("[Kwrg] 已加载配置文件: {}.json", cleanName);
                return true;
            }
            if (createIfMissing) {
                IrcConfig profile = new IrcConfig(null);
                writeProfile(cleanName, profile.toJson());
                activate(profile, cleanName);
                saveSelectorMeta(cleanName);
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 加载配置文件 {}.json 失败", cleanName, e);
        }
        return false;
    }

    public static synchronized boolean createProfile(String profileName) {
        String cleanName = sanitizeProfileName(profileName);
        if (!currentProfileName.equalsIgnoreCase(cleanName) && !flushPendingSave()) return false;

        activate(new IrcConfig(null), cleanName);
        LOGGER.info("[Kwrg] 已在内存中新建配置 [{}]（尚未保存到磁盘）", cleanName);
        return true;
    }

    public static synchronized void importProfile(String profileName, IrcConfig profile) throws IOException {
        String cleanName = sanitizeProfileName(profileName);
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

    public static synchronized void deleteProfile(String profileName) throws IOException {
        Files.deleteIfExists(getProfilePath(profileName));
        if (profileName.equalsIgnoreCase(currentProfileName)) {
            cancelPendingSave();
            dirty = false;
        }
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
        try {
            writeProfile(currentProfileName, instance.toJson());
            dirty = false;
            saveSelectorMeta(currentProfileName);
            LOGGER.info("[Kwrg] 配置已成功保存至: {}.json", currentProfileName);
            return true;
        } catch (Exception e) {
            dirty = true;
            LOGGER.error("[Kwrg] 保存配置文件失败", e);
            return false;
        }
    }

    private static void writeProfile(String name, String json) throws IOException {
        Files.createDirectories(getKwrgDir());
        Files.writeString(getProfilePath(name), json);
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
        pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
            synchronized (IrcConfig.class) {
                if (generation == saveGeneration) save();
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
        } catch (Exception e) {
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
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public static Path getFontDir() {
        return getKwrgDir().resolve("font");
    }
}
