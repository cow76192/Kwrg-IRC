package me.cookie.kwrg.irc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IrcConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-irc-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kwrg-config-saver");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> pendingSaveTask = null;

    public String rank = "&7[Member&7]";
    public String namestyle = "";
    public String chatstyle = "";
    public boolean hideArmor = false;
    public boolean glowEnabled = true;
    public String glowColor = "#00FF88";
    public List<String> importantPlayers = new ArrayList<>();

    public boolean chat = true;
    public String chatSound = "CAT_AMBIENT_BABY";

    public boolean importantPlayer = true;
    public String playerFinderColor = "#FF3333";

    public boolean channelEnabled = false;
    public String currentChannel = "";
    public List<String> savedChannels = new ArrayList<>();

    public List<String> playerFinderTargets = new ArrayList<>();

    public boolean dynamicIsland = false;
    public boolean islandShowFps = true;
    public boolean islandShowPing = true;
    public boolean islandShowTps = true;
    public boolean islandShowRam = false;
    public boolean islandShowGpu = false;
    public boolean islandShowCpu = false;

    private static IrcConfig instance = new IrcConfig();
    private static String currentProfileName = "default";

    public static IrcConfig get() {
        return instance;
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

    public static void load() {
        try {
            Path metaPath = getMetaPath();
            String selected = "default";

            if (Files.exists(metaPath)) {
                String metaJson = Files.readString(metaPath);
                ProfileSelector selector = GSON.fromJson(metaJson, ProfileSelector.class);
                if (selector != null && selector.selected != null && !selector.selected.isBlank()) {
                    selected = sanitizeProfileName(selector.selected);
                }
            }

            loadProfile(selected, true);
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 初始化读取配置文件失败，启用内存默认配置", e);
            instance = new IrcConfig();
            currentProfileName = "default";
        }
    }

    public static boolean loadProfile(String profileName, boolean createIfMissing) {
        String cleanName = sanitizeProfileName(profileName);
        Path profilePath = getProfilePath(cleanName);

        try {
            if (Files.exists(profilePath)) {
                String json = Files.readString(profilePath);
                IrcConfig loaded = GSON.fromJson(json, IrcConfig.class);
                if (loaded == null) loaded = new IrcConfig();
                if (loaded.importantPlayers == null) loaded.importantPlayers = new ArrayList<>();
                if (loaded.playerFinderTargets == null) loaded.playerFinderTargets = new ArrayList<>();

                instance = loaded;
                currentProfileName = cleanName;
                saveSelectorMeta(cleanName);
                LOGGER.info("[Kwrg] 已加载配置文件: {}.json", cleanName);
                return true;
            } else if (createIfMissing) {
                instance = new IrcConfig();
                currentProfileName = cleanName;
                save();
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 加载配置文件 {}.json 失败", cleanName, e);
        }
        return false;
    }

    public static void createProfile(String profileName) {
        String cleanName = sanitizeProfileName(profileName);
        instance = new IrcConfig();
        currentProfileName = cleanName;
        LOGGER.info("[Kwrg] 已在内存中新建配置 [{}]（尚未保存到磁盘）", cleanName);
    }

    /**
     * 立即保存到磁盘
     */
    public static synchronized boolean save() {
        if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
            pendingSaveTask.cancel(false);
        }

        try {
            Path dir = getKwrgDir();
            Files.createDirectories(dir);

            Path profilePath = getProfilePath(currentProfileName);
            Files.writeString(profilePath, GSON.toJson(instance));

            saveSelectorMeta(currentProfileName);

            LOGGER.info("[Kwrg] 配置已成功保存至: {}.json", currentProfileName);
            return true;
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 保存配置文件失败", e);
            return false;
        }
    }

    /**
     * 【核心新增】：内存缓冲延迟防抖保存 (如颜色选择器快速拖动时调用)
     * 在设定的 delayMs 毫秒内如果再次调用，会自动重置计时器，直到停顿后才执行 1 次磁盘写入。
     */
    public static synchronized void saveDelayed(long delayMs) {
        if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
            pendingSaveTask.cancel(false);
        }

        pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
            save();
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
}