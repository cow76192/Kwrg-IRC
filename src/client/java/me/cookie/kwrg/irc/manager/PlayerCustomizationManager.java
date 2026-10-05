package me.cookie.kwrg.irc.manager;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.mixin.accessor.NativeImageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class PlayerCustomizationManager {

    public static final float[] SCALE_STEPS = {0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f};

    public static final int CAPE_MAX_FPS_LIMIT = 1000;

    private static final Map<String, RenderType> CAPE_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> CAPE_IDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<NativeImage>> CAPE_FRAMES_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, NativeImage> CAPE_ACTIVE_IMAGE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CAPE_LAST_FRAME_TIME = new ConcurrentHashMap<>();
    private static final Map<String, Integer> CAPE_CURRENT_FRAME_INDEX = new ConcurrentHashMap<>();
    private static final Map<String, int[]> CAPE_FRAME_DELAYS = new ConcurrentHashMap<>();
    private static final Map<String, String> IRC_PLAYER_CAPES = new ConcurrentHashMap<>();
    private static final Map<String, Float> IRC_PLAYER_SCALES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CAPE_ACTIVE_USERS = new ConcurrentHashMap<>();
    private static final Map<String, Float> REMOTE_PLAYER_SCALES = new ConcurrentHashMap<>();
    public static volatile boolean inInventoryRendering = false;

    private static volatile Object localRenderState = null;

    public static final int CAPE_PREVIEW_W = 40;
    public static final int CAPE_PREVIEW_H = 64;

    private record CapePreview(Identifier id, long lastModified) {}

    private static final Map<String, CapePreview> CAPE_PREVIEW_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> CAPE_PREVIEW_LOADING = ConcurrentHashMap.newKeySet();

    private static final ExecutorService PREVIEW_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kwrg-cape-preview");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService CAPE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kwrg-cape-loader");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicLong CURRENT_LOAD_TOKEN = new AtomicLong(0);
    private static volatile String activeCapeName = null;
    private static volatile String loadingCapeName = null;
    private static volatile String syncedPresetCape = null;
    private static volatile String syncedGifCape = null;

    public static float getMyScale() { return IrcConfig.get().myScale; }

    public static Identifier peekCapePreview(String fileName) {
        CapePreview p = CAPE_PREVIEW_CACHE.get(fileName);
        return p == null ? null : p.id();
    }

    public static void requestCapePreview(String fileName) {
        if (fileName == null || fileName.isBlank()) return;

        Path file = getCapeDir().resolve(fileName);
        long modified;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return;
        }

        CapePreview cached = CAPE_PREVIEW_CACHE.get(fileName);
        if (cached != null && cached.lastModified() == modified) return;
        if (!CAPE_PREVIEW_LOADING.add(fileName)) return;

        PREVIEW_EXECUTOR.submit(() -> {
            NativeImage img = null;
            boolean handedOver = false;
            try {
                BufferedImage src = ImageIO.read(file.toFile());
                if (src == null) {
                    return;
                }

                int frames = "gif".equals(capeMagic(file)) ? 1 : parseRequestedFrameCount(fileName);
                if (frames >= 2 && src.getWidth() >= frames) {
                    src = src.getSubimage(0, 0, src.getWidth() / frames, src.getHeight());
                }

                img = toNativeImage(downscaleSmooth(src, CAPE_PREVIEW_W, CAPE_PREVIEW_H));
                final NativeImage finalImg = img;

                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        CapePreview old = CAPE_PREVIEW_CACHE.get(fileName);
                        if (old != null) mc.getTextureManager().release(old.id());

                        Identifier id = Identifier.fromNamespaceAndPath("kwrg",
                                "cape_preview/" + sanitizePath(fileName) + "_" + Integer.toHexString(fileName.hashCode()));
                        mc.getTextureManager().register(id,
                                new DynamicTexture(() -> "kwrg_cape_preview", finalImg));
                        CAPE_PREVIEW_CACHE.put(fileName, new CapePreview(id, modified));
                    } catch (Exception e) {
                        e.printStackTrace();
                        finalImg.close();
                    } finally {
                        CAPE_PREVIEW_LOADING.remove(fileName);
                    }
                });
                handedOver = true;
            } catch (Exception e) {
                System.err.println("[KWRG Preview] load failed: " + fileName);
                e.printStackTrace();
            } finally {
                if (!handedOver) {
                    if (img != null) img.close();
                    CAPE_PREVIEW_LOADING.remove(fileName);
                }
            }
        });
    }

    public static void clearCapePreviews() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(PlayerCustomizationManager::clearCapePreviews);
            return;
        }
        for (CapePreview p : CAPE_PREVIEW_CACHE.values()) {
            mc.getTextureManager().release(p.id());
        }
        CAPE_PREVIEW_CACHE.clear();
    }

    public static void setMyScale(float scale) {
        IrcConfig.get().myScale = scale;
        IrcConfig.save();
        IrcClient.sendScaleSync(scale);
    }

    public static void setRemotePlayerScale(String username, float scale) {
        if (username == null || username.isBlank()) return;
        String key = username.toLowerCase(Locale.ROOT);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            REMOTE_PLAYER_SCALES.put(key, scale);
        } else {
            REMOTE_PLAYER_SCALES.remove(key);
        }
    }

    public static float getPlayerScale(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            return getMyScale();
        }
        String name = player.getGameProfile().name().toLowerCase(Locale.ROOT);
        return REMOTE_PLAYER_SCALES.getOrDefault(name, 1.0f);
    }

    public static void clearRemotePlayerScales() {
        REMOTE_PLAYER_SCALES.clear();
    }

    public static float cycleMyScale() {
        float current = getMyScale();
        for (int i = 0; i < SCALE_STEPS.length; i++) {
            if (Math.abs(current - SCALE_STEPS[i]) < 0.001f) {
                float next = SCALE_STEPS[(i + 1) % SCALE_STEPS.length];
                setMyScale(next);
                return next;
            }
        }
        setMyScale(1.0f);
        return 1.0f;
    }

    public static void reloadCurrentCape() {
        String current = getMyCape();
        if (current == null || current.isBlank()) return;
        triggerAsyncLoad(current, true);
    }

    public static void markLocalRenderState(Object state) { localRenderState = state; }
    public static boolean isLocalRenderState(Object state) { return state != null && state == localRenderState; }

    public static Path getCapeDir() { return IrcConfig.getKwrgDir().resolve("capes"); }

    public static List<String> getAvailableCapes() {
        Path dir = getCapeDir();
        if (!Files.exists(dir)) return new ArrayList<>();
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String lower = n.toLowerCase(Locale.ROOT);
                        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif");
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void setIrcPlayerCustomization(String username, String capeFile, float scale) {
        String key = capeKey(username);
        if (key.isEmpty()) return;

        if (capeFile != null && !capeFile.isBlank()) {
            IRC_PLAYER_CAPES.put(key, capeFile);

            boolean isNearby = CAPE_ACTIVE_USERS.values().stream().anyMatch(set -> set.contains(key));
            if (isNearby) {
                bindPlayerCape(key, capeFile);
            }
        } else {
            IRC_PLAYER_CAPES.remove(key);
            unbindPlayer(key);
        }

        if (scale > 0.1f && scale <= 5.0f) {
            IRC_PLAYER_SCALES.put(key, scale);
        } else {
            IRC_PLAYER_SCALES.remove(key);
        }
    }

    public static void bindPlayerCape(String username, String newCape) {
        String key = capeKey(username);
        if (key.isEmpty()) return;

        String oldCape = null;
        for (Map.Entry<String, Set<String>> entry : CAPE_ACTIVE_USERS.entrySet()) {
            if (entry.getValue().remove(key)) {
                oldCape = entry.getKey();
                if (entry.getValue().isEmpty()) {
                    CAPE_ACTIVE_USERS.remove(oldCape);
                    releaseSpecificCape(oldCape);
                }
                break;
            }
        }

        if (newCape != null && !newCape.isBlank()) {
            CAPE_ACTIVE_USERS.computeIfAbsent(newCape, k -> ConcurrentHashMap.newKeySet()).add(key);
            triggerAsyncLoad(newCape);
        }
    }

    public static void preloadIrcPlayer(String username) {
        String capeName = IRC_PLAYER_CAPES.get(capeKey(username));
        if (capeName != null && !capeName.isBlank()) {
            bindPlayerCape(username, capeName);
        }
    }


    public static void unbindPlayer(String username) {
        bindPlayerCape(username, null);
    }

    public static void removeIrcPlayer(String username) {
        String key = capeKey(username);
        IRC_PLAYER_CAPES.remove(key);
        IRC_PLAYER_SCALES.remove(key);
    }

    private static String capeKey(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    public static RenderType getPlayerCapeRenderType(Player player) {
        Minecraft mc = Minecraft.getInstance();
        String capeName;
        if (player == mc.player) {
            capeName = getMyCape();
        } else {
            capeName = IRC_PLAYER_CAPES.get(capeKey(player.getGameProfile().name()));
        }

        if (capeName == null || capeName.isBlank()) return null;

        RenderType type = CAPE_TYPE_CACHE.get(capeName);
        if (type != null) {
            updateFrameIfNeeded(capeName);
            return type;
        }

        triggerAsyncLoad(capeName);
        return null;
    }

    public static String getMyCape() {
        String cape = IrcConfig.get().myCape;
        return (cape != null && !cape.isBlank()) ? cape : null;
    }

    public static void setMyCape(String fileName) {
        String target = (fileName != null && !fileName.isBlank()) ? fileName : "";
        IrcConfig.get().myCape = target;
        IrcConfig.save();

        if (target.isBlank()) {
            CURRENT_LOAD_TOKEN.incrementAndGet();
            loadingCapeName = null;
            clearCapeCache();
            activeCapeName = null;
            syncedPresetCape = null;
            syncedGifCape = null;
        } else {
            if (!target.equals(loadingCapeName)) {
                loadingCapeName = null;
            }
            triggerAsyncLoad(target);
            IrcCapeManager.syncMyCape();
            applyCapePresetFps(target);
        }
    }

    private static void applyCapePresetFps(String capeName) {
        if (capeName.equals(syncedPresetCape)) return;
        syncedPresetCape = capeName;

        if (parseRequestedFrameCount(capeName) < 2) return;

        int preset = getCapePresetFps(capeName);
        if (preset <= 0) return;

        IrcConfig.get().capeAnimFps = preset;
        IrcConfig.save();
        refreshFrameTiming();
    }

    private static void applyGifPresetFps(String capeName, int[] delays) {
        if (!capeName.equals(getMyCape())) return;
        if (capeName.equals(syncedGifCape)) return;
        syncedGifCape = capeName;
        if (getCapePresetFps(capeName) > 0) return;

        long sum = 0;
        for (int delay : delays) sum += delay;
        if (sum <= 0) return;

        IrcConfig.get().capeAnimFps = clampFps((int) Math.max(1L, 1000L * delays.length / sum));
        IrcConfig.save();
        refreshFrameTiming();
    }

    public static void clearCapeCache() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(PlayerCustomizationManager::clearCapeCache);
            return;
        }

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
        activeCapeName = null;
        loadingCapeName = null;
    }

    private static void releaseSpecificCape(String capeName) {
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
    }

    private static void cleanupLoading(long token) {
        if (token == CURRENT_LOAD_TOKEN.get()) {
            loadingCapeName = null;
        }
    }

    private static void triggerAsyncLoad(String value) {
        triggerAsyncLoad(value, false);
    }

    private static void triggerAsyncLoad(String value, boolean force) {
        if (value == null || value.isBlank()) return;

        if (!force && value.equals(activeCapeName) && CAPE_TYPE_CACHE.containsKey(value)) {
            return;
        }
        if (!force && value.equals(loadingCapeName)) {
            return;
        }

        loadingCapeName = value;
        final long token = CURRENT_LOAD_TOKEN.incrementAndGet();

        CAPE_EXECUTOR.submit(() -> {
            List<NativeImage> frames = new ArrayList<>();
            NativeImage activeImage = null;
            try {
                Path file = getCapeDir().resolve(value);
                if (!Files.exists(file) || token != CURRENT_LOAD_TOKEN.get()) {
                    cleanupLoading(token);
                    return;
                }

                String magic = capeMagic(file);
                boolean gifSource = "gif".equals(magic);
                String extension = capeExtension(value);
                if (!magicMatchesExtension(magic, extension)) {
                    System.err.println("[KWRG Cape] " + value + " extension=" + extension + " content=" + magic);
                }

                BufferedImage src = gifSource ? null : ImageIO.read(file.toFile());
                if ((!gifSource && src == null) || token != CURRENT_LOAD_TOKEN.get()) {
                    cleanupLoading(token);
                    return;
                }

                List<BufferedImage> blocks;
                if (gifSource) {
                    blocks = layoutGifFrames(file, value);
                    if (blocks == null || blocks.isEmpty()) {
                        System.err.println("[KWRG Cape] " + value + " gif decode failed, fallback to first frame");
                        BufferedImage first = ImageIO.read(file.toFile());
                        blocks = first == null ? null : layoutFrames(first, plainCapeName(value));
                    }
                } else {
                    blocks = layoutFrames(src, value);
                }
                if (blocks == null || blocks.isEmpty() || token != CURRENT_LOAD_TOKEN.get()) {
                    cleanupLoading(token);
                    return;
                }

                for (BufferedImage block : blocks) {
                    if (token != CURRENT_LOAD_TOKEN.get()) {
                        frames.forEach(NativeImage::close);
                        cleanupLoading(token);
                        return;
                    }
                    frames.add(toNativeImage(block));
                }

                if (frames.size() > 1) {
                    NativeImage firstFrame = frames.get(0);
                    activeImage = new NativeImage(firstFrame.getWidth(), firstFrame.getHeight(), false);
                    activeImage.copyFrom(firstFrame);
                } else {
                    activeImage = frames.get(0);
                }

                if (token != CURRENT_LOAD_TOKEN.get()) {
                    if (activeImage != frames.get(0)) activeImage.close();
                    frames.forEach(NativeImage::close);
                    cleanupLoading(token);
                    return;
                }

                final NativeImage finalActive = activeImage;
                Minecraft.getInstance().execute(() -> {
                    try {
                        if (token != CURRENT_LOAD_TOKEN.get()) {
                            if (finalActive != frames.get(0)) finalActive.close();
                            frames.forEach(NativeImage::close);
                            return;
                        }

                        Minecraft mc = Minecraft.getInstance();
                        Identifier id = Identifier.fromNamespaceAndPath("kwrg", sanitizePath(value));

                        DynamicTexture tex = new DynamicTexture(() -> "kwrg_cape", finalActive);
                        mc.getTextureManager().register(id, tex);

                        int blockWidth = finalActive.getWidth();
                        FilterMode filterMode = (blockWidth <= 256) ? FilterMode.NEAREST : FilterMode.LINEAR;
                        GpuSampler sampler = (filterMode == FilterMode.NEAREST)
                                ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                                : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

                        RenderType out = RenderType.create("kwrg_cape", RenderSetup.builder(RenderPipelines.ENTITY_SOLID)
                                .withTexture("Sampler0", id, () -> sampler)
                                .useLightmap()
                                .useOverlay()
                                .affectsCrumbling()
                                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                                .createRenderSetup());

                        List<NativeImage> prevFrames = CAPE_FRAMES_CACHE.remove(value);
                        NativeImage prevActive = CAPE_ACTIVE_IMAGE.remove(value);
                        if (prevActive != null && (prevFrames == null || !prevFrames.contains(prevActive))) {
                            prevActive.close();
                        }
                        if (prevFrames != null) {
                            for (NativeImage img : prevFrames) img.close();
                        }

                        CAPE_FRAMES_CACHE.put(value, frames);
                        CAPE_ACTIVE_IMAGE.put(value, finalActive);
                        CAPE_TYPE_CACHE.put(value, out);
                        CAPE_IDS_CACHE.put(value, id);
                        CAPE_CURRENT_FRAME_INDEX.put(value, 0);
                        CAPE_LAST_FRAME_TIME.put(value, System.currentTimeMillis());

                        activeCapeName = value;

                        int[] gifDelays = CAPE_FRAME_DELAYS.get(value);
                        if (gifDelays != null) applyGifPresetFps(value, gifDelays);
                    } catch (Exception e) {
                        if (finalActive != frames.get(0)) finalActive.close();
                        frames.forEach(NativeImage::close);
                    } finally {
                        cleanupLoading(token);
                    }
                });

            } catch (Exception e) {
                if (activeImage != null && (frames.isEmpty() || activeImage != frames.get(0))) activeImage.close();
                frames.forEach(NativeImage::close);
                cleanupLoading(token);
            }
        });
    }

    public static RenderType resolveMyCapeRenderType() {
        String value = getMyCape();
        if (value == null) return null;

        RenderType type = CAPE_TYPE_CACHE.get(value);
        if (type != null) {
            updateFrameIfNeeded(value);
            return type;
        }

        if (activeCapeName != null) {
            RenderType oldType = CAPE_TYPE_CACHE.get(activeCapeName);
            if (oldType != null) {
                if (!value.equals(loadingCapeName)) {
                    triggerAsyncLoad(value);
                }
                return oldType;
            }
        }

        if (!value.equals(loadingCapeName)) {
            triggerAsyncLoad(value);
        }

        return null;
    }

    private static void updateFrameIfNeeded(String value) {
        List<NativeImage> frames = CAPE_FRAMES_CACHE.get(value);
        if (frames == null || frames.size() <= 1) return;

        int fps = resolvePlaybackFps(value);
        int currentIndex = CAPE_CURRENT_FRAME_INDEX.getOrDefault(value, 0);
        int[] delays = CAPE_FRAME_DELAYS.get(value);

        long interval;
        if (delays != null && delays.length == frames.size()) {
            long sum = 0;
            for (int delay : delays) sum += delay;
            long average = Math.max(1L, sum / delays.length);
            interval = Math.max(1L, delays[currentIndex] * (1000L / fps) / average);
        } else {
            interval = 1000L / fps;
        }

        long now = System.currentTimeMillis();
        long lastTime = CAPE_LAST_FRAME_TIME.getOrDefault(value, 0L);

        if (now - lastTime >= interval) {
            int nextIndex = (currentIndex + 1) % frames.size();

            NativeImage activeImage = CAPE_ACTIVE_IMAGE.get(value);
            Identifier id = CAPE_IDS_CACHE.get(value);
            if (activeImage != null && id != null) {
                NativeImage nextFrame = frames.get(nextIndex);

                int block = activeImage.getWidth();
                int u = block / 64;
                int validWidth = Math.min(block, 22 * u + 2);
                int validHeight = Math.min(block / 2, 17 * u + 2);

                long srcPtr = ((NativeImageAccessor) (Object) nextFrame).kwrg$getPixels();
                long dstPtr = ((NativeImageAccessor) (Object) activeImage).kwrg$getPixels();
                int bytesPerRow = validWidth * 4; // 每个像素 4 字节 (RGBA)
                int srcStride = nextFrame.getWidth() * 4;
                int dstStride = activeImage.getWidth() * 4;

                for (int y = 0; y < validHeight; y++) {
                    MemoryUtil.memCopy(srcPtr + (long) y * srcStride, dstPtr + (long) y * dstStride, bytesPerRow);
                }

                Minecraft mc = Minecraft.getInstance();
                AbstractTexture tex = mc.getTextureManager().getTexture(id);
                if (tex instanceof DynamicTexture dt) {
                    dt.upload();
                }

                CAPE_CURRENT_FRAME_INDEX.put(value, nextIndex);
                CAPE_LAST_FRAME_TIME.put(value, now);
            }
        }
    }

    private static List<BufferedImage> layoutFrames(BufferedImage src, String fileName) {
        List<BufferedImage> blocks = new ArrayList<>();

        boolean isRemote = fileName != null && fileName.startsWith("cache/");
        int block = isRemote ? IrcConfig.get().remoteCapeCanvasSize : IrcConfig.get().capeCanvasSize;

        int srcW = src.getWidth();
        int srcH = src.getHeight();

        int requestedFrames = parseRequestedFrameCount(fileName);
        boolean isAnimated = requestedFrames >= 2 && srcW >= requestedFrames;

        if (isAnimated) {
            if (isRemote) {
                block = Math.min(block, 512);
            } else {
                block = Math.min(block, 1024);
            }
        }

        int faceW = block * 10 / 64;
        int faceH = block * 16 / 64;

        if (isAnimated) {
            int frameWidth = srcW / requestedFrames;
            for (int k = 0; k < requestedFrames; k++) {
                BufferedImage frame = src.getSubimage(k * frameWidth, 0, frameWidth, srcH);
                blocks.add(paintBlock(downscaleSmooth(frame, faceW, faceH), block, faceW, faceH));
            }
            return blocks;
        }

        blocks.add(paintBlock(downscaleSmooth(src, faceW, faceH), block, faceW, faceH));
        return blocks;
    }

    private static String capeMagic(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = new byte[12];
            int read = in.read(header);

            if (read >= 3 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') return "gif";
            if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return hasPngAnimationChunk(file) ? "apng" : "png";
            }
            if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8) return "jpg";
            if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "webp";

            if (read <= 0) return "empty";
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(read, 8); i++) hex.append(String.format("%02X", header[i]));
            return "raw:" + hex;
        } catch (Exception e) {
            return "unreadable";
        }
    }

    private static boolean hasPngAnimationChunk(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] chunk = new byte[4096];
            int read = in.read(chunk);
            if (read <= 0) return false;
            return new String(chunk, 0, read, StandardCharsets.ISO_8859_1).contains("acTL");
        } catch (Exception e) {
            return false;
        }
    }

    private static String plainCapeName(String fileName) {
        if (fileName == null) return null;
        return fileName.replace('@', '_').replace('#', '_');
    }

    private static String capeExtension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static boolean magicMatchesExtension(String magic, String extension) {
        if ("jpg".equals(extension) || "jpeg".equals(extension)) return "jpg".equals(magic);
        if ("gif".equals(extension)) return "gif".equals(magic);
        if ("png".equals(extension)) return "png".equals(magic);
        return true;
    }

    private static List<BufferedImage> layoutGifFrames(Path file, String fileName) throws Exception {
        boolean isRemote = fileName != null && fileName.startsWith("cache/");
        int block = isRemote ? Math.min(IrcConfig.get().remoteCapeCanvasSize, 512) : Math.min(IrcConfig.get().capeCanvasSize, 1024);
        int faceW = block * 10 / 64;
        int faceH = block * 16 / 64;

        List<BufferedImage> blocks = new ArrayList<>();
        ImageReader reader = null;
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) return null;

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;

            reader = readers.next();
            input.seek(0);
            reader.setInput(input, false, false);

            int count = reader.getNumImages(true);
            if (count <= 0) return null;

            int[] delays = new int[count];
            BufferedImage canvas = new BufferedImage(reader.getWidth(0), reader.getHeight(0), BufferedImage.TYPE_INT_ARGB);
            BufferedImage previous = null;

            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                int[] info = gifFrameInfo(reader.getImageMetadata(i));
                int left = info[0];
                int top = info[1];
                int disposal = info[2];
                delays[i] = Math.max(10, info[3]);

                if (disposal == 3) previous = copyOf(canvas);

                boolean fullSize = frame.getWidth() == canvas.getWidth() && frame.getHeight() == canvas.getHeight();
                int x = fullSize ? 0 : left;
                int y = fullSize ? 0 : top;

                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, x, y, null);
                g.dispose();

                blocks.add(paintBlock(downscaleSmooth(copyOf(canvas), faceW, faceH), block, faceW, faceH));

                if (disposal == 2) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
                    clear.dispose();
                } else if (disposal == 3 && previous != null) {
                    canvas = copyOf(previous);
                }
            }

            CAPE_FRAME_DELAYS.put(fileName, delays);
        } finally {
            if (reader != null) reader.dispose();
        }

        return blocks;
    }

    private static int[] gifFrameInfo(IIOMetadata metadata) {
        int[] info = {0, 0, 1, 100};
        if (metadata == null) return info;

        String format = metadata.getNativeMetadataFormatName();
        if (format == null) return info;

        try {
            gifCollect(metadata.getAsTree(format), info);
        } catch (Exception ignored) {}
        return info;
    }

    private static void gifCollect(Node node, int[] info) {
        String name = node.getNodeName();
        if ("ImageDescriptor".equalsIgnoreCase(name)) {
            info[0] = gifAttr(node, "imageLeftPosition", info[0]);
            info[1] = gifAttr(node, "imageTopPosition", info[1]);
        } else if ("GraphicControlExtension".equalsIgnoreCase(name)) {
            info[2] = gifDisposal(node, info[2]);
            int delay = gifAttr(node, "delayTime", -1);
            if (delay >= 0) info[3] = delay * 10;
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            gifCollect(child, info);
        }
    }

    private static int gifAttr(Node node, String attribute, int fallback) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(attribute);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.getNodeValue().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int gifDisposal(Node node, int fallback) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("disposalMethod");
        if (value == null) return fallback;

        String mode = value.getNodeValue();
        if ("restoreToBackgroundColor".equalsIgnoreCase(mode)) return 2;
        if ("restoreToPrevious".equalsIgnoreCase(mode)) return 3;
        if ("none".equalsIgnoreCase(mode)) return 0;
        return fallback;
    }

    private static BufferedImage copyOf(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    public static void reloadRemoteCapes() {
        for (String key : new ArrayList<>(CAPE_TYPE_CACHE.keySet())) {
            if (key.startsWith("cache/")) {
                releaseSpecificCape(key);
            }
        }
    }

    private static int parseRequestedFrameCount(String fileName) {
        if (fileName == null || fileName.isBlank()) return 1;

        int at = fileName.lastIndexOf('@');
        if (at < 0 || at + 1 >= fileName.length()) return 1;

        int dot = fileName.indexOf('.', at);
        int hash = fileName.indexOf('#', at);
        if (hash >= 0 && (dot < 0 || hash < dot)) dot = hash;
        String num = dot >= 0 ? fileName.substring(at + 1, dot) : fileName.substring(at + 1);

        try {
            int n = Integer.parseInt(num);
            return n >= 2 ? n : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static int getCapeFrameCount(String fileName) {
        return parseRequestedFrameCount(fileName);
    }

    public static int getCapePresetFps(String fileName) {
        if (fileName == null || fileName.isBlank()) return -1;

        int atMark = fileName.lastIndexOf('@');
        int hashMark = fileName.lastIndexOf('#');
        if (atMark < 0 || hashMark < atMark || hashMark + 1 >= fileName.length()) return -1;

        int dot = fileName.indexOf('.', hashMark);
        String num = dot >= 0 ? fileName.substring(hashMark + 1, dot) : fileName.substring(hashMark + 1);

        try {
            int n = Integer.parseInt(num);
            return n >= 1 ? clampFps(n) : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static int clampFps(int fps) {
        return Math.max(1, Math.min(fps, IrcConfig.get().capeRenderMaxFps));
    }

    public static void refreshFrameTiming() {
        CAPE_LAST_FRAME_TIME.clear();
    }

    private static int resolvePlaybackFps(String capeName) {
        if (capeName == null || !capeName.equals(getMyCape())) {
            int preset = getCapePresetFps(capeName);
            if (preset > 0) return preset;
        }
        return clampFps(IrcConfig.get().capeAnimFps);
    }

    private static BufferedImage paintBlock(BufferedImage face, int block, int faceW, int faceH) {
        int u = block / 64;
        int width = block;
        int height = block / 2;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        g.drawImage(face, 1 * u, 0, 11 * u, 1 * u, 0, 0, faceW, 1, null);
        g.drawImage(face, 11 * u, 0, 21 * u, 1 * u, 0, faceH - 1, faceW, faceH, null);

        g.drawImage(face, 0, 1 * u, 1 * u, 17 * u, 0, 0, 1, faceH, null);              // 右侧厚度 (West)
        g.drawImage(face, 1 * u, 1 * u, 11 * u, 17 * u, 0, 0, faceW, faceH, null);      // 正面朝内 (North)
        g.drawImage(face, 11 * u, 1 * u, 12 * u, 17 * u, faceW - 1, 0, faceW, faceH, null); // 左侧厚度 (East)
        g.drawImage(face, 12 * u, 1 * u, 22 * u, 17 * u, 0, 0, faceW, faceH, null);     // 背面朝外 (South)

        g.dispose();
        return canvas;
    }

    private static BufferedImage downscaleSmooth(BufferedImage src, int tw, int th) {
        // 快速短路
        if (src.getWidth() == tw && src.getHeight() == th) {
            return src;
        }

        BufferedImage cur = src;

        // 如果目标是极小的像素 (如 10x16)，绝不要反复减半 8 次，最多减半到 4 倍尺寸即可，防止对比度被磨平！
        int minStepW = Math.max(tw * 2, 64);
        int minStepH = Math.max(th * 2, 64);

        while (cur.getWidth() > minStepW && cur.getHeight() > minStepH) {
            int nw = Math.max(minStepW, cur.getWidth() / 2);
            int nh = Math.max(minStepH, cur.getHeight() / 2);
            BufferedImage step = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = step.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            sg.drawImage(cur, 0, 0, nw, nh, null);
            sg.dispose();
            cur = step;
        }

        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = out.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        og.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        og.drawImage(cur, 0, 0, tw, th, null);
        og.dispose();
        return out;
    }

    private static String sanitizePath(String fileName) {
        if (fileName == null || fileName.isBlank()) return "unknown_cape";

        String clean = fileName.toLowerCase(Locale.ROOT);
        int dot = clean.lastIndexOf('.');
        if (dot > 0) {
            clean = clean.substring(0, dot);
        }

        clean = clean.replace('@', '_');
        clean = clean.replaceAll("[^a-z0-9._-]", "_");

        return clean.isEmpty() || clean.equals("_") ? "unknown_cape" : clean;
    }

    private static NativeImage toNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);

        int[] argbPixels = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, argbPixels, 0, width);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = argbPixels[y * width + x];
                nativeImage.setPixel(x, y, argb);
            }
        }

        return nativeImage;
    }
}