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

    private static final String BUILTIN_PREFIX = "builtin:";

    public static final List<String[]> BUILTIN_PRESETS = List.of(
            new String[]{"sunset", "日落渐变"},
            new String[]{"ocean", "海洋渐变"},
            new String[]{"fire", "烈焰渐变"},
            new String[]{"rainbow", "彩虹"},
            new String[]{"stars", "星空"},
            new String[]{"heart", "像素心"},
            new String[]{"creeper", "苦力怕"},
            new String[]{"lightning", "闪电"},
            new String[]{"stripes", "条纹"},
            new String[]{"checker", "棋盘"}
    );

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

    public static boolean isBuiltin(String value) {
        return value != null && value.startsWith(BUILTIN_PREFIX);
    }

    public static String builtinId(String value) {
        return isBuiltin(value) ? value.substring(BUILTIN_PREFIX.length()) : null;
    }

    public static String displayCapeName(String value) {
        if (value == null || value.isBlank()) return "无 (原版)";
        if (isBuiltin(value)) {
            String id = builtinId(value);
            for (String[] p : BUILTIN_PRESETS) {
                if (p[0].equals(id)) return p[1];
            }
            return "内置预设";
        }
        return value;
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

    public static Identifier resolveMyCapeTexture() {
        String value = getMyCape();
        if (value == null) return null;
        Identifier cached = CAPE_TEXTURE_CACHE.get(value);
        if (cached != null) return cached;

        NativeImage image;
        if (isBuiltin(value)) {
            image = buildBuiltinCapeTexture(builtinId(value));
        } else {
            Path file = getCapeDir().resolve(value);
            if (!Files.exists(file)) return null;
            image = buildCapeTexture(file);
        }
        if (image == null) return null;

        Minecraft mc = Minecraft.getInstance();
        try {
            DynamicTexture tex = new DynamicTexture(() -> "kwrg_cape", image);
            Identifier id = Identifier.fromNamespaceAndPath("kwrg", sanitizePath(value));
            mc.getTextureManager().register(id, tex);
            CAPE_TEXTURE_CACHE.put(value, id);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    private static NativeImage buildBuiltinCapeTexture(String presetId) {
        BufferedImage face = renderBuiltinFace(presetId);
        if (face == null) return null;
        try {
            BufferedImage canvas = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(face, FRONT_X, FRONT_Y, null);
            g.drawImage(face, BACK_X, BACK_Y, null);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(canvas, "png", baos);
            return NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage renderBuiltinFace(String id) {
        BufferedImage face = new BufferedImage(FACE_W, FACE_H, BufferedImage.TYPE_INT_ARGB);
        if (id == null) id = "sunset";
        switch (id) {
            case "ocean" -> verticalGradient(face, 0x00D2FF, 0x2B5876, 0x0B1E3A);
            case "fire" -> verticalGradient(face, 0xF6D365, 0xFFB199, 0xFF0844, 0x7A0010);
            case "rainbow" -> rainbow(face);
            case "stars" -> stars(face);
            case "heart" -> heart(face);
            case "creeper" -> creeper(face);
            case "lightning" -> lightning(face);
            case "stripes" -> stripes(face);
            case "checker" -> checker(face);
            default -> verticalGradient(face, 0xFF5E62, 0xFF9966, 0xE056FD);
        }
        return face;
    }

    private static void verticalGradient(BufferedImage f, int... hexStops) {
        int h = f.getHeight();
        int segs = hexStops.length - 1;
        for (int y = 0; y < h; y++) {
            float t = (h <= 1) ? 0 : (float) y / (h - 1) * segs;
            int idx = Math.min((int) t, segs - 1);
            float local = t - idx;
            int c = lerpColor(hexStops[idx], hexStops[idx + 1], local);
            for (int x = 0; x < f.getWidth(); x++) f.setRGB(x, y, c);
        }
    }

    private static void rainbow(BufferedImage f) {
        int h = f.getHeight();
        for (int y = 0; y < h; y++) {
            int c = java.awt.Color.HSBtoRGB((float) y / h, 0.85f, 1.0f) | 0xFF000000;
            for (int x = 0; x < f.getWidth(); x++) f.setRGB(x, y, c);
        }
    }

    private static void stars(BufferedImage f) {
        fill(f, 0x0B1026);
        int w = f.getWidth(), h = f.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int hash = (x * 73856093) ^ (y * 19349663);
                if ((hash & 31) == 0) f.setRGB(x, y, 0xFFFFFFFF);
                else if ((hash & 63) == 0) f.setRGB(x, y, 0xFF9FB8FF);
            }
        }
        dot(f, 2, 4, 0xFFFFFFFF);
        dot(f, 7, 9, 0xFFCCE0FF);
        dot(f, 4, 18, 0xFFFFFFFF);
        dot(f, 8, 24, 0xFF9FB8FF);
        cross(f, 5, 13, 0xFFFFFFFF);
    }

    private static void heart(BufferedImage f) {
        fill(f, 0x14060C);
        String[] tpl = {
                ".XX.XX.",
                "XXXXXXX",
                "XXXXXXX",
                ".XXXXX.",
                "..XXX..",
                "...X..."
        };
        int ox = (f.getWidth() - 7) / 2;
        int oy = (f.getHeight() - tpl.length) / 2;
        for (int y = 0; y < tpl.length; y++) {
            for (int x = 0; x < tpl[y].length(); x++) {
                if (tpl[y].charAt(x) == 'X') f.setRGB(ox + x, oy + y, 0xFFFF3B5C);
            }
        }
        f.setRGB(ox + 1, oy + 1, 0xFFFF9DB0);
        f.setRGB(ox + 2, oy + 1, 0xFFFF8AA0);
    }

    private static void creeper(BufferedImage f) {
        fill(f, 0x4B8A3B);
        for (int y = 0; y < f.getHeight(); y++) {
            for (int x = 0; x < f.getWidth(); x++) {
                if (((x + y) & 3) == 0) f.setRGB(x, y, 0x57A043);
                else if (((x * 3 + y) & 7) == 0) f.setRGB(x, y, 0x3F7531);
            }
        }
        String[] face = {
                "........",
                ".XX..XX.",
                ".XX..XX.",
                "..XXXX..",
                "..XXXX..",
                ".XXXXXX.",
                ".XX..XX.",
                ".XX..XX."
        };
        int ox = (f.getWidth() - 8) / 2;
        int oy = 4;
        for (int y = 0; y < face.length; y++) {
            for (int x = 0; x < face[y].length(); x++) {
                if (face[y].charAt(x) == 'X') f.setRGB(ox + x, oy + y, 0x0A0F0A);
            }
        }
    }

    private static void lightning(BufferedImage f) {
        fill(f, 0x0E1430);
        int[][] bolt = {
                {6, 2}, {5, 5}, {4, 8}, {5, 11}, {3, 14}, {4, 17}, {2, 20}, {3, 23}, {1, 26}, {2, 29}
        };
        for (int[] p : bolt) {
            thick(f, p[0], p[1], 0xFFE7FF5A);
            if (p[0] + 1 < f.getWidth()) f.setRGB(p[0] + 1, p[1], 0xFFFFF3A0);
        }
    }

    private static void stripes(BufferedImage f) {
        int h = f.getHeight();
        for (int y = 0; y < h; y++) {
            int c = (y / 4) % 2 == 0 ? 0xFF1FA2A6 : 0xFF0C3A5C;
            for (int x = 0; x < f.getWidth(); x++) f.setRGB(x, y, c);
        }
    }

    private static void checker(BufferedImage f) {
        int w = f.getWidth(), h = f.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean on = ((x / 2) + (y / 2)) % 2 == 0;
                f.setRGB(x, y, on ? 0xFF222831 : 0xFF00ADB5);
            }
        }
    }

    private static void fill(BufferedImage f, int argb) {
        for (int y = 0; y < f.getHeight(); y++)
            for (int x = 0; x < f.getWidth(); x++) f.setRGB(x, y, argb | 0xFF000000);
    }

    private static void dot(BufferedImage f, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < f.getWidth() && y < f.getHeight()) f.setRGB(x, y, argb);
    }

    private static void cross(BufferedImage f, int x, int y, int argb) {
        dot(f, x, y, argb);
        dot(f, x - 1, y, argb);
        dot(f, x + 1, y, argb);
        dot(f, x, y - 1, argb);
        dot(f, x, y + 1, argb);
    }

    private static void thick(BufferedImage f, int x, int y, int argb) {
        dot(f, x, y, argb);
        dot(f, x + 1, y, argb);
        dot(f, x, y + 1, argb);
        dot(f, x + 1, y + 1, argb);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
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

            BufferedImage face = highQualityDownscale(src, FACE_W, FACE_H);

            BufferedImage canvas = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(face, FRONT_X, FRONT_Y, null);
            g.drawImage(face, BACK_X, BACK_Y, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(canvas, "png", baos);
            return NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage highQualityDownscale(BufferedImage src, int tw, int th) {
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
        String clean = fileName.toLowerCase(Locale.ROOT);
        if (clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg")) {
            clean = clean.substring(0, clean.lastIndexOf('.'));
        }
        return clean.replaceAll("[^a-z0-9._-]", "_");
    }
}