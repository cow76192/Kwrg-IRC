package me.cookie.kwrg.svg.render;

import me.cookie.kwrg.svg.SvgImage;
import com.mojang.blaze3d.platform.NativeImage;
import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SvgChatRenderer {

    private static final int MAX_TEXTURES = 32;
    private static final int ANIMATION_STEP_MS = 100;
    private static final int ANIMATION_FRAMES = 8;
    private static final int HUD_VISIBLE_TICKS = 200;
    private static final int MESSAGE_INDENT = 4;
    private static final int HUD_FADE_TICKS = 20;
    private static final int MAX_ACTIVE = 100;
    private static final int MAX_PENDING = 6;
    private static final int MAX_UPLOADS_PER_FRAME = 3;

    private static final Map<Component, SvgImage> ACTIVE = new IdentityHashMap<>();
    private static final LinkedHashMap<String, Identifier> TEXTURES = new LinkedHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Queue<Object[]> READY = new ConcurrentLinkedQueue<>();
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "kwrg-svg-raster");
        thread.setDaemon(true);
        return thread;
    });

    private static int textureCounter = 0;
    private static final Map<String, Identifier> LAST_FRAME = new HashMap<>();

    private SvgChatRenderer() {
    }

    public static void bind(Component component, SvgImage image) {
        if (component == null || image == null) {
            return;
        }
        ACTIVE.put(component, image);
        while (ACTIVE.size() > MAX_ACTIVE) {
            Component first = ACTIVE.keySet().iterator().next();
            ACTIVE.remove(first);
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Identifier id : TEXTURES.values()) {
            mc.getTextureManager().release(id);
        }
        TEXTURES.clear();
        LAST_FRAME.clear();
        ACTIVE.clear();
        PENDING.clear();
        READY.clear();
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("kwrg-svg");
    private static final java.util.Map<String, Integer> FAIL_COUNTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int FAIL_LIMIT = 20;

    public static void fail(String key, String message, Throwable error) {
        int count = FAIL_COUNTS.merge(key, 1, Integer::sum);
        if (count <= FAIL_LIMIT) {
            LOGGER.error("[SVG] {} {}", key, message, error);
        } else if (count == FAIL_LIMIT + 1) {
            LOGGER.error("[SVG] {} further messages suppressed", key);
        }
    }

    public static void decodeAsync(String payload, java.util.function.Consumer<SvgImage> onDone) {
        WORKER.submit(() -> {
            SvgImage image;
            try {
                image = SvgImage.decode(payload);
            } catch (RuntimeException e) {
                fail("decode", "payload rejected: " + e.getMessage(), e);
                return;
            }
            Minecraft.getInstance().execute(() -> onDone.accept(image));
        });
    }

    public static void sendMessage(SvgImage image, Component prefix) {
        if (image == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        int lines = reservedLines(image, mc);
        image.blockLines(lines);
        MutableComponent component = prefix == null ? Component.empty() : prefix.copy();
        for (int i = 0; i < lines; i++) {
            component.append("\n ");
        }
        bind(component, image);
        ChatNotifier.sendRawInChatScreen(component);
    }

    private static int reservedLines(SvgImage image, Minecraft mc) {
        double scale = Math.max(0.1, mc.options.chatScale().get());
        int lineHeight = Math.max(1, (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0)));
        int maxWidth = Mth.ceil(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale);
        int maxHeight = Mth.ceil(ChatComponent.getHeight(mc.options.chatHeightFocused().get()) / scale) - lineHeight * 2;
        if (maxHeight < lineHeight * 2) {
            maxHeight = lineHeight * 2;
        }
        float baseWidth = Math.max(1.0f, image.baseWidth());
        float baseHeight = Math.max(1.0f, image.baseHeight());
        double fit = Math.min(maxWidth / (double) baseWidth, maxHeight / (double) baseHeight);
        int displayWidth = Math.max(1, (int) Math.round(baseWidth * fit));
        int displayHeight = Math.max(1, (int) Math.round(baseHeight * fit));
        int lines = Math.max(1, (int) Math.ceil(displayHeight / (double) lineHeight));
        return lines;
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, Minecraft mc, List<GuiMessage.Line> trimmed,
                            int scrollPos, int linesPerPage, int mouseX, int mouseY, boolean foreground) {
        try {
            drawInternal(graphics, font, mc, trimmed, scrollPos, linesPerPage, mouseX, mouseY, foreground);
        } catch (RuntimeException e) {
            fail("draw-crash", "draw failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
    }

    private static void drawInternal(GuiGraphicsExtractor graphics, Font font, Minecraft mc, List<GuiMessage.Line> trimmed,
                                     int scrollPos, int linesPerPage, int mouseX, int mouseY, boolean foreground) {
        drainReady();
        if (trimmed == null || trimmed.isEmpty()) {
            if (!ACTIVE.isEmpty() || !TEXTURES.isEmpty()) {
                clear();
            }
            return;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        double scale = Math.max(0.1, mc.options.chatScale().get());
        int lineHeight = Math.max(1, (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0)));
        int chatBottom = Mth.floor((graphics.guiHeight() - 40) / scale);
        int maxWidth = Mth.ceil(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale);
        org.joml.Matrix3x2fStack pose = graphics.pose();
        boolean needPose = Math.abs(pose.m00() - (float) scale) > 0.001f;
        pose.pushMatrix();
        try {
            if (needPose) {
                pose.scale((float) scale, (float) scale);
                pose.translate(4.0f, 0.0f);
            }

            int visible = Math.min(trimmed.size() - scrollPos, Math.max(0, linesPerPage));
            Map<GuiMessage, int[]> groups = new LinkedHashMap<>();
            Map<GuiMessage, SvgImage> images = new LinkedHashMap<>();
            for (int i = visible - 1; i >= 0; i--) {
                int index = i + scrollPos;
                if (index < 0 || index >= trimmed.size()) {
                    continue;
                }
                GuiMessage.Line line = trimmed.get(index);
                if (line == null || line.parent() == null) {
                    continue;
                }
                GuiMessage parent = line.parent();
                SvgImage image = ACTIVE.get(parent.content());
                if (image == null) {
                    continue;
                }
                images.put(parent, image);
                int[] span = groups.get(parent);
                if (span == null) {
                    groups.put(parent, new int[]{i, i});
                } else {
                    span[0] = Math.min(span[0], i);
                    span[1] = Math.max(span[1], i);
                }
            }
            if (groups.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            for (Map.Entry<GuiMessage, int[]> entry : groups.entrySet()) {
                int[] span = entry.getValue();
                SvgImage image = images.get(entry.getKey());
                if (image == null) {
                    continue;
                }
                int alpha = 255;
                if (!foreground) {
                    int age = mc.gui.getGuiTicks() - entry.getKey().addedTime();
                    if (age > HUD_VISIBLE_TICKS) {
                        continue;
                    }
                    float fade = Mth.clamp((float) (HUD_VISIBLE_TICKS - age) / (float) HUD_FADE_TICKS, 0.0f, 1.0f);
                    double opacity = mc.options.chatOpacity().get();
                    alpha = (int) Math.round(255.0 * fade * opacity);
                    if (alpha < 8) {
                        continue;
                    }
                }
                int imageTop = chatBottom - (span[0] + Math.max(1, image.blockLines())) * lineHeight;
                int top = Math.max(imageTop, chatBottom - (span[1] + 1) * lineHeight);
                int bottom = chatBottom - span[0] * lineHeight;
                int rectHeight = Math.max(lineHeight, bottom - top);
                float aspect = Math.max(0.05f, image.aspect());
                int width = Math.min(maxWidth, Math.max(1, Math.round(rectHeight / aspect)));
                int blockHeight = Math.max(1, Math.round(width * aspect));
                if (blockHeight > rectHeight) {
                    blockHeight = rectHeight;
                    width = Math.min(maxWidth, Math.max(1, Math.round(blockHeight / aspect)));
                }
                int drawTop = bottom - blockHeight;
                Identifier texture = texture(image, width, blockHeight, now);
                if (texture == null) {
                    continue;
                }
                if (alpha >= 255) {
                    graphics.blit(texture, MESSAGE_INDENT, drawTop, MESSAGE_INDENT + width, drawTop + blockHeight, 0.0f, 1.0f, 0.0f, 1.0f);
                } else {
                    graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, MESSAGE_INDENT, drawTop, 0.0f, 0.0f, width, blockHeight, width, blockHeight, (alpha << 24) | 0xFFFFFF);
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static Identifier texture(SvgImage image, int width, int height, long now) {
        int safeWidth = Math.max(1, Math.min(width, 512));
        int safeHeight = Math.max(1, Math.min(height, 512));
        int frame = 0;
        if (image.animated()) {
            frame = (int) ((now / ANIMATION_STEP_MS) % ANIMATION_FRAMES);
        }
        final int frameIndex = frame;
        String base = System.identityHashCode(image) + ":" + image.source().hashCode();
        String key = base + ":" + safeWidth + "x" + safeHeight + ":" + frameIndex;
        Identifier cached = TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        String imageKey = base + ":" + safeWidth + "x" + safeHeight;
        if (PENDING.size() >= MAX_PENDING) {
            return LAST_FRAME.get(imageKey);
        }
        if (!PENDING.add(key)) {
            return LAST_FRAME.get(imageKey);
        }
        long time = image.animated() ? frameIndex * (long) ANIMATION_STEP_MS : 0L;
        WORKER.submit(() -> {
            try {
                BufferedImage raster = SvgRenderer.render(image.document(), safeWidth, safeHeight, time, false, false);
                if (opaqueCount(raster) == 0) {
                    raster = SvgRenderer.render(image.document(), safeWidth, safeHeight, 0L, false, true);
                }
                READY.add(new Object[]{key, raster, safeWidth, safeHeight, frameIndex});
            } catch (RuntimeException e) {
                fail("raster", "render failed " + safeWidth + "x" + safeHeight + ": " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
            } finally {
                PENDING.remove(key);
            }
        });
        return LAST_FRAME.get(imageKey);
    }

    private static void drainReady() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < MAX_UPLOADS_PER_FRAME; i++) {
            Object[] entry = READY.poll();
            if (entry == null) {
                return;
            }
            String key = (String) entry[0];
            BufferedImage raster = (BufferedImage) entry[1];
            int width = (Integer) entry[2];
            int height = (Integer) entry[3];
            if (!READY.isEmpty() && raster.getWidth() == width && raster.getHeight() == height && READY.size() > 8) {
                continue;
            }
            NativeImage nativeImage = toNativeImage(raster);
            Identifier id = Identifier.fromNamespaceAndPath("kwrg", "svg/" + (textureCounter++));
            try {
                mc.getTextureManager().register(id, new DynamicTexture(() -> "kwrg_svg", nativeImage));
            } catch (RuntimeException e) {
                fail("register", "register failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
                nativeImage.close();
                continue;
            }
            TEXTURES.put(key, id);
            LAST_FRAME.put(key.substring(0, key.lastIndexOf(':')), id);
            while (TEXTURES.size() > MAX_TEXTURES) {
                String oldest = TEXTURES.keySet().iterator().next();
                Identifier removed = TEXTURES.remove(oldest);
                LAST_FRAME.values().remove(removed);
                if (removed != null && !removed.equals(id)) {
                    mc.getTextureManager().release(removed);
                }
            }
        }
    }

    private static int opaqueCount(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 4) {
            for (int x = 0; x < image.getWidth(); x += 4) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                    count++;
                }
            }
        }
        return count;
    }

    private static NativeImage toNativeImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage image = new NativeImage(width, height, false);
        int[] argb = new int[width * height];
        source.getRGB(0, 0, width, height, argb, 0, width);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[y * width + x]);
            }
        }
        return image;
    }
}
