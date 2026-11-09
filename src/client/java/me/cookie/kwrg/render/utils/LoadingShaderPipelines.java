package me.cookie.kwrg.render.utils;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.mixin.accessor.GlDeviceAccessor;
import me.cookie.kwrg.mixin.accessor.GpuDeviceAccessor;
import me.cookie.kwrg.mixin.accessor.ShaderSourceKeyAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class LoadingShaderPipelines {
    // Compilation, cache replacement and cleanup all run on the render thread.
    private static Compilation active;

    private LoadingShaderPipelines() {
    }

    public static CompletableFuture<Void> stage(ShaderManager.Configs configs, Runnable apply, Executor executor) {
        Minecraft client = Minecraft.getInstance();
        GpuDevice device = RenderSystem.tryGetDevice();
        if (!LoadingAtlasUpload.isLoadingWithoutWorld(client) || device == null
                || !(((GpuDeviceAccessor) device).kwrg$getBackend() instanceof GlDeviceAccessor gl)) {
            apply.run();
            return CompletableFuture.completedFuture(null);
        }

        if (active != null) {
            return active.completion.handle((result, failure) -> null)
                    .thenComposeAsync(unused -> stage(configs, apply, executor), executor);
        }

        Compilation batch = new Compilation(device, gl, configs);
        active = batch;
        batch.completion = batch.ready.thenRunAsync(() -> {
            try {
                batch.publishing = true;
                apply.run();
            } finally {
                active = null;
                batch.discard();
            }
        }, task -> client.schedule(() -> executor.execute(task)));
        return batch.completion;
    }

    public static boolean step(Minecraft client) {
        Compilation batch = active;
        if (batch == null || batch.ready.isDone()) {
            return false;
        }

        if (LoadingAtlasUpload.isLoadingWithoutWorld(client)) {
            try {
                boolean worked = batch.compileNext();
                if (batch.isDone()) {
                    batch.ready.complete(null);
                    return true;
                }
                return worked;
            } catch (RuntimeException exception) {
                CookieMain.LOGGER.warn("Could not stage shader compilation. Using the normal resource reload.", exception);
            }
        }
        batch.discard();
        batch.ready.complete(null);
        return true;
    }

    public static boolean install(GpuDevice device, ShaderManager.Configs configs, Runnable clearLiveCache) {
        Compilation batch = active;
        if (batch == null || !batch.publishing || batch.device != device || batch.configs != configs || batch.staged == null) {
            return false;
        }
        // The loading programs use bundled sources and can survive the resource pack change.
        batch.gl.kwrg$getPipelineCache().entrySet().removeIf(entry -> {
            if (!LoadingOverlayRenderer.isLoadingPipeline(entry.getKey())) {
                return false;
            }
            batch.staged.pipelines().put(entry.getKey(), entry.getValue());
            return true;
        });

        clearLiveCache.run();
        batch.staged.bind(batch.gl);
        batch.staged = null;
        return true;
    }

    public static void close() {
        Compilation batch = active;
        if (batch != null) {
            active = null;
            batch.ready.cancel(false);
            batch.completion.cancel(false);
            batch.discard();
        }
    }

    private record Cache(Map<RenderPipeline, GlRenderPipeline> pipelines, Map<?, GlShaderModule> shaders) {
        static Cache current(GlDeviceAccessor gl) {
            return new Cache(gl.kwrg$getPipelineCache(), gl.kwrg$getShaderCache());
        }

        void bind(GlDeviceAccessor gl) {
            gl.kwrg$setPipelineCache(pipelines);
            gl.kwrg$setShaderCache(shaders);
        }
    }

    private static final class Compilation {
        private final GpuDevice device;
        private final GlDeviceAccessor gl;
        private final ShaderManager.Configs configs;
        private final ShaderSource source;
        private final ParallelShaderLinks links = ParallelShaderLinks.create();
        private final Iterator<RenderPipeline> pipelines = RenderPipelines.getStaticPipelines().iterator();
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private Cache staged = new Cache(new IdentityHashMap<>(), new HashMap<>());
        private CompletableFuture<Void> completion;
        private boolean publishing;

        private Compilation(GpuDevice device, GlDeviceAccessor gl, ShaderManager.Configs configs) {
            this.device = device;
            this.gl = gl;
            this.configs = configs;
            Map<ShaderType, Map<Identifier, String>> sources = new HashMap<>();
            Map<?, String> entries = configs.shaderSources();
            entries.forEach((key, value) -> {
                ShaderSourceKeyAccessor shader = (ShaderSourceKeyAccessor) key;
                sources.computeIfAbsent(shader.kwrg$getType(), unused -> new HashMap<>()).put(shader.kwrg$getId(), value);
            });
            source = (id, type) -> sources.getOrDefault(type, Map.of()).get(id);
        }

        private boolean compileNext() {
            RenderPipeline pipeline = links == null ? null : links.readyPipeline();
            if (pipeline == null) {
                if (!pipelines.hasNext() || (links != null && !links.canSubmit())) {
                    return false;
                }
                pipeline = pipelines.next();
            }

            Cache live = Cache.current(gl);
            staged.bind(gl);
            try {
                if (links == null) {
                    device.precompilePipeline(pipeline, source);
                } else {
                    RenderPipeline next = pipeline;
                    links.compile(next, () -> device.precompilePipeline(next, source));
                }
            } finally {
                live.bind(gl);
            }
            return true;
        }

        private boolean isDone() {
            return !pipelines.hasNext() && (links == null || links.isIdle());
        }

        private void discard() {
            if (links != null) {
                links.close();
            }

            if (staged != null) {
                Cache live = Cache.current(gl);
                staged.bind(gl);
                try {
                    device.clearPipelineCache();
                } finally {
                    live.bind(gl);
                    staged = null;
                }
            }
        }
    }
}
