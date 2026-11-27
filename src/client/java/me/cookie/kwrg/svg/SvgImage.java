package me.cookie.kwrg.svg;

import me.cookie.kwrg.svg.render.SvgRenderer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SvgImage {

    public static final int MAX_PAYLOAD_CHARS = 262144;

    private static final Set<String> ANIMATION_TAGS = Set.of("animate", "set", "animatetransform");

    private final String source;
    private final SvgParser.Document document;
    private final boolean animated;
    private final float baseWidth;
    private final float baseHeight;
    private int blockLines = 1;

    private SvgImage(String source, SvgParser.Document document, float baseWidth, float baseHeight) {
        this.source = source;
        this.document = document;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.animated = scanAnimated(document.root(), 0);
    }

    public static final class Draft {

        private static volatile SvgImage pending;
        private static volatile long lastAccept = 0L;

        private Draft() {
        }

        public static void put(SvgImage image) {
            pending = image;
        }

        public static SvgImage peek() {
            return pending;
        }

        public static boolean has() {
            return pending != null;
        }

        public static SvgImage take() {
            SvgImage image = pending;
            pending = null;
            return image;
        }

        public static boolean acceptWithin(long windowMs) {
            long now = System.currentTimeMillis();
            if (now - lastAccept < windowMs) {
                return false;
            }
            lastAccept = now;
            return true;
        }

        public static void clear() {
            pending = null;
        }

        public static SvgImage fromClipboard(String text) {
            if (text == null) {
                return null;
            }
            String value = text.trim();
            if (value.isEmpty() || value.length() > MAX_PAYLOAD_CHARS) {
                return null;
            }
            try {
                String lower = value.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("data:image/svg+xml;base64,")) {
                    byte[] bytes = Base64.getDecoder().decode(value.substring(26));
                    return of(new String(bytes, StandardCharsets.UTF_8));
                }
                if (lower.startsWith("data:image/svg+xml,")) {
                    return of(value.substring(19));
                }
                if (lower.contains("<svg")) {
                    return of(value.substring(lower.indexOf("<svg")));
                }
                if (value.startsWith("v1:")) {
                    return decode(value);
                }
            } catch (RuntimeException e) {
                return null;
            }
            return null;
        }

        public static SvgImage fromPath(String rawPath) {
            if (rawPath == null) {
                return null;
            }
            String value = rawPath.trim();
            if (value.isEmpty()) {
                return null;
            }
            if (value.length() > 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).trim();
            }
            if (!value.toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
                return null;
            }
            try {
                java.nio.file.Path path = java.nio.file.Path.of(value).toAbsolutePath().normalize();
                if (!java.nio.file.Files.isRegularFile(path) || !java.nio.file.Files.isReadable(path)) {
                    return null;
                }
                long size = java.nio.file.Files.size(path);
                if (size <= 0L || size > SvgParser.MAX_SOURCE) {
                    return null;
                }
                byte[] bytes = java.nio.file.Files.readAllBytes(path);
                return of(new String(bytes, StandardCharsets.UTF_8));
            } catch (java.io.IOException | RuntimeException e) {
                return null;
            }
        }
    }

    public static SvgImage decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("empty payload");
        }
        String raw = payload.trim();
        if (!raw.startsWith("v1:")) {
            throw new IllegalArgumentException("unsupported payload version");
        }
        String body = raw.substring(3);
        if (body.length() > MAX_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("payload too large");
        }
        byte[] compressed = Base64.getDecoder().decode(body);
        String source;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > SvgParser.MAX_SOURCE) {
                    throw new IllegalArgumentException("svg too large");
                }
                out.write(buffer, 0, read);
            }
            source = out.toString(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("payload corrupt", e);
        }
        return of(source);
    }

    public static SvgImage of(String source) {
        SvgParser.Document document = SvgParser.parse(source);
        float[] size = size(document);
        return new SvgImage(source, document, size[0], size[1]);
    }

    public static String encode(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("empty svg");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(buffer)) {
                out.write(source.getBytes(StandardCharsets.UTF_8));
            }
            return "v1:" + Base64.getEncoder().encodeToString(buffer.toByteArray());
        } catch (Exception e) {
            throw new IllegalArgumentException("payload encode failed", e);
        }
    }

    private static float[] size(SvgParser.Document document) {
        double[] box = SvgRenderer.intrinsicBox(document);
        float width = (float) box[2];
        float height = (float) box[3];
        if (!(width > 0.0f)) {
            width = 128.0f;
        }
        if (!(height > 0.0f)) {
            height = 128.0f;
        }
        return new float[]{width, height};
    }

    private static boolean scanAnimated(SvgNode node, int depth) {
        if (node == null || depth > 40) {
            return false;
        }
        for (SvgNode child : node.children()) {
            if (ANIMATION_TAGS.contains(child.tag().toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            if (scanAnimated(child, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    public String source() {
        return source;
    }

    public SvgParser.Document document() {
        return document;
    }

    public boolean animated() {
        return animated;
    }

    public float baseWidth() {
        return baseWidth;
    }

    public float baseHeight() {
        return baseHeight;
    }

    public int blockLines() {
        return blockLines;
    }

    public void blockLines(int value) {
        this.blockLines = Math.max(1, value);
    }

    public float aspect() {
        return baseWidth <= 0.0f ? 1.0f : baseHeight / baseWidth;
    }

}
