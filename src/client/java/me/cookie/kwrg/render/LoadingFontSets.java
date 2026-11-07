package me.cookie.kwrg.render;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.mixin.accessor.FontManagerAccessor;
import me.cookie.kwrg.mixin.accessor.FontPreparationAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class LoadingFontSets {
    private static Build active;
    private static long generation;

    private LoadingFontSets() {
    }

    public static CompletableFuture<Object> stage(Minecraft client, FontManager manager, Object preparation) {
        if (!LoadingAtlasUpload.isLoadingWithoutWorld(client)) {
            return CompletableFuture.completedFuture(preparation);
        }
        if (active != null) {
            return active.manager == manager
                    ? active.finished.thenApply(unused -> preparation)
                    : CompletableFuture.completedFuture(preparation);
        }

        FontPreparationAccessor data = (FontPreparationAccessor) preparation;
        var definitions = data.kwrg$getDefinitions();
        if (definitions.size() < 2 || !definitions.containsKey(Minecraft.DEFAULT_FONT)) {
            return CompletableFuture.completedFuture(preparation);
        }

        active = new Build(manager, preparation, definitions.entrySet().iterator(),
                FontManagerAccessor.kwrg$getFontOptions(client.options), ++generation);
        return active.ready;
    }

    public static boolean step(Minecraft client) {
        Build build = active;
        if (build == null || build.completionQueued) {
            return false;
        }
        if (!LoadingAtlasUpload.isLoadingWithoutWorld(client)) {
            build.clearSets();
            build.queueApply(client);
            return true;
        }

        try {
            build.createNext();
        } catch (RuntimeException exception) {
            build.clearSets();
            CookieMain.LOGGER.warn("Staged font creation failed. Using the normal font reload.", exception);
            build.queueApply(client);
            return true;
        }
        if (!build.remaining.hasNext()) {
            build.queueApply(client);
        }
        return true;
    }

    public static void publish(FontManager manager, Object preparation, Consumer<Object> apply) {
        Build build = active;
        if (build == null || build.manager != manager || build.preparation != preparation) {
            apply.accept(preparation);
            return;
        }
        try {
            build.publishing = true;
            apply.accept(preparation);
        } finally {
            active = null;
            build.clearSets();
            build.finished.complete(null);
        }
    }

    public static FontSet take(FontManager manager, Identifier id, List<GlyphProvider.Conditional> providers,
                               Set<FontOption> options) {
        Build build = active;
        if (build == null || !build.publishing || build.manager != manager || !build.options.equals(options)) {
            return null;
        }
        StagedFont staged = build.sets.get(id);
        if (staged == null || !staged.providers().equals(providers)) {
            return null;
        }
        build.sets.remove(id);
        return staged.font();
    }

    public static void close(FontManager manager) {
        Build build = active;
        if (build != null && build.manager == manager && !build.publishing) {
            active = null;
            build.ready.cancel(false);
            build.finished.cancel(false);
            build.clearSets();
            ((FontPreparationAccessor) build.preparation).kwrg$getProviders().forEach(GlyphProvider::close);
        }
    }

    private record StagedFont(List<GlyphProvider.Conditional> providers, FontSet font) {
    }

    private static final class Build {
        private final FontManager manager;
        private final Object preparation;
        private final Iterator<Map.Entry<Identifier, List<GlyphProvider.Conditional>>> remaining;
        private final Set<FontOption> options;
        private final long generation;
        private final Map<Identifier, StagedFont> sets = new HashMap<>();
        private final CompletableFuture<Object> ready = new CompletableFuture<>();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private boolean completionQueued;
        private boolean publishing;

        private Build(FontManager manager, Object preparation,
                      Iterator<Map.Entry<Identifier, List<GlyphProvider.Conditional>>> remaining,
                      Set<FontOption> options, long generation) {
            this.manager = manager;
            this.preparation = preparation;
            this.remaining = remaining;
            this.options = options;
            this.generation = generation;
        }

        private void createNext() {
            var entry = remaining.next();
            Identifier id = entry.getKey();

            Identifier texturePrefix = Identifier.fromNamespaceAndPath(CookieMain.MOD_ID,
                    "staged_font/" + generation + "/" + id.getNamespace() + "/" + id.getPath());
            List<GlyphProvider.Conditional> providers = List.copyOf(Lists.reverse(entry.getValue()));
            FontSet font = new FontSet(new GlyphStitcher(((FontManagerAccessor) manager).kwrg$getTextureManager(), texturePrefix));
            try {
                font.reload(providers, options);
            } catch (RuntimeException | Error exception) {
                font.close();
                throw exception;
            }
            sets.put(id, new StagedFont(providers, font));
        }

        private void queueApply(Minecraft client) {
            completionQueued = true;
            client.schedule(() -> ready.complete(preparation));
        }

        private void clearSets() {
            sets.values().forEach(staged -> staged.font().close());
            sets.clear();
        }
    }
}
