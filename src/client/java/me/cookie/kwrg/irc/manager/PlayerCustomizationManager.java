package me.cookie.kwrg.irc.manager;

import com.mojang.blaze3d.platform.NativeImage;
import me.cookie.kwrg.irc.IrcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCustomizationManager {

    public static final float[] SCALE_STEPS = {0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f};

    private static final int TEX_SIZE = 64;
    private static final int FRONT_X = 1, FRONT_Y = 1;
    private static final int FACE_W = 10, FACE_H = 32;
    private static final int BACK_X = 12, BACK_Y = 1;

    private static final Map<String, Identifier> CAPE_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static volatile Object localRenderState = null;

    public static float getMyScale() {
        return IrcConfig.get().myScale;
    }

    public static void setMyScale(float scale) {
        IrcConfig.get().myScale = scale;
        IrcConfig.save();
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

    public static void markLocalRenderState(Object state) {
        localRenderState = state;
    }

    public static boolean isLocalRenderState(Object state) {
        return state != null && state == localRenderState;
    }

    public static Path getCapeDir() {
        return IrcConfig.getKwrgDir().resolve("capes");
    }

    public static List<String> getAvailableCapes() {
        List<String> list = new ArrayList<>();
        Path dir = getCapeDir();
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{png,jpg,jpeg,PNG,JPG,JPEG}")) {
                for (Path p : stream) {
                    list.add(p.getFileName().toString());
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public static String getMyCape() {
        String cape = IrcConfig.get().myCape;
        return (cape != null && !cape.isBlank()) ? cape : null;
    }

    public static void setMyCape(String fileName) {
        IrcConfig.get().myCape = (fileName != null && !fileName.isBlank()) ? fileName : "";
        IrcConfig.save();
        CAPE_TEXTURE_CACHE.clear();
    }

    public static String cycleMyCape() {
        List<String> available = getAvailableCapes();
        String current = getMyCape();
        String next;
        if (current == null) {
            next = available.isEmpty() ? null : available.get(0);
        } else {
            int idx = -1;
            for (int i = 0; i < available.size(); i++) {
                if (available.get(i).equalsIgnoreCase(current)) {
                    idx = i;
                    break;
                }
            }
            next = (idx == -1 || idx + 1 >= available.size()) ? null : available.get(idx + 1);
        }
        setMyCape(next);
        return next;
    }

    public static Identifier resolveMyCapeTexture() {
        String fileName = getMyCape();
        if (fileName == null) return null;
        Identifier cached = CAPE_TEXTURE_CACHE.get(fileName);
        if (cached != null) return cached;
        Path file = getCapeDir().resolve(fileName);
        if (!Files.exists(file)) return null;

        NativeImage image = buildCapeTexture(file);
        if (image == null) return null;

        Minecraft mc = Minecraft.getInstance();
        try {
            DynamicTexture tex = new DynamicTexture(() -> "kwrg_cape", image);
            Identifier id = Identifier.fromNamespaceAndPath("kwrg", sanitizePath(fileName));
            mc.getTextureManager().register(id, tex);
            CAPE_TEXTURE_CACHE.put(fileName, id);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    private static NativeImage buildCapeTexture(Path file) {
        try {
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) return null;

            if (src.getWidth() == TEX_SIZE && src.getHeight() == TEX_SIZE) {
                ByteArrayOutputStream baos0 = new ByteArrayOutputStream();
                ImageIO.write(src, "png", baos0);
                return NativeImage.read(new ByteArrayInputStream(baos0.toByteArray()));
            }

            BufferedImage canvas = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(src, FRONT_X, FRONT_Y, FRONT_X + FACE_W, FRONT_Y + FACE_H, null);
            g.drawImage(src, BACK_X, BACK_Y, BACK_X + FACE_W, BACK_Y + FACE_H, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(canvas, "png", baos);
            return NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitizePath(String fileName) {
        String clean = fileName.toLowerCase(Locale.ROOT);
        if (clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg")) {
            clean = clean.substring(0, clean.lastIndexOf('.'));
        }
        return clean.replaceAll("[^a-z0-9._-]", "_");
    }
}