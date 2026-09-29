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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class PlayerCustomizationManager {

    public static final float[] SCALE_STEPS = {0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f};

    private static final Map<String, RenderType[]> CAPE_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Identifier[]> CAPE_IDS_CACHE = new ConcurrentHashMap<>();
    private static volatile Object localRenderState = null;

    private static final ExecutorService CAPE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kwrg-cape-loader");
        t.setDaemon(true);
        return t;
    });
    private static volatile String loadingCapeValue = null;
    private static volatile CompletableFuture<List<byte[]>> loadingFuture = null;

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
        for (Identifier[] ids : CAPE_IDS_CACHE.values()) {
            if (ids != null) {
                for (Identifier id : ids) {
                    mc.getTextureManager().release(id);
                }
            }
        }
        CAPE_TYPE_CACHE.clear();
        CAPE_IDS_CACHE.clear();
    }

    private static void triggerAsyncLoad(String value) {
        Minecraft mc = Minecraft.getInstance();

        for (Identifier[] ids : CAPE_IDS_CACHE.values()) {
            if (ids != null) {
                for (Identifier id : ids) {
                    mc.getTextureManager().release(id);
                }
            }
        }
        CAPE_TYPE_CACHE.clear();
        CAPE_IDS_CACHE.clear();

        if (loadingFuture != null) loadingFuture.cancel(true);

        if (value == null || value.isBlank()) {
            loadingCapeValue = null;
            loadingFuture = null;
            return;
        }

        loadingCapeValue = value;
        loadingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Path file = getCapeDir().resolve(value);
                if (!Files.exists(file)) return null;
                BufferedImage src = ImageIO.read(file.toFile());
                if (src == null) return null;
                List<BufferedImage> blocks = layoutFrames(src);
                if (blocks == null || blocks.isEmpty()) return null;

                List<byte[]> pngBytes = new ArrayList<>();
                for (BufferedImage block : blocks) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(block, "png", baos);
                    pngBytes.add(baos.toByteArray());
                }
                return pngBytes;
            } catch (Exception e) {
                return null;
            }
        }, CAPE_EXECUTOR);
    }

    public static RenderType resolveMyCapeRenderType() {
        String value = getMyCape();
        if (value == null) return null;

        RenderType[] arr = CAPE_TYPE_CACHE.get(value);
        if (arr != null) {
            int fps = IrcConfig.get().capeAnimFps;
            long interval = fps > 0 ? 1000L / fps : 100L;
            return arr.length == 1 ? arr[0] : arr[(int) ((System.currentTimeMillis() / interval) % arr.length)];
        }

        if (!value.equals(loadingCapeValue) || loadingFuture == null) {
            triggerAsyncLoad(value);
            return null;
        }

        if (!loadingFuture.isDone()) return null;

        try {
            List<byte[]> pngBytes = loadingFuture.get();
            loadingFuture = null;

            if (pngBytes == null || pngBytes.isEmpty()) return null;

            Minecraft mc = Minecraft.getInstance();
            GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            RenderType[] out = new RenderType[pngBytes.size()];
            Identifier[] ids = new Identifier[pngBytes.size()];
            String base = sanitizePath(value);

            for (int i = 0; i < pngBytes.size(); i++) {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(pngBytes.get(i)));
                Identifier id = Identifier.fromNamespaceAndPath("kwrg", base + (out.length > 1 ? "_f" + i : ""));
                ids[i] = id;
                DynamicTexture tex = new DynamicTexture(() -> "kwrg_cape", img);
                mc.getTextureManager().register(id, tex);

                out[i] = RenderType.create("kwrg_cape", RenderSetup.builder(RenderPipelines.ENTITY_SOLID)
                        .withTexture("Sampler0", id, () -> linearSampler)
                        .useLightmap()
                        .useOverlay()
                        .affectsCrumbling()
                        .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                        .createRenderSetup());
            }
            CAPE_TYPE_CACHE.put(value, out);
            CAPE_IDS_CACHE.put(value, ids);

            int fps = IrcConfig.get().capeAnimFps;
            long interval = fps > 0 ? 1000L / fps : 100L;
            return out.length == 1 ? out[0] : out[(int) ((System.currentTimeMillis() / interval) % out.length)];
        } catch (Exception e) {
            loadingFuture = null;
            return null;
        }
    }

    private static List<BufferedImage> layoutFrames(BufferedImage src) {
        List<BufferedImage> blocks = new ArrayList<>();
        int block = IrcConfig.get().capeCanvasSize;
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        if (srcH == block && srcW % block == 0) {
            for (int x = 0; x < srcW; x += block) blocks.add(src.getSubimage(x, 0, block, block));
            return blocks;
        }

        int faceW = block * 10 / 64;
        int faceH = block * 16 / 64;

        int n = 1;
        double cand = (double) srcW * faceH / ((double) srcH * faceW);
        int ni = (int) Math.round(cand);
        if (ni >= 2 && Math.abs(cand - ni) < 0.05) n = ni;

        int fw = srcW / n;
        for (int k = 0; k < n; k++) {
            BufferedImage frame = src.getSubimage(k * fw, 0, fw, srcH);
            blocks.add(paintBlock(downscaleSmooth(frame, faceW, faceH), block, faceW, faceH));
        }
        return blocks;
    }

    private static BufferedImage paintBlock(BufferedImage face, int block, int faceW, int faceH) {
        int frontX = block / 64;
        int frontY = block / 64;
        int backX = block * 12 / 64;
        int backY = block / 64;

        BufferedImage canvas = new BufferedImage(block, block, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        g.drawImage(face, frontX, frontY, null);
        g.drawImage(face, backX, backY, null);

        g.drawImage(face, frontX, 0, frontX + faceW, frontY, 0, 0, faceW, 1, null);
        g.drawImage(face, frontX + faceW, 0, frontX + faceW + faceW, frontY, 0, faceH - 1, faceW, faceH, null);
        g.drawImage(face, frontX, frontY + faceH, frontX + faceW, block, 0, faceH - 1, faceW, faceH, null);
        g.drawImage(face, backX, frontY + faceH, backX + faceW, block, 0, faceH - 1, faceW, faceH, null);
        g.drawImage(face, 0, frontY, frontX, frontY + faceH, 0, 0, 1, faceH, null);
        g.drawImage(face, frontX + faceW, frontY, backX, frontY + faceH, faceW - 1, 0, faceW, faceH, null);
        g.drawImage(face, backX + faceW, frontY, block, frontY + faceH, faceW - 1, 0, faceW, faceH, null);

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
        if (clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg")) {
            clean = clean.substring(0, clean.lastIndexOf('.'));
        }
        clean = clean.replaceAll("[^a-z0-9._-]", "_");
        return clean.isEmpty() || clean.equals("_") ? "unknown_cape" : clean;
    }
}