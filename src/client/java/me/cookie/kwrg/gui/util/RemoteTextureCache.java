package me.cookie.kwrg.gui.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads images from URLs in the background and turns them into GUI textures. */
public final class RemoteTextureCache {

    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private static final long RETRY_AFTER_MS = 30_000L;
    private static final int MAX_BYTES = 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "kwrg-remote-texture");
        t.setDaemon(true);
        return t;
    });

    private RemoteTextureCache() {}

    /** Cheap map lookup, safe to call every frame. Returns null while loading or after a failure. */
    public static Identifier peek(String url) {
        return url == null ? null : READY.get(url);
    }

    /**
     * Starts a background download if needed. The image is resized to exactly width x height,
     * so the size you pass to withPreview(...) always matches the real texture.
     * Failed URLs are retried at most once every 30 seconds.
     */
    public static void request(String url, int width, int height) {
        if (url == null || url.isBlank() || width <= 0 || height <= 0) return;
        if (READY.containsKey(url)) return;

        Long failedAt = FAILED_AT.get(url);
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) return;
        if (!LOADING.add(url)) return;

        EXECUTOR.submit(() -> load(url, width, height));
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(RemoteTextureCache::clear);
            return;
        }
        for (Identifier id : READY.values()) {
            mc.getTextureManager().release(id);
        }
        READY.clear();
        FAILED_AT.clear();
    }

    private static void load(String url, int width, int height) {
        NativeImage img = null;
        boolean handedOver = false;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "kwrg-mod")
                    .GET()
                    .build();

            byte[] bytes;
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
                bytes = in.readNBytes(MAX_BYTES + 1); // stops reading past the cap
            }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new IOException("unexpected size: " + bytes.length);
            }

            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) throw new IOException("not a decodable image");

            img = toNativeImage(fit(src, width, height));
            final NativeImage finalImg = img;

            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    Identifier id = Identifier.fromNamespaceAndPath("kwrg", "remote/" + idFor(url));
                    mc.getTextureManager().register(id, new DynamicTexture(() -> "kwrg_remote", finalImg));
                    READY.put(url, id);
                } catch (Exception e) {
                    System.err.println("[KWRG Remote] register failed: " + url);
                    e.printStackTrace();
                    finalImg.close();
                    FAILED_AT.put(url, System.currentTimeMillis());
                } finally {
                    LOADING.remove(url);
                }
            });
            handedOver = true;
        } catch (Exception e) {
            System.err.println("[KWRG Remote] load failed: " + url + " (" + e + ")");
            FAILED_AT.put(url, System.currentTimeMillis());
        } finally {
            if (!handedOver) {
                if (img != null) img.close();
                LOADING.remove(url);
            }
        }
    }

    private static String idFor(String url) {
        return UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private static BufferedImage fit(BufferedImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static NativeImage toNativeImage(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        NativeImage ni = new NativeImage(w, h, false);
        int[] argb = new int[w * h];
        bi.getRGB(0, 0, w, h, argb, 0, w);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ni.setPixel(x, y, argb[y * w + x]);
            }
        }
        return ni;
    }
}