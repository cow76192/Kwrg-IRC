package me.cookie.kwrg.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cookie.kwrg.CookieMain;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class LoadingOverlayRenderer {
    private static final int GRID_SIZE = 16;
    private static final int LAYER_VOXELS = GRID_SIZE * GRID_SIZE;
    private static final int SHELL_VOXELS = 2 * LAYER_VOXELS + (GRID_SIZE - 2) * (4 * GRID_SIZE - 4);
    private static final int CORE_FRAGMENTS = 384;
    private static final int EARLY_INFLOW = 1152;
    private static final int ASSEMBLY_INFLOW = 640;
    private static final int STREAM_VOXELS = 112;
    private static final int ATMOSPHERE_VOXELS = 64;
    private static final int FRAME_EDGES = 12;
    private static final int GRID_LINES = 10 * (GRID_SIZE - 1);
    private static final float BUILD_START = 0.30f;
    private static final float BUILD_END = 0.80f;
    private static final int CUBE_INDICES = 36;
    private static final int FRAME_UNIFORM_BYTES = 64;
    private static final String FRAME_UNIFORM = "LoadingFrame";
    private static final ColorTargetState BLEND = new ColorTargetState(
            Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR);

    private static final RenderPipeline BACKGROUND = pipeline("loading_background", "loading_background")
            .withCull(false)
            .build();
    private static final DepthStencilState SOLID_DEPTH = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
    private static final RenderPipeline VOXELS = pipeline("loading_voxels", "loading_voxels")
            .withShaderDefine("ASSEMBLY_INFLOW", ASSEMBLY_INFLOW)
            .withDepthStencilState(SOLID_DEPTH)
            .build();
    private static final RenderPipeline SHELL = pipeline("loading_shell", "loading_structure")
            .withDepthStencilState(SOLID_DEPTH)
            .build();
    private static final RenderPipeline HOLOGRAM = pipeline("loading_hologram", "loading_structure")
            .withShaderDefine("HOLOGRAM")
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    private static final List<RenderPipeline> PIPELINES = List.of(BACKGROUND, VOXELS, SHELL, HOLOGRAM);

    private static final GlslPreprocessor PREPROCESSOR = new GlslPreprocessor() {
        @Override
        public String applyImport(boolean relative, String path) {
            Identifier id = Identifier.tryParse(path);
            if (relative || id == null || !CookieMain.MOD_ID.equals(id.getNamespace())) {
                return null;
            }
            return readShader(id.withPath(name -> "shaders/include/" + name));
        }
    };

    private static final Map<Identifier, Optional<String>> SOURCES = new HashMap<>();
    // The bundled sources are immutable, including across Blaze3D's resource-reload pipeline cache clear.
    private static final ShaderSource SHADERS = (id, type) -> SOURCES.computeIfAbsent(type.idConverter().idToFile(id), file ->
            Optional.ofNullable(readShader(file)).map(source -> String.join("", PREPROCESSOR.process(source)))).orElse(null);

    private static String readShader(Identifier file) {
        String path = "/assets/" + file.getNamespace() + "/" + file.getPath();
        try (var stream = LoadingOverlayRenderer.class.getResourceAsStream(path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CookieMain.LOGGER.warn("Could not read loading overlay shader {}", path, exception);
            return null;
        }
    }

    private static Frame frame;
    private static MappableRingBuffer uniforms;
    private static GpuTexture depth;
    private static GpuTextureView depthView;
    private static boolean shaderFailureLogged;

    private LoadingOverlayRenderer() {
    }

    private static RenderPipeline.Builder pipeline(String name, String shader) {
        Identifier shaderId = Identifier.fromNamespaceAndPath(CookieMain.MOD_ID, "core/" + shader);
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(CookieMain.MOD_ID, "pipeline/" + name))
                .withVertexShader(shaderId)
                .withFragmentShader(shaderId)
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withCull(true)
                .withShaderDefine("GRID_SIZE", GRID_SIZE)
                .withShaderDefine("BUILD_START", BUILD_START)
                .withShaderDefine("BUILD_END", BUILD_END)
                .withUniform(FRAME_UNIFORM, UniformType.UNIFORM_BUFFER)
                .withColorTargetState(BLEND);
    }

    public static boolean prepare() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return false;
        }

        boolean ready = device.precompilePipeline(BACKGROUND, SHADERS).isValid()
                && device.precompilePipeline(VOXELS, SHADERS).isValid()
                && device.precompilePipeline(SHELL, SHADERS).isValid()
                && device.precompilePipeline(HOLOGRAM, SHADERS).isValid();

        if (!ready && !shaderFailureLogged) {
            CookieMain.LOGGER.warn("Loading overlay shaders are unavailable. Using the vanilla overlay.");
        }

        shaderFailureLogged = !ready;
        return ready;
    }

    public static void preload(RenderTarget target) {
        for (RenderPipeline pipeline : PIPELINES) {
            SHADERS.get(pipeline.getVertexShader(), ShaderType.VERTEX);
            SHADERS.get(pipeline.getFragmentShader(), ShaderType.FRAGMENT);
        }

        GpuDevice device = RenderSystem.tryGetDevice();
        if (device != null && target != null && target.width > 0 && target.height > 0) {
            prepareBuffers(device, target.width, target.height);
            RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES).getBuffer(CUBE_INDICES);
        }
    }

    public static void beginFrame() {
        frame = null;
    }

    public static void submit(float time, float progress, float opacity, float burst, float charge,
                              float mouseX, float mouseY, float mouseMotion) {
        frame = new Frame(time, progress, opacity, burst, charge, mouseX, mouseY, mouseMotion);
    }

    public static void render(RenderTarget target) {
        Frame current = frame;
        frame = null;

        if (current == null) {
            close();
            return;
        }

        if (current.burst() >= 0.98f) {
            return;
        }

        float progress = current.progress();
        float layer = (progress - BUILD_START) * GRID_SIZE / (BUILD_END - BUILD_START);
        int rows = Math.clamp((int) layer, 0, GRID_SIZE);
        boolean bursting = current.burst() > 0.0f;
        boolean building = progress >= BUILD_START && progress < BUILD_END;
        int active = building ? LAYER_VOXELS : 0;
        int inflow = progress < BUILD_START ? EARLY_INFLOW : building ? ASSEMBLY_INFLOW : 0;
        int stream = progress >= 0.79f ? STREAM_VOXELS : 0;
        int lines = progress >= 0.17f ? FRAME_EDGES + (building ? GRID_LINES : 0) : 0;
        int particles = bursting ? SHELL_VOXELS + CORE_FRAGMENTS : active + inflow + stream;

        GpuDevice device = RenderSystem.getDevice();
        prepareBuffers(device, target.width, target.height);
        var encoder = device.createCommandEncoder();
        GpuBuffer uniform = uniforms.currentBuffer();

        try (var mapped = encoder.mapBuffer(uniform, false, true)) {
            Std140Builder.intoBuffer(mapped.data())
                    .putVec4(current.time(), current.progress(), current.opacity(), current.burst())
                    .putVec4(target.width, target.height, current.mouseX(), current.mouseY())
                    .putVec4(current.charge(), current.mouseMotion(), layer - rows, 0.0f)
                    .putIVec4(rows, active, inflow, lines);
        }

        var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES);
        GpuBuffer cubeIndices = indices.getBuffer(CUBE_INDICES);
        try (RenderPass pass = encoder.createRenderPass(() -> "KWRG loading overlay", target.getColorTextureView(),
                OptionalInt.empty(), depthView, OptionalDouble.of(1.0))) {
            if (current.burst() < 0.69f) {
                usePipeline(pass, BACKGROUND, uniform);
                pass.draw(0, 3);
            }

            if (rows > 0 && !bursting) {
                usePipeline(pass, SHELL, uniform);
                pass.draw(0, CUBE_INDICES);
            }

            pass.setIndexBuffer(cubeIndices, indices.type());
            usePipeline(pass, VOXELS, uniform);
            pass.drawIndexed(0, 0, CUBE_INDICES, particles);

            if (current.burst() < 0.46f) {
                usePipeline(pass, HOLOGRAM, uniform);
                pass.drawIndexed(0, 0, 6, lines + ATMOSPHERE_VOXELS);
            }
        }
        uniforms.rotate();
    }

    private static void usePipeline(RenderPass pass, RenderPipeline pipeline, GpuBuffer uniform) {
        pass.setPipeline(pipeline);
        pass.setUniform(FRAME_UNIFORM, uniform);
    }

    private static void prepareBuffers(GpuDevice device, int width, int height) {
        if (uniforms == null) {
            uniforms = new MappableRingBuffer(() -> "KWRG loading frame", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                    FRAME_UNIFORM_BYTES);
        }

        if (depth == null || depth.getWidth(0) != width || depth.getHeight(0) != height) {
            closeDepth();
            depth = device.createTexture("KWRG loading depth", GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.DEPTH32, width, height, 1, 1);
            depthView = device.createTextureView(depth);
        }
    }

    private static void closeDepth() {
        if (depth != null) {
            depthView.close();
            depth.close();
            depthView = null;
            depth = null;
        }
    }

    public static void close() {
        frame = null;
        closeDepth();

        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    private record Frame(float time, float progress, float opacity, float burst, float charge,
                         float mouseX, float mouseY, float mouseMotion) {
    }
}
