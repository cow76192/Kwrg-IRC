package me.cookie.kwrg.irc.manager;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class CapeCache {
    private static final Map<String, RenderType> CAPE_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> CAPE_IDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<NativeImage>> CAPE_FRAMES_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, NativeImage> CAPE_ACTIVE_IMAGE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CAPE_LAST_FRAME_TIME = new ConcurrentHashMap<>();
    private static final Map<String, Integer> CAPE_CURRENT_FRAME_INDEX = new ConcurrentHashMap<>();
    private static final Map<String, int[]> CAPE_FRAME_DELAYS = new ConcurrentHashMap<>();
    // GIF 原生帧率
    private static final Map<String, Integer> CAPE_NATIVE_FPS = new ConcurrentHashMap<>();
    private static final Map<String, String> IRC_PLAYER_CAPES = new ConcurrentHashMap<>();
    private static final Map<String, Float> IRC_PLAYER_SCALES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CAPE_ACTIVE_USERS = new ConcurrentHashMap<>();
    private static final Map<String, Float> REMOTE_PLAYER_SCALES = new ConcurrentHashMap<>();
    // 记录对应披风是否具备合法鞘翅贴图
    private static final Map<String, Boolean> CAPE_HAS_ELYTRA = new ConcurrentHashMap<>();
    private static final Map<String, RenderType> ELYTRA_TYPE_CACHE = new ConcurrentHashMap<>();

    record CapePreview(Identifier id, long lastModified) {}

    private static final Map<String, CapePreview> CAPE_PREVIEW_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> CAPE_PREVIEW_LOADING = ConcurrentHashMap.newKeySet();

    private CapeCache() {}

    static Map<String, RenderType> capeTypes() { return CAPE_TYPE_CACHE; }
    static Map<String, Identifier> capeIds() { return CAPE_IDS_CACHE; }
    static Map<String, List<NativeImage>> capeFrames() { return CAPE_FRAMES_CACHE; }
    static Map<String, NativeImage> activeImages() { return CAPE_ACTIVE_IMAGE; }
    static Map<String, Long> lastFrameTimes() { return CAPE_LAST_FRAME_TIME; }
    static Map<String, Integer> currentFrameIndexes() { return CAPE_CURRENT_FRAME_INDEX; }
    static Map<String, int[]> frameDelays() { return CAPE_FRAME_DELAYS; }
    static Map<String, Integer> nativeFps() { return CAPE_NATIVE_FPS; }
    static Map<String, String> playerCapes() { return IRC_PLAYER_CAPES; }
    static Map<String, Float> playerScales() { return IRC_PLAYER_SCALES; }
    static Map<String, Set<String>> activeUsers() { return CAPE_ACTIVE_USERS; }
    static Map<String, Float> remoteScales() { return REMOTE_PLAYER_SCALES; }
    static Map<String, Boolean> hasElytra() { return CAPE_HAS_ELYTRA; }
    static Map<String, RenderType> elytraTypes() { return ELYTRA_TYPE_CACHE; }
    static Map<String, CapePreview> previews() { return CAPE_PREVIEW_CACHE; }
    static Set<String> previewLoading() { return CAPE_PREVIEW_LOADING; }

    static void clearAll() {
        Minecraft mc = Minecraft.getInstance();

        for (Identifier id : CAPE_IDS_CACHE.values()) {
            mc.getTextureManager().release(id);
        }

        for (Map.Entry<String, List<NativeImage>> entry : CAPE_FRAMES_CACHE.entrySet()) {
            List<NativeImage> frames = entry.getValue();
            NativeImage active = CAPE_ACTIVE_IMAGE.get(entry.getKey());
            if (active != null && (frames == null || !frames.contains(active))) {
                active.close();
            }
            if (frames != null) {
                for (NativeImage img : frames) img.close();
            }
        }

        CAPE_TYPE_CACHE.clear();
        CAPE_IDS_CACHE.clear();
        CAPE_FRAMES_CACHE.clear();
        CAPE_ACTIVE_IMAGE.clear();
        CAPE_LAST_FRAME_TIME.clear();
        CAPE_CURRENT_FRAME_INDEX.clear();
        CAPE_FRAME_DELAYS.clear();
        CAPE_NATIVE_FPS.clear();
        CAPE_HAS_ELYTRA.clear();
    }

    static void releaseCape(String capeName) {
        if (capeName == null) return;
        Minecraft mc = Minecraft.getInstance();

        Identifier id = CAPE_IDS_CACHE.remove(capeName);
        if (id != null) {
            mc.getTextureManager().release(id);
        }

        List<NativeImage> frames = CAPE_FRAMES_CACHE.remove(capeName);
        NativeImage active = CAPE_ACTIVE_IMAGE.remove(capeName);

        if (active != null && (frames == null || !frames.contains(active))) {
            active.close();
        }
        if (frames != null) {
            for (NativeImage img : frames) img.close();
        }

        CAPE_TYPE_CACHE.remove(capeName);
        CAPE_LAST_FRAME_TIME.remove(capeName);
        CAPE_CURRENT_FRAME_INDEX.remove(capeName);
        CAPE_FRAME_DELAYS.remove(capeName);
        CAPE_NATIVE_FPS.remove(capeName);
        CAPE_HAS_ELYTRA.remove(capeName);
    }

    static void releasePreviews() {
        Minecraft mc = Minecraft.getInstance();
        for (CapePreview p : CAPE_PREVIEW_CACHE.values()) {
            mc.getTextureManager().release(p.id());
        }
        CAPE_PREVIEW_CACHE.clear();
    }
}
