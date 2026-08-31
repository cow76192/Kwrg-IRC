package me.cookie.kwrg.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TotemAnimationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-totem");
    private static class CachedImage {
        final Identifier id;
        final long byteSize;

        CachedImage(Identifier id, long byteSize) {
            this.id = id;
            this.byteSize = byteSize;
        }
    }
    private static final Map<String, CachedImage> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static Identifier currentTexture = null;
    private static int animationTicksLeft = 0;

    public static void playUrlEffect(String sender, String url) {
        Minecraft mc = Minecraft.getInstance();

        if (IMAGE_CACHE.containsKey(url)) {
            triggerAnimation(sender, IMAGE_CACHE.get(url).id);
            return;
        }

        if (!Objects.equals(sender, "server ") && !Objects.equals(sender, "控制台管理员")) {
            ChatNotifier.sendInChatScreen("§6[IRC] 正在下载来自 §e" + sender + " §6的图片...");
        }


        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "image/png,image/jpeg,image/pjpeg,image/bmp;q=0.9,*/*;q=0.8")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    try (InputStream in = response.body()) {
                        byte[] bytes = in.readAllBytes();
                        NativeImage nativeImage = null;

                        try {
                            java.awt.image.BufferedImage bufferedImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                            if (bufferedImage != null) {
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                javax.imageio.ImageIO.write(bufferedImage, "png", baos);
                                nativeImage = NativeImage.read(new java.io.ByteArrayInputStream(baos.toByteArray()));
                            }
                        } catch (Exception ignored) {
                        }

                        if (nativeImage == null) {
                            java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
                            try {
                                buffer.put(bytes);
                                buffer.flip();
                                nativeImage = NativeImage.read(buffer);
                            } finally {
                                org.lwjgl.system.MemoryUtil.memFree(buffer);
                            }
                        }

                        if (nativeImage == null) {
                            String error = "图片无法解析或不是有效图片";
                            ChatNotifier.sendInChatScreen("§c[IRC] " + error);
                            me.cookie.kwrg.irc.IrcClient.sendPlayFail(sender, url, error);
                            return;
                        }

                        final NativeImage finalImage = nativeImage;
                        mc.execute(() -> {
                            try {
                                DynamicTexture dynamicTexture = new DynamicTexture(() -> "kwrg_dynamic_totem", finalImage);
                                String uniqueKey = "dynamic/" + UUID.randomUUID().toString().replace("-", "");
                                Identifier textureId = Identifier.fromNamespaceAndPath("kwrg", uniqueKey);

                                mc.getTextureManager().register(textureId, dynamicTexture);
                                long memSize = (long) finalImage.getWidth() * finalImage.getHeight() * 4L;
                                IMAGE_CACHE.put(url, new CachedImage(textureId, memSize));

                                triggerAnimation(sender, textureId);
                            } catch (Exception e) {
                                LOGGER.error("注册动态材质失败", e);
                                String error = "注册动态材质失败: " + e.getMessage();
                                ChatNotifier.sendInChatScreen("§c[IRC] " + error);
                                me.cookie.kwrg.irc.IrcClient.sendPlayFail(sender, url, error);
                            }
                        });
                    }
                } else {
                    String error = "图片无法下载 (HTTP " + response.statusCode() + ")";
                    ChatNotifier.sendInChatScreen("§c[IRC] " + error);
                    me.cookie.kwrg.irc.IrcClient.sendPlayFail(sender, url, error);
                }
            } catch (Exception e) {
                LOGGER.error("下载图片异常", e);
                String error = "图片无法下载: " + e.getMessage();
                ChatNotifier.sendInChatScreen("§c[IRC] " + error);
                me.cookie.kwrg.irc.IrcClient.sendPlayFail(sender, url, error);
            }
        });
    }

    private static void triggerAnimation(String sender, Identifier textureId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            currentTexture = textureId;
            animationTicksLeft = 40;

            if (mc.player != null) {
                mc.player.playSound(SoundEvents.TOTEM_USE, 0.1F, 1.0F);
            }

            if(!Objects.equals(sender, "server ") && !Objects.equals(sender, "控制台管理员")) {
                ChatNotifier.sendInChatScreen("§6[IRC] §e" + sender + " §a向你发送了网络图片图腾动画！");
            }

        });
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (animationTicksLeft <= 0 || currentTexture == null) {
            return;
        }

        animationTicksLeft--;

        float progress = (40 - animationTicksLeft) / 40.0F;
        float f1 = progress * progress;
        float f2 = progress * f1;
        float popFactor = 4.0F * (f1 - f2);

        float scale = 0.8F + popFactor * 1.2F;

        float alpha = 1.0F;
        if (animationTicksLeft < 10) {
            alpha = animationTicksLeft / 10.0F;
        }

        int alphaInt = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        if (alphaInt <= 0) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2 - 10;

        int imgSize = 120;

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) centerX, (float) centerY);
        graphics.pose().scale(scale, scale);

        int color = ARGB.white(alphaInt);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                currentTexture,
                -imgSize / 2,
                -imgSize / 2,
                0.0F,
                0.0F,
                imgSize,
                imgSize,
                imgSize,
                imgSize,
                color
        );

        graphics.pose().popMatrix();
    }

    public static double clearCache() {
        Minecraft mc = Minecraft.getInstance();
        long totalBytes = 0;

        for (CachedImage cached : IMAGE_CACHE.values()) {
            totalBytes += cached.byteSize;
            mc.execute(() -> mc.getTextureManager().release(cached.id));
        }

        IMAGE_CACHE.clear();
        currentTexture = null;
        animationTicksLeft = 0;

        return (double) totalBytes / (1024.0 * 1024.0);
    }

    public static int getCacheCount() {
        return IMAGE_CACHE.size();
    }
}