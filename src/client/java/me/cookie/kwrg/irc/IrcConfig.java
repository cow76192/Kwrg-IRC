package me.cookie.kwrg.irc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.cookie.kwrg.gui.hud.HudElement;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import me.cookie.kwrg.irc.manager.WaypointManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public List<WaypointEntry> waypoints = new ArrayList<>();
    public boolean routeShowLine = false;
    public boolean routeVisible = true;
    public int routeSeedVersion = 0;

    public boolean chat = true;
    public String chatSound = "CAT_AMBIENT_BABY";

    public boolean importantPlayer = true;
    public String playerFinderColor = "#FF3333";

    public boolean channelEnabled = false;
    public String currentChannel = "";
    public List<String> savedChannels = new ArrayList<>();

    public List<String> playerFinderTargets = new ArrayList<>();

    public float myScale = 1.0f;
    public String myCape = "";

    public int remoteCapeCanvasSize = 1024;
    public int capeCanvasSize = 2048;
    public int capeAnimFps = 10;
    public int capeRenderMaxFps = 120;

    public boolean islandShowFps = true;
    public boolean islandShowPing = true;
    public boolean islandShowTps = true;
    public boolean islandShowRam = false;
    public boolean islandShowGpu = false;
    public boolean islandShowCpu = false;
    public float tabOpacity = 1.0f;
    public boolean dynamicTab = true;
    public boolean islandPosAffectsTab = true;
    public boolean scoreboardHideScores = false;
    public Map<String, HudElement.State> hudElements = new LinkedHashMap<>();

    public boolean disableBlur = false;
    public float blurQuality = 8.0f;
    public float blurSize = 16.0f;

    public int guiFpsLimit = 60;

    public float dynamicIslandAAValue = 0.35f;
    public boolean islandShowServerPing = true;

    public boolean connectPublicChannelFirst = true;

    public String guiFont = "default";
    public String guiColor = "#11161F";

    public float guiScale = 1.0f;

    public boolean nickEnabled = false;
    public String customNick = "";
    public String nickRank = "MVP+";
    public String nickPlusColor = "RED";

    public boolean chatSuffixEnabled = false;
    public String chatSuffixPrefix = "";
    public String chatSuffix = "喵~";
    public boolean chatSuffixFollowMessageStyle = false;
    public boolean chatSuffixPublicChat = false;

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

                JsonObject jsonObj = GSON.fromJson(json, JsonObject.class);
                ConfigMigrator.migrate(jsonObj);
                IrcConfig loaded = GSON.fromJson(jsonObj, IrcConfig.class);

                if (loaded == null) loaded = new IrcConfig();
                if (loaded.importantPlayers == null) loaded.importantPlayers = new ArrayList<>();
                if (loaded.playerFinderTargets == null) loaded.playerFinderTargets = new ArrayList<>();
                if (loaded.myCape == null) loaded.myCape = "";

                if (loaded.myScale <= 0.0f || Float.isNaN(loaded.myScale)) loaded.myScale = 1.0f;
                if (loaded.capeCanvasSize < 64) loaded.capeCanvasSize = 4096;
                if (loaded.capeAnimFps <= 0) loaded.capeAnimFps = 10;
                if (loaded.capeRenderMaxFps <= 0) loaded.capeRenderMaxFps = 120;
                if (loaded.capeRenderMaxFps > me.cookie.kwrg.irc.manager.PlayerCustomizationManager.CAPE_MAX_FPS_LIMIT) {
                    loaded.capeRenderMaxFps = me.cookie.kwrg.irc.manager.PlayerCustomizationManager.CAPE_MAX_FPS_LIMIT;
                }
                if (loaded.capeAnimFps > loaded.capeRenderMaxFps) loaded.capeAnimFps = loaded.capeRenderMaxFps;

                if (loaded.hudElements == null) {
                    loaded.hudElements = new LinkedHashMap<>();
                } else {
                    loaded.hudElements.values().removeIf(Objects::isNull);
                    loaded.hudElements.values().forEach(HudElement.State::sanitize);
                }

                instance = loaded;
                currentProfileName = cleanName;
                saveSelectorMeta(cleanName);

                PlayerFinderManager.reloadFromConfig();
                WaypointManager.reloadFromConfig();

                LOGGER.info("[Kwrg] 已加载配置文件: {}.json", cleanName);
                return true;
            } else if (createIfMissing) {
                instance = new IrcConfig();
                currentProfileName = cleanName;
                WaypointManager.reloadFromConfig();
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
        WaypointManager.reloadFromConfig();
        LOGGER.info("[Kwrg] 已在内存中新建配置 [{}]（尚未保存到磁盘）", cleanName);
    }

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

    public static synchronized void saveDelayed(long delayMs) {
        if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
            pendingSaveTask.cancel(false);
        }

        pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
            synchronized (IrcConfig.class) {
                save();
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

    public static class WaypointEntry {
        public String name = "";
        public double x;
        public double y;
        public double z;
        public boolean enabled = true;
        public String source = "";

        public WaypointEntry() {
        }

        public WaypointEntry(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static Path getFontDir() {
        return getKwrgDir().resolve("font");
    }
}
