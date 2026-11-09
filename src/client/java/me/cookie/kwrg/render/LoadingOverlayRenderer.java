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
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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
    private static final int MENU_VOXELS = 224;
    private static final int MODEL_VOXELS = 9 * 9 * 11 + 96;
    private static final int BEACON_VOXELS = 13;
    private static final int PORTAL_VOXELS = 96;
    private static final int BOARD_LINES = 18;
    private static final int RING_LINES = 96;
    private static final int FRAME_EDGES = 12;
    private static final int GRID_LINES = 10 * (GRID_SIZE - 1);
    private static final float BUILD_START = 0.30f;
    private static final float BUILD_END = 0.80f;
    private static final int CUBE_INDICES = 36;
    private static final int FRAME_UNIFORM_BYTES = 304;
    private static final String FRAME_UNIFORM = "LoadingFrame";
    private static final ColorTargetState BLEND = new ColorTargetState(
            Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR);

    private static final RenderPipeline BACKGROUND = pipeline("loading_background", "loading_background")
            .withCull(false)
            .build();
    private static final RenderPipeline TITLE_WARP = pipeline("title_warp", "loading_background")
            .withCull(false)
            .withShaderDefine("PANORAMA_WARP")
            .withSampler("PanoramaSampler")
            .build();
    private static final DepthStencilState SOLID_DEPTH = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
    private static final RenderPipeline VOXELS = pipeline("loading_voxels", "loading_voxels")
            .withShaderDefine("ASSEMBLY_INFLOW", ASSEMBLY_INFLOW)
            .withShaderDefine("MENU_VOXELS", MENU_VOXELS)
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
    private static final RenderPipeline TITLE_PROJECTION = pipeline("title_projection", "title_projection")
            .withDepthStencilState(SOLID_DEPTH)
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
    static final ShaderSource SHADERS = (id, type) -> SOURCES.computeIfAbsent(type.idConverter().idToFile(id), file ->
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
    private static TitleFrame title;
    private static float loadingFade;
    private static float titleTimeOffset;
    private static boolean titleDrawn;
    private static MappableRingBuffer uniforms;
    private static GpuTexture depth;
    private static GpuTextureView depthView;
    private static GpuTexture panorama;
    private static GpuTextureView panoramaView;
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

    public static boolean prepareBrowser() {
        return prepare() && RenderSystem.getDevice().precompilePipeline(TITLE_PROJECTION, SHADERS).isValid();
    }

    public static boolean prepareSwitch() {
        return prepare() && RenderSystem.getDevice().precompilePipeline(TITLE_WARP, SHADERS).isValid();
    }

    public static float panoramaOverlayAlpha() {
        return title == null ? 1.0f : 1.0f - smooth(0.0f, 0.38f, title.mode().collapse());
    }

    public static void preload(RenderTarget target) {
        for (RenderPipeline pipeline : PIPELINES) {
            SHADERS.get(pipeline.getVertexShader(), ShaderType.VERTEX);
            SHADERS.get(pipeline.getFragmentShader(), ShaderType.FRAGMENT);
        }
        SHADERS.get(TITLE_PROJECTION.getVertexShader(), ShaderType.VERTEX);
        SHADERS.get(TITLE_PROJECTION.getFragmentShader(), ShaderType.FRAGMENT);

        GpuDevice device = RenderSystem.tryGetDevice();
        if (device != null && target != null && target.width > 0 && target.height > 0) {
            prepareBuffers(device, target.width, target.height);
            RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES).getBuffer(CUBE_INDICES);
        }
    }

    public static void beginFrame() {
        frame = null;
        title = null;
        loadingFade = Float.NaN;
        titleDrawn = false;
    }

    public static void setLoadingFade(float fade) {
        loadingFade = fade;
    }

    public static float titleReveal() {
        return Float.isNaN(loadingFade) ? 1.0f : smooth(0.14f, 0.82f, loadingFade - 1.0f);
    }

    public static void submitTitle(TitleFrame scene) {
        title = scene;
    }

    public static void renderTitle(RenderTarget target) {
        if (title == null || (!Float.isNaN(loadingFade) && loadingFade < 1.0f)) {
            return;
        }
        Frame loading = frame;
        frame = null;
        float pan = loading == null ? 1.0f : smooth(0.02f, 0.90f, loading.burst());
        if (loading != null) {
            titleTimeOffset = loading.time() - title.time();
        }
        float time = title.time() + titleTimeOffset;
        float mouseX = loading == null ? title.mouseX() : mix(loading.mouseX(), title.mouseX(), pan);
        float mouseY = loading == null ? title.mouseY() : mix(loading.mouseY(), title.mouseY(), pan);
        Frame combined = new Frame(time, 1.0f, 1.0f, loading == null ? title.mode().burst() : loading.burst(),
                loading == null ? 0.0f : loading.charge(), mouseX, mouseY, 0.0f);
        drawScene(target, combined, title, pan);
        titleDrawn = true;
    }

    private static float smooth(float from, float to, float value) {
        float t = Math.clamp((value - from) / (to - from), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float mix(float from, float to, float t) {
        return from + (to - from) * t;
    }

    public static void submit(float time, float progress, float opacity, float burst, float charge,
                              float mouseX, float mouseY, float mouseMotion) {
        frame = new Frame(time, progress, opacity, burst, charge, mouseX, mouseY, mouseMotion);
    }

    public static void render(RenderTarget target) {
        Frame current = frame;
        frame = null;

        if (titleDrawn) {
            return;
        }
        if (current == null) {
            titleTimeOffset = 0.0f;
            close();
            return;
        }

        if (current.burst() >= 0.98f) {
            return;
        }

        drawScene(target, current, null, 0.0f);
    }

    private static void drawScene(RenderTarget target, Frame current, TitleFrame menu, float pan) {
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
        SwitchFrame mode = menu == null ? SwitchFrame.NONE : menu.mode();
        if (menu != null) {
            particles = MENU_VOXELS + (bursting ? SHELL_VOXELS : 0)
                    + (mode.gather() >= 0.0f && mode.gather() < 1.0f ? CORE_FRAGMENTS : 0);
        }

        int ringLines = menu != null && menu.settings() * pan > 0.004f ? RING_LINES : 0;
        BrowserFrame browser = menu == null ? BrowserFrame.NONE : menu.browser();
        int boardLines = browser.camera() > 0.0f && !browser.network() && browser.model() == 0 ? BOARD_LINES : 0;

        GpuDevice device = RenderSystem.getDevice();
        boolean drawProjection = browser.camera() > 0.0f && device.precompilePipeline(TITLE_PROJECTION, SHADERS).isValid();
        prepareBuffers(device, target.width, target.height);

        var encoder = device.createCommandEncoder();
        boolean warp = mode.panoramaScale() > 0.0f;
        if (warp) copyPanorama(device, encoder, target);

        GpuBuffer uniform = uniforms.currentBuffer();

        try (var mapped = encoder.mapBuffer(uniform, false, true)) {
            Std140Builder data = Std140Builder.intoBuffer(mapped.data())
                    .putVec4(current.time(), current.progress(), current.opacity(), current.burst())
                    .putVec4(target.width, target.height, current.mouseX(), current.mouseY())
                    .putVec4(current.charge(), current.mouseMotion(), layer - rows, ringLines)
                    .putIVec4(rows, active, inflow, lines)
                    .putVec4(menu == null ? 0.0f : 1.0f, pan,
                            menu == null ? 0.0f : menu.orbit() * pan, menu == null ? 0.0f : menu.compact())
                    .putVec4(menu == null ? 0.0f : menu.solo() * pan, menu == null ? 0.0f : menu.network() * pan,
                            menu == null ? 0.0f : menu.settings() * pan, menu == null ? 0.0f : menu.quit() * pan)
                    .putVec4(browser.camera(), browser.network() ? 1.0f : 0.0f, browser.model(), browser.growth())
                    .putVec4(browser.dive(), browser.selected(), browser.count(), boardLines)
                    .putVec4(mode.approach(), mode.gather(), mode.panoramaScale(), mode.collapse())
                    .putVec4(mode.distortion(), mode.shake(), mode.lockTime(), 0.0f)
                    .putVec4(browser.launch(), browser.launchSlot(), 0.0f, 0.0f);
            int[] signals = browser.signals();
            if (browser.count() > 0) {
                for (int i = 0; i < signals.length; i += 4) {
                    data.putIVec4(signals[i], signals[i + 1], signals[i + 2], signals[i + 3]);
                }
            }
        }

        var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES);
        GpuBuffer cubeIndices = indices.getBuffer(CUBE_INDICES);
        try (RenderPass pass = encoder.createRenderPass(() -> "KWRG loading overlay", target.getColorTextureView(),
                OptionalInt.empty(), depthView, OptionalDouble.of(1.0))) {
            if (menu != null || current.burst() < 0.69f) {
                usePipeline(pass, warp ? TITLE_WARP : BACKGROUND, uniform);
                if (warp) {
                    pass.bindTexture("PanoramaSampler", panoramaView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                }
                pass.draw(0, 3);
            }

            boolean showScene = !warp || mode.gather() >= 0.0f || mode.burst() < 1.0f;
            boolean switchBurst = mode.burst() > 0.0f;
            if (showScene && rows > 0 && !switchBurst && (menu != null || !bursting)) {
                usePipeline(pass, SHELL, uniform);
                pass.draw(0, CUBE_INDICES);
            }

            pass.setIndexBuffer(cubeIndices, indices.type());
            if (showScene) {
                usePipeline(pass, VOXELS, uniform);
                pass.drawIndexed(0, 0, CUBE_INDICES, particles);
            }

            if (drawProjection) {
                int count = browser.network()
                        ? browser.count() * BEACON_VOXELS + (browser.selected() >= 0 ? PORTAL_VOXELS : 0)
                        : MODEL_VOXELS;
                usePipeline(pass, TITLE_PROJECTION, uniform);
                pass.drawIndexed(0, 0, CUBE_INDICES, LAYER_VOXELS + count);
            }
            if (showScene && (menu != null || current.burst() < 0.46f)) {
                usePipeline(pass, HOLOGRAM, uniform);
                pass.drawIndexed(0, 0, 6, lines + ringLines + boardLines + ATMOSPHERE_VOXELS);
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

    private static void copyPanorama(GpuDevice device, CommandEncoder encoder, RenderTarget target) {
        GpuTexture source = target.getColorTexture();
        if (panorama == null || panorama.getWidth(0) != target.width || panorama.getHeight(0) != target.height
                || panorama.getFormat() != source.getFormat()) {
            closePanorama();
            panorama = device.createTexture("KWRG title panorama", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    source.getFormat(), target.width, target.height, 1, 1);
            panoramaView = device.createTextureView(panorama);
        }
        encoder.copyTextureToTexture(source, panorama, 0, 0, 0, 0, 0, target.width, target.height);
    }

    private static void closePanorama() {
        if (panorama != null) {
            panoramaView.close();
            panorama.close();
            panoramaView = null;
            panorama = null;
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
        closePanorama();

        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    private record Frame(float time, float progress, float opacity, float burst, float charge,
                         float mouseX, float mouseY, float mouseMotion) {
    }

    public record TitleFrame(float time, float mouseX, float mouseY, float orbit, float compact,
                             float solo, float network, float settings, float quit, BrowserFrame browser, SwitchFrame mode) {
    }

    public record SwitchFrame(float approach, float burst, float gather, float panoramaScale, float collapse,
                              float distortion, float shake, float lockTime) {
        public static final SwitchFrame NONE = new SwitchFrame(0.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public record BrowserFrame(float camera, boolean network, int model, float growth, float dive,
                               int count, int selected, int[] signals, float launch, int launchSlot) {
        public static final BrowserFrame NONE = new BrowserFrame(0.0f, false, 0, 0.0f, 0.0f, 0, -1, new int[0], 0.0f, -1);
    }
}
