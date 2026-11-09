package me.cookie.kwrg.render.utils;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.gui.util.FontManagerHelper;
import me.cookie.kwrg.mixin.accessor.FontManagerAccessor;
import me.cookie.kwrg.mixin.accessor.FontPreparationAccessor;
import me.cookie.kwrg.mixin.accessor.FontSetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class LoadingFontSets {
    private static Build active;
    private static long generation;

    private LoadingFontSets() {
    }

    public static CompletableFuture<Object> stage(Minecraft client, FontManager manager, Object preparation,
                                                  Executor preparationExecutor) {
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
        if (!definitions.containsKey(Minecraft.DEFAULT_FONT)) {
            return CompletableFuture.completedFuture(preparation);
        }

        active = new Build(manager, preparation, definitions.entrySet().iterator(),
                FontManagerAccessor.kwrg$getFontOptions(client.options), ++generation, preparationExecutor);
        return active.ready;
    }

    public static boolean step(Minecraft client) {
        Build build = active;
        if (build == null || build.completionQueued) {
            return false;
        }
        if (build.pending != null && !build.pending.selected().isDone()) {
            return false;
        }
        if (!LoadingAtlasUpload.isLoadingWithoutWorld(client)) {
            build.clearSets();
            build.queueApply(client);
            return true;
        }

        try {
            if (build.pending == null) {
                if (build.remaining.hasNext()) {
                    build.startNext();
                } else {
                    build.startGuiFont();
                }
            } else {
                build.finishPending();
            }
        } catch (RuntimeException exception) {
            if (build.guiFontStarted) {
                build.clearPending();
                CookieMain.LOGGER.warn("Could not stage the external GUI font. Using the normal GUI font reload.", exception);
            } else {
                build.clearSets();
                CookieMain.LOGGER.warn("Staged font creation failed. Using the normal resource reload.", exception);
            }
            build.queueApply(client);
            return true;
        }
        if (build.pending == null && !build.remaining.hasNext()
                && (build.guiFontName == null || build.guiFontStarted)) {
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

    public static GuiFont takeGuiFont(FontManager manager, String name) {
        Build build = active;
        if (build == null || !build.publishing || build.manager != manager || !name.equals(build.guiFontName)) {
            return null;
        }
        GuiFont gui = build.guiFont;
        build.guiFont = null;
        return gui;
    }

    public static void close(FontManager manager) {
        Build build = active;
        if (build != null && build.manager == manager && !build.publishing) {
            active = null;
            build.ready.cancel(false);
            build.finished.cancel(false);
            if (build.pending != null) {
                // Shutdown must let the CPU scan release the providers before closing them.
                build.pending.selected().handle((selected, failure) -> null).join();
            }
            build.clearSets();
            ((FontPreparationAccessor) build.preparation).kwrg$getProviders().forEach(GlyphProvider::close);
        }
    }

    private record StagedFont(List<GlyphProvider.Conditional> providers, FontSet font) {
    }

    public record GuiFont(FontSet font, GlyphProvider provider) {
    }

    private record SelectedFont(List<GlyphProvider.Conditional> providers, List<GlyphProvider> active,
                                GlyphProvider externalProvider) {
    }

    private record PendingFont(Identifier id, FontSet font, CompletableFuture<SelectedFont> selected) {
    }

    private static final class Build {
        private final FontManager manager;
        private final Object preparation;
        private final Iterator<Map.Entry<Identifier, List<GlyphProvider.Conditional>>> remaining;
        private final Set<FontOption> options;
        private final long generation;
        private final Executor preparationExecutor;
        private final String guiFontName;
        private final Map<Identifier, StagedFont> sets = new HashMap<>();
        private final CompletableFuture<Object> ready = new CompletableFuture<>();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private PendingFont pending;
        private GuiFont guiFont;
        private boolean guiFontStarted;
        private boolean completionQueued;
        private boolean publishing;

        private Build(FontManager manager, Object preparation,
                      Iterator<Map.Entry<Identifier, List<GlyphProvider.Conditional>>> remaining,
                      Set<FontOption> options, long generation, Executor preparationExecutor) {
            this.manager = manager;
            this.preparation = preparation;
            this.remaining = remaining;
            this.options = options;
            this.generation = generation;
            this.preparationExecutor = preparationExecutor;
            this.guiFontName = FontManagerHelper.configuredExternalFont();
        }

        private void startNext() {
            var entry = remaining.next();
            List<GlyphProvider.Conditional> providers = List.copyOf(Lists.reverse(entry.getValue()));
            startFont(entry.getKey(), providers, options, null);
        }

        private void startGuiFont() {
            guiFontStarted = true;
            startFont(FontManagerHelper.GUI_FONT_ID, sets.get(Minecraft.DEFAULT_FONT).providers(), Set.of(), guiFontName);
        }

        private void startFont(Identifier id, List<GlyphProvider.Conditional> providers, Set<FontOption> options,
                               String externalFont) {
            Identifier texturePrefix = Identifier.fromNamespaceAndPath(CookieMain.MOD_ID,
                    "staged_font/" + generation + "/" + (externalFont == null ? "resource/" : "external/")
                            + id.getNamespace() + "/" + id.getPath());
            FontSet font = new FontSet(new GlyphStitcher(((FontManagerAccessor) manager).kwrg$getTextureManager(), texturePrefix));
            FontSetAccessor access = (FontSetAccessor) font;
            try {
                access.kwrg$resetTextures();
                // Vanilla selection only scans glyph providers and fills this unpublished FontSet's width buckets.
                var selected = CompletableFuture.supplyAsync(
                        () -> selectFont(access, providers, options, externalFont), preparationExecutor);
                pending = new PendingFont(id, font, selected);
            } catch (RuntimeException | Error exception) {
                font.close();
                throw exception;
            }
        }

        private void finishPending() {
            FontSet font = pending.font();
            FontSetAccessor access = (FontSetAccessor) font;
            SelectedFont selected = pending.selected().join();
            access.kwrg$setAllProviders(selected.providers());
            access.kwrg$setActiveProviders(selected.active());
            if (selected.externalProvider() == null) {
                sets.put(pending.id(), new StagedFont(selected.providers(), font));
            } else {
                guiFont = new GuiFont(font, selected.externalProvider());
            }
            pending = null;
        }

        private void queueApply(Minecraft client) {
            completionQueued = true;
            client.schedule(() -> ready.complete(preparation));
        }

        private void clearSets() {
            clearPending();
            if (guiFont != null) {
                guiFont.font().close();
                guiFont.provider().close();
                guiFont = null;
            }
            sets.values().forEach(staged -> staged.font().close());
            sets.clear();
        }

        private void clearPending() {
            if (pending != null) {
                if (!pending.selected().isCompletedExceptionally()) {
                    GlyphProvider provider = pending.selected().join().externalProvider();
                    if (provider != null) {
                        provider.close();
                    }
                }
                pending.font().close();
                pending = null;
            }
        }
    }

    private static SelectedFont selectFont(FontSetAccessor access, List<GlyphProvider.Conditional> providers,
                                           Set<FontOption> options, String externalFont) {
        if (externalFont == null) {
            return new SelectedFont(providers, access.kwrg$selectProviders(providers, options), null);
        }

        GlyphProvider external;
        try {
            external = FontManagerHelper.loadExternalProvider(FontManagerHelper.ensureFontDir().resolve(externalFont));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        try {
            List<GlyphProvider.Conditional> combined = new ArrayList<>(providers.size() + 1);
            combined.add(new GlyphProvider.Conditional(external, FontOption.Filter.ALWAYS_PASS));
            combined.addAll(providers);
            return new SelectedFont(combined, access.kwrg$selectProviders(combined, options), external);
        } catch (RuntimeException | Error exception) {
            external.close();
            throw exception;
        }
    }
}
