package me.cookie.kwrg.irc.manager;

import com.mojang.blaze3d.platform.NativeImage;
import org.w3c.dom.Node;

import javax.imageio.metadata.IIOMetadata;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class CapeImagePipeline {
    private CapeImagePipeline() {}

    //检查贴图右侧的鞘翅有效区域
    static boolean checkHasElytra(BufferedImage img) {
        if (!isStandardCape(img.getWidth(), img.getHeight())) {
            return false;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int u = w / 64;

        int startX = 22 * u;
        for (int y = 0; y < h; y++) {
            for (int x = startX; x < w; x++) {
                int pixel = img.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha > 10) {
                    return true;
                }
            }
        }
        return false;
    }

    static String capeMagic(Path file) {
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

    static boolean hasPngAnimationChunk(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] chunk = new byte[4096];
            int read = in.read(chunk);
            if (read <= 0) return false;
            return new String(chunk, 0, read, StandardCharsets.ISO_8859_1).contains("acTL");
        } catch (Exception e) {
            return false;
        }
    }

    static String plainCapeName(String fileName) {
        if (fileName == null) return null;
        return fileName.replace('@', '_').replace('#', '_');
    }

    static String capeExtension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static boolean magicMatchesExtension(String magic, String extension) {
        if ("jpg".equals(extension) || "jpeg".equals(extension)) return "jpg".equals(magic);
        if ("gif".equals(extension)) return "gif".equals(magic);
        if ("png".equals(extension)) return "png".equals(magic);
        return true;
    }

    static int[] gifFrameInfo(IIOMetadata metadata) {
        int[] info = {0, 0, 1, 100};
        if (metadata == null) return info;

        String format = metadata.getNativeMetadataFormatName();
        if (format == null) return info;

        try {
            gifCollect(metadata.getAsTree(format), info);
        } catch (Exception ignored) {}
        return info;
    }

    static void gifCollect(Node node, int[] info) {
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

    static int gifAttr(Node node, String attribute, int fallback) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(attribute);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.getNodeValue().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static int gifDisposal(Node node, int fallback) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("disposalMethod");
        if (value == null) return fallback;

        String mode = value.getNodeValue();
        if ("restoreToBackgroundColor".equalsIgnoreCase(mode)) return 2;
        if ("restoreToPrevious".equalsIgnoreCase(mode)) return 3;
        if ("none".equalsIgnoreCase(mode)) return 0;
        return fallback;
    }

    static BufferedImage copyOf(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    static int parseRequestedFrameCount(String fileName) {
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

    static BufferedImage paintBlock(BufferedImage face, int block, int faceW, int faceH) {
        int u = block / 64;
        int width = block;
        int height = block / 2;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

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

    static BufferedImage downscaleSmooth(BufferedImage src, int tw, int th) {

        if (src.getWidth() == tw && src.getHeight() == th) {
            return src;
        }

        BufferedImage cur = src;

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

    static String sanitizePath(String fileName) {
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

    static NativeImage toNativeImage(BufferedImage bufferedImage) {
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

    static boolean isStandardCape(int width, int height) {
        return width == height * 2;
    }

    static BufferedImage scaleStandardCape(BufferedImage src, int targetW, int targetH) {
        if (src.getWidth() == targetW && src.getHeight() == targetH) {
            return src;
        }
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();

        if (src.getWidth() < targetW) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }

        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return out;
    }

    static BufferedImage processCapeFrame(BufferedImage frame, int block, int faceW, int faceH) {
        if (isStandardCape(frame.getWidth(), frame.getHeight())) {
            return scaleStandardCape(frame, block, block / 2);
        } else {
            return paintBlock(downscaleSmooth(frame, faceW, faceH), block, faceW, faceH);
        }
    }
}
