package me.cookie.kwrg.irc.manager;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.config.IrcConfig;
import me.cookie.kwrg.mixin.accessor.NativeImageAccessor;
import me.cookie.kwrg.util.SafePaths;
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
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class PlayerCustomizationManager {

    public static final float[] SCALE_STEPS = {0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f};

    public static final int CAPE_MAX_FPS_LIMIT = 60;

    private static final int MAX_GIF_FRAMES = 96;

    public static volatile boolean inInventoryRendering = false;

    private static volatile Object localRenderState = null;

    public static final int CAPE_PREVIEW_W = 40;
    public static final int CAPE_PREVIEW_H = 64;

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

    public static float getMyScale() { return IrcConfig.get().myScale; }

    public static Identifier peekCapePreview(String fileName) {
        CapeCache.CapePreview p = CapeCache.previews().get(fileName);
        return p == null ? null : p.id();
    }

    public static Identifier getPlayerElytraTexture(Player player) {
        Minecraft mc = Minecraft.getInstance();
        String capeName = (player == mc.player) ? getMyCape() : CapeCache.playerCapes().get(capeKey(player.getGameProfile().name()));
        if (capeName == null || capeName.isBlank()) return null;

        if (!Boolean.TRUE.equals(CapeCache.hasElytra().get(capeName))) {
            return null;
        }

        Identifier id = CapeCache.capeIds().get(capeName);
        if (id != null) {
            updateFrameIfNeeded(capeName);
            return id;
        }

        triggerAsyncLoad(capeName);
        return null;
    }


    public static void requestCapePreview(String fileName) {
        if (fileName == null || fileName.isBlank()) return;

        Path file = resolveCapeFile(fileName);
        if (file == null) return;
        long modified;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException e) {
            return;
        }

        CapeCache.CapePreview cached = CapeCache.previews().get(fileName);
        if (cached != null && cached.lastModified() == modified) return;
        if (!CapeCache.previewLoading().add(fileName)) return;

        PREVIEW_EXECUTOR.submit(() -> {
            NativeImage img = null;
            boolean handedOver = false;
            try {
                BufferedImage src = ImageIO.read(file.toFile());
                if (src == null) {
                    return;
                }

                int frames = "gif".equals(CapeImagePipeline.capeMagic(file)) ? 1 : CapeImagePipeline.parseRequestedFrameCount(fileName);
                if (frames >= 2 && src.getWidth() >= frames) {
                    src = src.getSubimage(0, 0, src.getWidth() / frames, src.getHeight());
                }

                BufferedImage previewSrc;
                if (CapeImagePipeline.isStandardCape(src.getWidth(), src.getHeight())) {
                    int u = src.getWidth() / 64;
                    previewSrc = src.getSubimage(12 * u, 1 * u, 10 * u, 16 * u);
                } else {
                    previewSrc = src;
                }

                BufferedImage scaledPreview = new BufferedImage(CAPE_PREVIEW_W, CAPE_PREVIEW_H, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaledPreview.createGraphics();
                if (previewSrc.getWidth() < CAPE_PREVIEW_W) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                } else {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                }
                g.drawImage(previewSrc, 0, 0, CAPE_PREVIEW_W, CAPE_PREVIEW_H, null);
                g.dispose();

                img = CapeImagePipeline.toNativeImage(scaledPreview);
                final NativeImage finalImg = img;

                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        CapeCache.CapePreview old = CapeCache.previews().get(fileName);
                        if (old != null) mc.getTextureManager().release(old.id());

                        Identifier id = Identifier.fromNamespaceAndPath("kwrg",
                                "cape_preview/" + CapeImagePipeline.sanitizePath(fileName) + "_" + Integer.toHexString(fileName.hashCode()));
                        mc.getTextureManager().register(id,
                                new DynamicTexture(() -> "kwrg_cape_preview", finalImg));
                        CapeCache.previews().put(fileName, new CapeCache.CapePreview(id, modified));
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        finalImg.close();
                    } finally {
                        CapeCache.previewLoading().remove(fileName);
                    }
                });
                handedOver = true;
            } catch (IOException | RuntimeException e) {
                System.err.println("[KWRG Preview] load failed: " + fileName);
                e.printStackTrace();
            } finally {
                if (!handedOver) {
                    if (img != null) img.close();
                    CapeCache.previewLoading().remove(fileName);
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
        CapeCache.releasePreviews();
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
            CapeCache.remoteScales().put(key, scale);
        } else {
            CapeCache.remoteScales().remove(key);
        }
    }

    public static float getPlayerScale(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            return getMyScale();
        }
        String name = player.getGameProfile().name().toLowerCase(Locale.ROOT);
        return CapeCache.remoteScales().getOrDefault(name, 1.0f);
    }

    public static void clearRemotePlayerScales() {
        CapeCache.remoteScales().clear();
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

    private static Path resolveCapeFile(String value) {
        if (value == null || value.isBlank()) return null;
        Path base = getCapeDir();
        String name = value;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            if (!"cache".equals(name.substring(0, slash))) return null;
            base = base.resolve("cache");
            name = name.substring(slash + 1);
        }
        return SafePaths.resolveInside(base, name);
    }

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
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
    }

    public static void setIrcPlayerCustomization(String username, String capeFile, float scale) {
        String key = capeKey(username);
        if (key.isEmpty()) return;

        if (capeFile != null && !capeFile.isBlank()) {
            CapeCache.playerCapes().put(key, capeFile);

            boolean isNearby = CapeCache.activeUsers().values().stream().anyMatch(set -> set.contains(key));
            if (isNearby) {
                bindPlayerCape(key, capeFile);
            }
        } else {
            CapeCache.playerCapes().remove(key);
            unbindPlayer(key);
        }

        if (scale > 0.1f && scale <= 5.0f) {
            CapeCache.playerScales().put(key, scale);
        } else {
            CapeCache.playerScales().remove(key);
        }
    }

    public static void bindPlayerCape(String username, String newCape) {
        String key = capeKey(username);
        if (key.isEmpty()) return;

        String oldCape = null;
        for (Map.Entry<String, Set<String>> entry : CapeCache.activeUsers().entrySet()) {
            if (entry.getValue().remove(key)) {
                oldCape = entry.getKey();
                if (entry.getValue().isEmpty()) {
                    CapeCache.activeUsers().remove(oldCape);
                    releaseSpecificCape(oldCape);
                }
                break;
            }
        }

        if (newCape != null && !newCape.isBlank()) {
            CapeCache.activeUsers().computeIfAbsent(newCape, k -> ConcurrentHashMap.newKeySet()).add(key);
            triggerAsyncLoad(newCape);
        }
    }

    public static void preloadIrcPlayer(String username) {
        String capeName = CapeCache.playerCapes().get(capeKey(username));
        if (capeName != null && !capeName.isBlank()) {
            bindPlayerCape(username, capeName);
        }
    }


    public static void unbindPlayer(String username) {
        bindPlayerCape(username, null);
    }

    public static void removeIrcPlayer(String username) {
        String key = capeKey(username);
        CapeCache.playerCapes().remove(key);
        CapeCache.playerScales().remove(key);
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
            capeName = CapeCache.playerCapes().get(capeKey(player.getGameProfile().name()));
        }

        if (capeName == null || capeName.isBlank()) return null;

        RenderType type = CapeCache.capeTypes().get(capeName);
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
        } else {
            if (!target.equals(loadingCapeName)) {
                loadingCapeName = null;
            }
            triggerAsyncLoad(target);
            IrcCapeManager.syncMyCape();
        }
    }

    public static void clearCapeCache() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(PlayerCustomizationManager::clearCapeCache);
            return;
        }

        CapeCache.clearAll();

        activeCapeName = null;
        loadingCapeName = null;
    }

    private static void releaseSpecificCape(String capeName) {
        CapeCache.releaseCape(capeName);
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

        if (!force && value.equals(activeCapeName) && CapeCache.capeTypes().containsKey(value)) {
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
                Path file = resolveCapeFile(value);
                if (file == null || !Files.exists(file) || token != CURRENT_LOAD_TOKEN.get()) {
                    cleanupLoading(token);
                    return;
                }

                String magic = CapeImagePipeline.capeMagic(file);
                boolean gifSource = "gif".equals(magic);
                String extension = CapeImagePipeline.capeExtension(value);
                if (!CapeImagePipeline.magicMatchesExtension(magic, extension)) {
                    System.err.println("[KWRG Cape] " + value + " extension=" + extension + " content=" + magic);
                }

                BufferedImage src = gifSource ? null : ImageIO.read(file.toFile());
                if ((!gifSource && src == null) || token != CURRENT_LOAD_TOKEN.get()) {
                    cleanupLoading(token);
                    return;
                }

                boolean hasElytra = false;
                if (!gifSource) {
                    int requestedFrames = CapeImagePipeline.parseRequestedFrameCount(value);
                    BufferedImage firstRawFrame = (requestedFrames >= 2 && src.getWidth() >= requestedFrames)
                            ? src.getSubimage(0, 0, src.getWidth() / requestedFrames, src.getHeight())
                            : src;
                    hasElytra = CapeImagePipeline.checkHasElytra(firstRawFrame);
                }


                List<BufferedImage> blocks;
                if (gifSource) {
                    blocks = layoutGifFrames(file, value);
                    if (blocks == null || blocks.isEmpty()) {
                        BufferedImage first = ImageIO.read(file.toFile());
                        blocks = first == null ? null : layoutFrames(first, CapeImagePipeline.plainCapeName(value));
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
                    frames.add(CapeImagePipeline.toNativeImage(block));
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

                final boolean finalHasElytra = hasElytra;
                final NativeImage finalActive = activeImage;
                Minecraft.getInstance().execute(() -> {
                    try {
                        if (token != CURRENT_LOAD_TOKEN.get()) {
                            if (finalActive != frames.get(0)) finalActive.close();
                            frames.forEach(NativeImage::close);
                            return;
                        }

                        Minecraft mc = Minecraft.getInstance();
                        Identifier id = Identifier.fromNamespaceAndPath("kwrg", CapeImagePipeline.sanitizePath(value));

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
                        if (finalHasElytra) {
                            RenderType elytraType = RenderType.create("kwrg_elytra", RenderSetup.builder(RenderPipelines.ARMOR_CUTOUT_NO_CULL)
                                    .withTexture("Sampler0", id, () -> sampler)
                                    .useLightmap()
                                    .useOverlay()
                                    .affectsCrumbling()
                                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                                    .createRenderSetup());
                            CapeCache.elytraTypes().put(value, elytraType);
                        } else {
                            CapeCache.elytraTypes().remove(value);
                        }
                        CapeCache.hasElytra().put(value, finalHasElytra);
                        List<NativeImage> prevFrames = CapeCache.capeFrames().remove(value);
                        NativeImage prevActive = CapeCache.activeImages().remove(value);
                        if (prevActive != null && (prevFrames == null || !prevFrames.contains(prevActive))) {
                            prevActive.close();
                        }
                        if (prevFrames != null) {
                            for (NativeImage img : prevFrames) img.close();
                        }

                        CapeCache.capeFrames().put(value, frames);
                        CapeCache.activeImages().put(value, finalActive);
                        CapeCache.capeTypes().put(value, out);
                        CapeCache.capeIds().put(value, id);
                        CapeCache.currentFrameIndexes().put(value, 0);
                        CapeCache.lastFrameTimes().put(value, System.currentTimeMillis());

                        activeCapeName = value;
                    } catch (RuntimeException e) {
                        if (finalActive != frames.get(0)) finalActive.close();
                        frames.forEach(NativeImage::close);
                    } finally {
                        cleanupLoading(token);
                    }
                });

            } catch (IOException | RuntimeException e) {
                if (activeImage != null && (frames.isEmpty() || activeImage != frames.get(0))) activeImage.close();
                frames.forEach(NativeImage::close);
                cleanupLoading(token);
            }
        });
    }

    public static RenderType resolveMyCapeRenderType() {
        String value = getMyCape();
        if (value == null) return null;

        RenderType type = CapeCache.capeTypes().get(value);
        if (type != null) {
            updateFrameIfNeeded(value);
            return type;
        }

        if (activeCapeName != null) {
            RenderType oldType = CapeCache.capeTypes().get(activeCapeName);
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
        List<NativeImage> frames = CapeCache.capeFrames().get(value);
        if (frames == null || frames.size() <= 1) return;

        int fps = resolvePlaybackFps(value);
        int currentIndex = CapeCache.currentFrameIndexes().getOrDefault(value, 0);
        int[] delays = CapeCache.frameDelays().get(value);

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
        long lastTime = CapeCache.lastFrameTimes().getOrDefault(value, 0L);

        if (now - lastTime >= interval) {
            int nextIndex = (currentIndex + 1) % frames.size();

            NativeImage activeImage = CapeCache.activeImages().get(value);
            Identifier id = CapeCache.capeIds().get(value);
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

                CapeCache.currentFrameIndexes().put(value, nextIndex);
                CapeCache.lastFrameTimes().put(value, now);
            }
        }
    }

    private static List<BufferedImage> layoutFrames(BufferedImage src, String fileName) {
        List<BufferedImage> blocks = new ArrayList<>();

        boolean isRemote = fileName != null && fileName.startsWith("cache/");
        int block = isRemote ? IrcConfig.get().remoteCapeCanvasSize : IrcConfig.get().capeCanvasSize;

        int srcW = src.getWidth();
        int srcH = src.getHeight();

        int requestedFrames = CapeImagePipeline.parseRequestedFrameCount(fileName);
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
                blocks.add(CapeImagePipeline.processCapeFrame(frame, block, faceW, faceH));
            }
            return blocks;
        }

        blocks.add(CapeImagePipeline.processCapeFrame(src, block, faceW, faceH));
        return blocks;
    }

    private static List<BufferedImage> layoutGifFrames(Path file, String fileName) throws IOException {
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

            int emitEvery = Math.max(1, (count + MAX_GIF_FRAMES - 1) / MAX_GIF_FRAMES);
            int emittedCount = (count + emitEvery - 1) / emitEvery;
            int[] delays = new int[emittedCount];
            long accumulatedDelay = 0;
            int emitted = 0;

            BufferedImage canvas = new BufferedImage(reader.getWidth(0), reader.getHeight(0), BufferedImage.TYPE_INT_ARGB);
            BufferedImage previous = null;

            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                int[] info = CapeImagePipeline.gifFrameInfo(reader.getImageMetadata(i));
                int left = info[0];
                int top = info[1];
                int disposal = info[2];
                accumulatedDelay += Math.max(10, info[3]);

                if (disposal == 3) previous = CapeImagePipeline.copyOf(canvas);

                boolean fullSize = frame.getWidth() == canvas.getWidth() && frame.getHeight() == canvas.getHeight();
                int x = fullSize ? 0 : left;
                int y = fullSize ? 0 : top;

                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, x, y, null);
                g.dispose();

                if (emitted < emittedCount && ((i + 1) % emitEvery == 0 || i == count - 1)) {
                    delays[emitted++] = (int) Math.min(Integer.MAX_VALUE, accumulatedDelay);
                    accumulatedDelay = 0;
                    blocks.add(CapeImagePipeline.processCapeFrame(CapeImagePipeline.copyOf(canvas), block, faceW, faceH));
                }

                if (disposal == 2) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
                    clear.dispose();
                } else if (disposal == 3 && previous != null) {
                    canvas = CapeImagePipeline.copyOf(previous);
                }
            }

            CapeCache.frameDelays().put(fileName, delays);

            long delaySum = 0;
            for (int delay : delays) delaySum += delay;
            if (delays.length > 0 && delaySum > 0) {
                CapeCache.nativeFps().put(fileName, clampFps((int) Math.max(1L, 1000L * delays.length / delaySum)));
            }
        } finally {
            if (reader != null) reader.dispose();
        }

        return blocks;
    }

    public static void reloadRemoteCapes() {
        for (String key : new ArrayList<>(CapeCache.capeTypes().keySet())) {
            if (key.startsWith("cache/")) {
                releaseSpecificCape(key);
            }
        }
    }

    public static int getCapeFrameCount(String fileName) {
        return CapeImagePipeline.parseRequestedFrameCount(fileName);
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
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    public static int clampFps(int fps) {
        return Math.max(1, Math.min(fps, IrcConfig.get().capeRenderMaxFps));
    }

    public static void refreshFrameTiming() {
        CapeCache.lastFrameTimes().clear();
    }

    private static int resolvePlaybackFps(String capeName) {
        int preset = getCapePresetFps(capeName);
        if (preset > 0) return preset;

        if (capeName != null) {
            Integer nativeFps = CapeCache.nativeFps().get(capeName);
            if (nativeFps != null) return nativeFps;
        }
        return clampFps(IrcConfig.get().capeAnimFps);
    }

}