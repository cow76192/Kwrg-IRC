package me.cookie.kwrg.render;

import com.mojang.blaze3d.textures.GpuTexture;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.duck.KwrgAtlasStaging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class LoadingAtlasUpload {
    private static final int SPRITES_PER_BATCH = 32;
    private static final int MIN_STATIC_SPRITES = 32;
    // The sum of all staged base levels is capped. Their mip levels add at most one third to this footprint.
    private static final long MAX_STAGED_PIXELS = 4096L * 4096L;

    // All access happens on the render thread, including the vanilla apply continuation.
    private static Upload active;

    private LoadingAtlasUpload() {
    }

    public static boolean isLoadingWithoutWorld(Minecraft client) {
        return client != null && client.isSameThread() && client.level == null && client.getOverlay() instanceof LoadingOverlay;
    }

    public static void stage(Minecraft client, AtlasManager manager, AtlasManager.PendingStitchResults stitches,
                             Runnable apply, Executor executor) {
        if (active != null && active.manager == manager) {
            active.completion.handle((value, failure) -> null).thenRunAsync(apply, executor);
            return;
        }
        if (active != null) {
            apply.run();
            return;
        }

        List<StagedAtlas> atlases;
        try {
            atlases = selectAtlases(manager, stitches);
        } catch (RuntimeException exception) {
            CookieMain.LOGGER.warn("Could not prepare staged atlas uploads. Using the normal resource reload.", exception);
            apply.run();
            return;
        }
        if (atlases.isEmpty()) {
            apply.run();
            return;
        }

        Upload upload = new Upload(manager, atlases);
        active = upload;

        Executor nextFrame = task -> client.schedule(() -> executor.execute(task));
        upload.completion = upload.ready.thenRunAsync(() -> {
            if (active != upload) {
                return;
            }
            try {
                upload.publishing = true;
                apply.run();
            } finally {
                active = null;
                upload.close();
            }
        }, nextFrame);
    }

    private static List<StagedAtlas> selectAtlases(AtlasManager manager, AtlasManager.PendingStitchResults stitches) {
        List<StagedAtlas> candidates = new ArrayList<>();
        manager.forEach((id, atlas) -> {
            SpriteLoader.Preparations preparations = stitches.get(id).join();
            List<TextureAtlasSprite> sprites = preparations.regions().values().stream()
                    .filter(sprite -> !sprite.contents().isAnimated())
                    .toList();
            if (sprites.size() >= MIN_STATIC_SPRITES) {
                candidates.add(new StagedAtlas(atlas, preparations, sprites));
            }
        });
        // Prefer atlases that would otherwise issue the most per-sprite GPU commands.
        candidates.sort(Comparator.comparingInt(StagedAtlas::uploadCount).reversed());
        List<StagedAtlas> selected = new ArrayList<>();
        long remainingPixels = MAX_STAGED_PIXELS;
        for (StagedAtlas atlas : candidates) {
            long pixels = (long) atlas.preparations.width() * atlas.preparations.height();
            if (pixels <= remainingPixels) {
                selected.add(atlas);
                remainingPixels -= pixels;
            }
        }
        return selected;
    }

    public static boolean step(Minecraft client) {
        Upload upload = active;
        if (upload == null || upload.ready.isDone()) {
            return false;
        }
        if (!isLoadingWithoutWorld(client)) {
            upload.close();
            upload.ready.complete(null);
            return true;
        }

        try {
            if (upload.advance()) {
                upload.ready.complete(null);
            }
        } catch (RuntimeException exception) {
            upload.close();
            CookieMain.LOGGER.warn("Staged atlas upload failed. Retrying through the normal resource reload.", exception);
            upload.ready.complete(null);
        }
        return true;
    }

    public static GpuTexture takeTexture(TextureAtlas target, SpriteLoader.Preparations preparations) {
        Upload upload = active;
        if (upload == null || !upload.publishing) {
            return null;
        }
        for (StagedAtlas atlas : upload.atlases) {
            if (atlas.target == target && atlas.preparations == preparations && atlas.staging != null) {
                return ((KwrgAtlasStaging) atlas.staging).kwrg$detachTexture();
            }
        }
        return null;
    }

    public static void close() {
        Upload upload = active;
        if (upload != null) {
            active = null;
            upload.ready.cancel(false);
            upload.close();
        }
    }

    private static final class Upload implements AutoCloseable {
        private final AtlasManager manager;
        private final List<StagedAtlas> atlases;
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private CompletableFuture<Void> completion;
        private int nextAtlas;
        private boolean publishing;

        private Upload(AtlasManager manager, List<StagedAtlas> atlases) {
            this.manager = manager;
            this.atlases = atlases;
        }

        private boolean advance() {
            if (atlases.get(nextAtlas).advance()) {
                nextAtlas++;
            }
            return nextAtlas == atlases.size();
        }

        @Override
        public void close() {
            atlases.forEach(StagedAtlas::close);
        }
    }

    private static final class StagedAtlas implements AutoCloseable {
        private final TextureAtlas target;
        private final SpriteLoader.Preparations preparations;
        private final List<TextureAtlasSprite> sprites;
        private TextureAtlas staging;
        private int nextSprite;

        private StagedAtlas(TextureAtlas target, SpriteLoader.Preparations preparations, List<TextureAtlasSprite> sprites) {
            this.target = target;
            this.preparations = preparations;
            this.sprites = sprites;
        }

        private int uploadCount() {
            return sprites.size() * (preparations.mipLevel() + 1);
        }

        private boolean advance() {
            if (staging == null) {
                staging = new TextureAtlas(target.location());
                ((KwrgAtlasStaging) staging).kwrg$allocate(preparations);
                return false;
            }

            int end = Math.min(nextSprite + SPRITES_PER_BATCH, sprites.size());
            ((KwrgAtlasStaging) staging).kwrg$uploadStaticBatch(sprites.subList(nextSprite, end));
            nextSprite = end;
            return nextSprite == sprites.size();
        }

        @Override
        public void close() {
            if (staging != null) {
                staging.close();
                staging = null;
            }
        }
    }
}
