package me.cookie.kwrg.render.utils;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.mixin.accessor.GlProgramAccessor;
import net.minecraft.client.renderer.ShaderManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.KHRParallelShaderCompile;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ParallelShaderLinks implements AutoCloseable {
    private static final int MAX_IN_FLIGHT = 6;
    private static final long FIRST_POLL_DELAY_NANOS = 25_000_000L;
    private static final long RETRY_DELAY_NANOS = 8_000_000L;
    private static ParallelShaderLinks current;

    private final Map<RenderPipeline, PendingLink> pending = new LinkedHashMap<>();
    private RenderPipeline compiling;

    static ParallelShaderLinks create() {
        var capabilities = GL.getCapabilities();
        return capabilities.GL_KHR_parallel_shader_compile || capabilities.GL_ARB_parallel_shader_compile
                ? new ParallelShaderLinks() : null;
    }

    public static ParallelShaderLinks current() {
        return current;
    }

    boolean canSubmit() {
        return pending.size() < MAX_IN_FLIGHT;
    }

    boolean isIdle() {
        return pending.isEmpty();
    }

    RenderPipeline readyPipeline() {
        long now = System.nanoTime();
        for (var entry : pending.entrySet()) {
            PendingLink link = entry.getValue();
            if (now < link.pollAfter()) {
                continue;
            }

            if (isComplete(link.program())) {
                return entry.getKey();
            }
            entry.setValue(new PendingLink(link.program(), now + RETRY_DELAY_NANOS));
        }
        return null;
    }

    void compile(RenderPipeline pipeline, Runnable compile) {
        compiling = pipeline;
        current = this;
        try {
            compile.run();
        } catch (LinkPending ignored) {
            // Keep the native handle until a later frame observes link completion.
        } finally {
            current = null;
            compiling = null;
        }
    }

    public GlProgram link(GlShaderModule vertex, GlShaderModule fragment, VertexFormat format, String label)
            throws ShaderManager.CompilationException {
        PendingLink completed = pending.remove(compiling);
        int program = completed == null ? GlStateManager.glCreateProgram() : completed.program();
        if (program <= 0) {
            throw new ShaderManager.CompilationException("Could not create shader program " + label);
        }

        boolean retained = false;
        try {
            if (completed == null) {
                int attribute = 0;
                for (String name : format.getElementAttributeNames()) {
                    GlStateManager._glBindAttribLocation(program, attribute++, name);
                }
                GlStateManager.glAttachShader(program, vertex.getShaderId());
                GlStateManager.glAttachShader(program, fragment.getShaderId());
                GlStateManager.glLinkProgram(program);
                // Some drivers still spend time in a completion query issued immediately after link.
                pending.put(compiling, new PendingLink(program, System.nanoTime() + FIRST_POLL_DELAY_NANOS));
                retained = true;
                throw LinkPending.INSTANCE;
            }

            int linked = GlStateManager.glGetProgrami(program, GL20C.GL_LINK_STATUS);
            String log = GlStateManager.glGetProgramInfoLog(program, 32768);
            if (linked == 0 || log.contains("Failed for unknown reason")) {
                throw new ShaderManager.CompilationException("Could not link " + label + ": " + log);
            }

            if (!log.isEmpty()) {
                CookieMain.LOGGER.info("Info log when linking program {}: {}", label, log);
            }

            GlProgram result = GlProgramAccessor.kwrg$create(program, label);
            retained = true;
            return result;
        } finally {
            if (!retained) {
                GlStateManager.glDeleteProgram(program);
            }
        }
    }

    private static boolean isComplete(int program) {
        // KHR and ARB expose the same completion-status token.
        return GlStateManager.glGetProgrami(program, KHRParallelShaderCompile.GL_COMPLETION_STATUS_KHR) != 0;
    }

    @Override
    public void close() {
        pending.values().forEach(link -> GlStateManager.glDeleteProgram(link.program()));
        pending.clear();
    }

    private record PendingLink(int program, long pollAfter) {
    }

    private static final class LinkPending extends RuntimeException {
        private static final LinkPending INSTANCE = new LinkPending();

        private LinkPending() {
            super(null, null, false, false);
        }
    }
}
