package me.cookie.kwrg.irc.manager;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import me.cookie.kwrg.irc.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class PlayerCustomizationManager {

    public static final float[] SCALE_STEPS = {0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f};

    private static final Map<String, RenderType> CAPE_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> CAPE_IDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<NativeImage>> CAPE_FRAMES_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, NativeImage> CAPE_ACTIVE_IMAGE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CAPE_LAST_FRAME_TIME = new ConcurrentHashMap<>();
    private static final Map<String, Integer> CAPE_CURRENT_FRAME_INDEX = new ConcurrentHashMap<>();

    private static volatile Object localRenderState = null;

    private static volatile Thread capeWorker = null;
    private static final BlockingQueue<Runnable> CAPE_TASK_QUEUE = new LinkedBlockingQueue<>();

    private static volatile String loadingCapeValue = null;
    private static volatile CompletableFuture<List<NativeImage>> loadingFuture = null;

    public static float getMyScale() { return IrcConfig.get().myScale; }
    public static void setMyScale(float scale) { IrcConfig.get().myScale = scale; IrcConfig.save(); }
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
                        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String getMyCape() {
        String cape = IrcConfig.get().myCape;
        return (cape != null && !cape.isBlank()) ? cape : null;
    }

    public static void setMyCape(String fileName) {
        IrcConfig.get().myCape = (fileName != null && !fileName.isBlank()) ? fileName : "";
        IrcConfig.save();
        triggerAsyncLoad(IrcConfig.get().myCape);
    }

    public static void clearCapeCache() {
        Minecraft mc = Minecraft.getInstance();
        for (Identifier id : CAPE_IDS_CACHE.values()) {
            mc.getTextureManager().release(id);
        }
        for (List<NativeImage> frames : CAPE_FRAMES_CACHE.values()) {
            for (NativeImage img : frames) img.close();
        }
        for (NativeImage img : CAPE_ACTIVE_IMAGE.values()) img.close();

        CAPE_TYPE_CACHE.clear();
        CAPE_IDS_CACHE.clear();
        CAPE_FRAMES_CACHE.clear();
        CAPE_ACTIVE_IMAGE.clear();
        CAPE_LAST_FRAME_TIME.clear();
        CAPE_CURRENT_FRAME_INDEX.clear();
    }

    private static void ensureCapeWorkerStarted() {
        if (capeWorker != null) return;
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Runnable task = CAPE_TASK_QUEUE.take();
                    task.run();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {}
            }
        }, "kwrg-cape-loader");
        t.setDaemon(true);
        capeWorker = t;
        t.start();
    }

    private static void triggerAsyncLoad(String value) {
        ensureCapeWorkerStarted();
        clearCapeCache();

        if (loadingFuture != null) loadingFuture.cancel(true);

        if (value == null || value.isBlank()) {
            loadingCapeValue = null;
            loadingFuture = null;
            return;
        }

        loadingCapeValue = value;
        CompletableFuture<List<NativeImage>> future = new CompletableFuture<>();
        loadingFuture = future;

        CAPE_TASK_QUEUE.offer(() -> {
            try {
                Path file = getCapeDir().resolve(value);
                if (!Files.exists(file)) { future.complete(null); return; }
                BufferedImage src = ImageIO.read(file.toFile());
                if (src == null) { future.complete(null); return; }
                List<BufferedImage> blocks = layoutFrames(src, value);
                if (blocks == null || blocks.isEmpty()) { future.complete(null); return; }

                List<NativeImage> frames = new ArrayList<>();
                for (BufferedImage block : blocks) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(block, "png", baos);
                    frames.add(NativeImage.read(new ByteArrayInputStream(baos.toByteArray())));
                }
                future.complete(frames);
            } catch (Exception e) {
                future.complete(null);
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

        if (!value.equals(loadingCapeValue) || loadingFuture == null) {
            triggerAsyncLoad(value);
            return null;
        }

        if (!loadingFuture.isDone()) return null;

        try {
            List<NativeImage> frames = loadingFuture.get();
            loadingFuture = null;

            if (frames == null || frames.isEmpty()) return null;

            Minecraft mc = Minecraft.getInstance();
            GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            Identifier id = Identifier.fromNamespaceAndPath("kwrg", sanitizePath(value));

            NativeImage firstFrame = frames.get(0);
            NativeImage activeImage = new NativeImage(firstFrame.getWidth(), firstFrame.getHeight(), false);
            activeImage.copyFrom(firstFrame);

            DynamicTexture tex = new DynamicTexture(() -> "kwrg_cape", activeImage);
            mc.getTextureManager().register(id, tex);

            RenderType out = RenderType.create("kwrg_cape", RenderSetup.builder(RenderPipelines.ENTITY_SOLID)
                    .withTexture("Sampler0", id, () -> linearSampler)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup());

            CAPE_FRAMES_CACHE.put(value, frames);
            CAPE_ACTIVE_IMAGE.put(value, activeImage);
            CAPE_TYPE_CACHE.put(value, out);
            CAPE_IDS_CACHE.put(value, id);
            CAPE_CURRENT_FRAME_INDEX.put(value, 0);
            CAPE_LAST_FRAME_TIME.put(value, System.currentTimeMillis());

            return out;
        } catch (Exception e) {
            loadingFuture = null;
            return null;
        }
    }

    private static void updateFrameIfNeeded(String value) {
        List<NativeImage> frames = CAPE_FRAMES_CACHE.get(value);
        if (frames == null || frames.size() <= 1) return;

        int fps = IrcConfig.get().capeAnimFps;
        long interval = fps > 0 ? 1000L / fps : 100L;
        long now = System.currentTimeMillis();
        long lastTime = CAPE_LAST_FRAME_TIME.getOrDefault(value, 0L);

        if (now - lastTime >= interval) {
            int currentIndex = CAPE_CURRENT_FRAME_INDEX.getOrDefault(value, 0);
            int nextIndex = (currentIndex + 1) % frames.size();

            NativeImage activeImage = CAPE_ACTIVE_IMAGE.get(value);
            Identifier id = CAPE_IDS_CACHE.get(value);
            if (activeImage != null && id != null) {
                activeImage.copyFrom(frames.get(nextIndex));

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
        int block = IrcConfig.get().capeCanvasSize;
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        int faceW = block * 10 / 64;
        int faceH = block * 32 / 64;

        int requestedFrames = parseRequestedFrameCount(fileName);
        if (requestedFrames >= 2 && srcW % requestedFrames == 0) {
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

    private static int parseRequestedFrameCount(String fileName) {
        if (fileName == null || fileName.isBlank()) return 1;

        int at = fileName.lastIndexOf('@');
        if (at < 0 || at + 1 >= fileName.length()) return 1;

        int dot = fileName.indexOf('.', at);
        String num = dot >= 0 ? fileName.substring(at + 1, dot) : fileName.substring(at + 1);

        try {
            int n = Integer.parseInt(num);
            return n >= 2 ? n : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static BufferedImage paintBlock(BufferedImage face, int block, int faceW, int faceH) {
        int u = block / 64;

        BufferedImage canvas = new BufferedImage(block, block, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        g.drawImage(face, 1 * u, 0, 11 * u, 2 * u, 0, 0, faceW, 1, null);
        g.drawImage(face, 11 * u, 0, 21 * u, 2 * u, 0, faceH - 1, faceW, faceH, null);

        g.drawImage(face, 0, 2 * u, 1 * u, 34 * u, 0, 0, 1, faceH, null);
        g.drawImage(face, 1 * u, 2 * u, 11 * u, 34 * u, 0, 0, faceW, faceH, null);
        g.drawImage(face, 11 * u, 2 * u, 12 * u, 34 * u, faceW - 1, 0, faceW, faceH, null);
        g.drawImage(face, 12 * u, 2 * u, 22 * u, 34 * u, 0, 0, faceW, faceH, null);
        g.drawImage(face, 21 * u, 2 * u, 22 * u, 34 * u, faceW - 1, 0, faceW, faceH, null);
        g.drawImage(face, 22 * u, 2 * u, block, 34 * u, faceW - 1, 0, faceW, faceH, null);

        g.drawImage(face, 0, 0, 1 * u, 2 * u, 0, 0, 1, 1, null);
        g.drawImage(face, 21 * u, 0, block, 2 * u, faceW - 1, faceH - 1, faceW, faceH, null);

        g.drawImage(face, 0, 34 * u, 1 * u, block, 0, faceH - 1, 1, faceH, null);
        g.drawImage(face, 1 * u, 34 * u, 11 * u, block, 0, faceH - 1, faceW, faceH, null);
        g.drawImage(face, 11 * u, 34 * u, 12 * u, block, faceW - 1, faceH - 1, faceW, faceH, null);
        g.drawImage(face, 12 * u, 34 * u, 22 * u, block, 0, faceH - 1, faceW, faceH, null);
        g.drawImage(face, 22 * u, 34 * u, block, block, faceW - 1, faceH - 1, faceW, faceH, null);

        g.dispose();
        return canvas;
    }

    private static BufferedImage downscaleSmooth(BufferedImage src, int tw, int th) {
        BufferedImage cur = src;
        while (cur.getWidth() > tw * 2 && cur.getHeight() > th * 2) {
            int nw = Math.max(tw, cur.getWidth() / 2);
            int nh = Math.max(th, cur.getHeight() / 2);
            BufferedImage step = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = step.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            sg.drawImage(cur, 0, 0, nw, nh, null);
            sg.dispose();
            cur = step;
        }
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = out.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        og.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
}