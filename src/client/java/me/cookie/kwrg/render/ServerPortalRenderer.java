package me.cookie.kwrg.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.gui.title.TitleBrowser;
import me.cookie.kwrg.gui.title.ServerRetry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

public final class ServerPortalRenderer {
    private static final String PORTAL_UNIFORM = "PortalFrame";
    private static final int SCENE_VOXELS = 128;
    private static final int AMBIENT_VOXELS = 40;
    private static final int DEBRIS_VOXELS = 14 * 4;
    private static final int TEAR_VOXELS = 10 * 3 * 2;
    private static final int CLOUD_VOXELS = 20;
    private static final int ARRIVAL_VOXELS = 320;
    private static final long BUILD_MS = 930L;
    private static final long IGNITE_MS = 450L;
    private static final long ENTRY_MS = 480L;
    private static final long FAILURE_SNAP_MS = 250L;
    private static final long RETRY_BUILD_MS = 750L;
    private static final long RETRY_IGNITION_MS = RETRY_BUILD_MS + 370L;
    private static final long RETRY_HANDOFF_MS = RETRY_BUILD_MS + 470L;
    private static final float RETURN_SECONDS = 0.80f;
    private static final Component BUILDING = Component.translatable("kwrg.portal.build");
    private static final Component LIGHTING = Component.translatable("kwrg.portal.ignite");
    private static final Component OPENING = Component.translatable("kwrg.portal.open");
    private static final Component CROSSING = Component.translatable("kwrg.portal.cross");
    private static final int CUBE_VERTICES = 36;
    private static final Identifier OBSIDIAN = Identifier.withDefaultNamespace("textures/block/obsidian.png");
    private static final Identifier CRYING_OBSIDIAN = Identifier.withDefaultNamespace("textures/block/crying_obsidian.png");
    private static final String OBSIDIAN_SAMPLER = "ObsidianSampler";
    private static final String CRYING_SAMPLER = "CryingObsidianSampler";
    private static final ColorTargetState TRANSLUCENT = new ColorTargetState(
            Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR);
    private static final DepthStencilState WRITE_DEPTH = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
    private static final RenderPipeline SKY = pipeline("server_portal_sky", "loading_background")
            .withCull(false).build();
    private static final RenderPipeline VOXELS = pipeline("server_portal_voxels", "server_portal_voxels")
            .withShaderDefine("PORTAL_AMBIENT_START", SCENE_VOXELS)
            .withShaderDefine("PORTAL_DEBRIS_START", SCENE_VOXELS + AMBIENT_VOXELS)
            .withShaderDefine("PORTAL_TEARS_START", SCENE_VOXELS + AMBIENT_VOXELS + DEBRIS_VOXELS)
            .withShaderDefine("PORTAL_ARRIVAL_SHARDS", ARRIVAL_VOXELS)
            .withSampler(OBSIDIAN_SAMPLER).withSampler(CRYING_SAMPLER)
            .withCull(true).withDepthStencilState(WRITE_DEPTH).build();
    private static final RenderPipeline CLOUD_DEPTH = cloudPipeline("server_portal_cloud_depth")
            .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_NONE))
            .withDepthStencilState(WRITE_DEPTH).build();
    private static final RenderPipeline CLOUD_COLOR = cloudPipeline("server_portal_cloud_color")
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)).build();

    private static Flight flight;
    private static PortalVisit activeVisit;
    private static MappableRingBuffer uniforms;
    private static GpuTexture depth;
    private static GpuTextureView depthView;

    private ServerPortalRenderer() {
    }

    private static RenderPipeline.Builder pipeline(String name, String vertex) {
        return pipeline(name, vertex, name);
    }

    private static RenderPipeline.Builder pipeline(String name, String vertex, String fragment) {
        return RenderPipeline.builder()
                .withLocation(id("pipeline/" + name))
                .withVertexShader(id("core/" + vertex))
                .withFragmentShader(id("core/" + fragment))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withUniform(PORTAL_UNIFORM, UniformType.UNIFORM_BUFFER)
                .withColorTargetState(TRANSLUCENT);
    }

    private static RenderPipeline.Builder cloudPipeline(String name) {
        return pipeline(name, "server_portal_voxels", "server_portal_voxels")
                .withShaderDefine("PORTAL_CLOUDS").withCull(true);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CookieMain.MOD_ID, path);
    }

    public static boolean prepare() {
        GpuDevice device = RenderSystem.tryGetDevice();
        return device != null && device.precompilePipeline(SKY, LoadingOverlayRenderer.SHADERS).isValid()
                && device.precompilePipeline(VOXELS, LoadingOverlayRenderer.SHADERS).isValid()
                && device.precompilePipeline(CLOUD_DEPTH, LoadingOverlayRenderer.SHADERS).isValid()
                && device.precompilePipeline(CLOUD_COLOR, LoadingOverlayRenderer.SHADERS).isValid();
    }

    public static void launch(Screen title, Screen list) {
        flight = new Flight(new PortalVisit(list, PortalLayout.create()), title);
    }

    public static void launchFrame(Screen title, float progress) {
        if (flight != null && flight.launchScreen == title) {
            flight.background = smooth(0.62f, 1.0f, progress);
        }
    }

    public static void handoff(Screen list) {
        if (flight != null && flight.parent == list) flight.handoff = true;
    }

    public static void cancelLaunch(Screen title) {
        if (flight != null && flight.launchScreen == title && !flight.handoff) flight = null;
    }

    public static void connect(Screen screen, Screen parent) {
        if (flight != null && flight.connect == screen) return;
        activeVisit = null;
        if (!TitleBrowser.ownsServerList(parent) || !prepare()) {
            flight = null;
            return;
        }
        if (flight == null || !flight.handoff || flight.parent != parent) {
            flight = new Flight(new PortalVisit(parent, PortalLayout.create()), null);
        }
        flight.connect = screen;
        flight.handoff = false;
    }

    public static boolean connecting(Screen screen) {
        return flight != null && flight.connect == screen && flight.failure == null;
    }

    public static boolean isSecurityStatus(Component status) {
        String key = key(status);
        return key.equals("connect.authorizing") || key.equals("connect.encrypting");
    }

    private static String key(Component status) {
        return status.getContents() instanceof TranslatableContents text ? text.getKey() : "";
    }

    public static void connectionFrame(Component status, boolean sawSecurity) {
        flight.background = 1.0f;
        String key = key(status);
        if (key.equals("connect.joining") || key.equals("connect.negotiating")) flight.joining = true;
        if (sawSecurity || flight.joining) flight.ignite();
    }

    public static boolean enterTerrain(Screen screen) {
        if (flight == null || flight.connect == null || flight.failure != null || flight.landingStart != 0L) return false;
        flight.terrain = screen;
        flight.joining = true;
        flight.ignite();
        return true;
    }

    public static boolean terrain(Screen screen) {
        return flight != null && flight.terrain == screen && flight.failure == null;
    }

    public static void terrainFrame() {
        flight.background = 1.0f;
    }

    public static Component status(Component fallback) {
        if (flight == null) return fallback;
        if (flight.entryAt != 0L && flight.now >= flight.entryAt) return CROSSING;
        if (flight.now - flight.buildAt < BUILD_MS) return BUILDING;
        if (flight.igniteAt == 0L) return fallback;
        if (flight.now - flight.igniteAt < IGNITE_MS) return LIGHTING;
        return flight.joining ? OPENING : fallback;
    }

    public static boolean holdTerrainForPortal(Screen screen) {
        if (!terrain(screen)) return false;
        long now = Util.getMillis();
        if (flight.entryAt == 0L) flight.entryAt = Math.max(now, flight.igniteAt + IGNITE_MS);
        return now < flight.entryAt + ENTRY_MS;
    }

    public static void abortTerrain(Minecraft game, Screen screen) {
        if (!terrain(screen)) return;
        Screen parent = flight.parent;
        flight = null;
        if (game.level != null) game.level.disconnect(ConnectScreen.ABORT_CONNECTION);

        game.disconnectWithProgressScreen();
        game.setScreen(parent);
    }

    public static boolean fail(Screen screen, Screen parent) {
        if (failure(screen)) return true;

        if (flight != null && flight.leaving) return false;

        if (flight == null || flight.landingStart != 0L) {
            if ((activeVisit == null && !TitleBrowser.ownsServerList(parent)) || !prepare()) return false;
            PortalVisit visit = activeVisit == null ? new PortalVisit(parent, PortalLayout.create()) : activeVisit;
            flight = Flight.completed(visit);
            flight.worldFailure = true;
        } else if (flight.connect == null) {
            return false;
        }

        if (flight.failure != screen) {
            flight.failure = screen;
            flight.failureTime = Util.getMillis();
            flight.failureSnapPlayed = false;
        }

        activeVisit = null;
        return true;
    }

    public static boolean failure(Screen screen) {
        return flight != null && flight.failure == screen;
    }

    public static float failureReveal() {
        return smooth(0.90f, 1.15f, (flight.now - flight.failureTime) * 0.001f);
    }

    public static float failureCardScale() {
        return flight.retryAt == 0L ? 1.0f : 1.0f - smooth(0.0f, 0.19f, flight.retryElapsed());
    }

    public static void failureFrame() {
        flight.background = 1.0f;
    }

    public static void beginDeparture(Minecraft game) {
        if (activeVisit == null || game.level == null || game.isLocalServer() || !prepare()) return;
        var server = game.getCurrentServer();
        if (server != null && server.isRealm()) return;

        flight = Flight.completed(activeVisit);
        flight.leaving = true;
        flight.returnAt = flight.now;
        activeVisit = null;
    }

    public static boolean finishDeparture(Minecraft game, Screen destination) {
        if (flight == null || !flight.leaving || !(destination instanceof JoinMultiplayerScreen)) return false;
        TitleBrowser.returnFromPortal(game, flight.parent);
        return true;
    }

    public static boolean rewindFailure(Minecraft game, Screen screen) {
        if (!failure(screen)) return false;
        if (flight.retryAt != 0L) return true;

        flight.returnAt = Util.getMillis();
        TitleBrowser.returnFromPortal(game, flight.parent);
        return true;
    }

    public static boolean retryFailure(Screen screen, ServerRetry target) {
        if (!failure(screen)) return false;

        if (flight.retryAt == 0L) {
            flight.retryAt = Util.getMillis();
            flight.retryTarget = target;
        }
        return true;
    }

    public static boolean retrying(Screen screen) {
        return failure(screen) && flight.retryAt != 0L;
    }

    public static void tickFailure(Minecraft game, Screen screen) {
        if (!failure(screen)) return;
        Flight current = flight;
        long now = Util.getMillis();

        if (!current.failureSnapPlayed && now - current.failureTime >= FAILURE_SNAP_MS) {
            current.failureSnapPlayed = true;
            game.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHAIN_BREAK, 1.45f, 0.40f));
        }

        if (current.retryAt == 0L) return;
        long elapsed = now - current.retryAt;
        if (elapsed >= RETRY_IGNITION_MS && !current.retrySoundPlayed) {
            current.retrySoundPlayed = true;
            game.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PORTAL_TRIGGER, 1.45f, 0.40f));
        }

        if (elapsed < RETRY_HANDOFF_MS) return;

        ServerRetry target = current.retryTarget;
        current.buildAt = now - BUILD_MS - 250L;
        current.igniteAt = current.retryAt + RETRY_BUILD_MS;
        current.retryAt = 0L;
        current.retryTarget = null;
        current.retrySoundPlayed = false;
        current.retryLit = true;
        current.joining = false;
        current.worldFailure = false;
        current.entryAt = 0L;
        current.connect = null;
        current.terrain = null;
        current.failure = null;
        current.handoff = true;
        target.connect(game);
    }

    public static void returnTo(TitleScreen title) {
        flight.returnTitle = title;
    }

    public static boolean returning(TitleScreen title) {
        return flight != null && flight.returnTitle == title;
    }

    public static float returnReveal(TitleScreen title) {
        return returning(title) ? smooth(0.50f, RETURN_SECONDS, flight.returnElapsed()) : 1.0f;
    }

    public static void beginFrame(Minecraft game, boolean renderLevel) {
        Flight current = flight;
        if (current == null) {
            closeDepth();
            return;
        }

        Screen screen = game.screen;
        long now = Util.getMillis();
        current.now = now;
        if (current.returnAt != 0L) {
            if (current.returnTitle != null && (screen != current.returnTitle || current.returnElapsed() >= RETURN_SECONDS)) {
                flight = null;
            }
            return;
        }

        if ((current.connect != null || screen != current.launchScreen)
                && (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen)) {
            flight = null;
            return;
        }

        current.background = 0.0f;
        current.landing = -1.0f;
        current.worldCamera = null;
        if (current.failure != null) return;

        if (current.landingStart == 0L && current.terrain != null && screen == null && renderLevel
                && game.level != null && game.player != null) {
            current.landingStart = now;
            current.arrivalLevel = game.level;
            activeVisit = new PortalVisit(current.parent, current.layout);
        }

        if (current.landingStart != 0L) {
            current.landing = (now - current.landingStart) / 600.0f;
            if (game.level != current.arrivalLevel || current.landing >= 1.0f) flight = null;
        }

    }

    public static float landingFov() {
        return flight == null || flight.landing < 0.0f ? 0.0f : 8.0f * (1.0f - smooth(0.0f, 0.65f, flight.landing));
    }

    public static void renderBackground(RenderTarget target) {
        if (flight == null) return;

        if (flight.returnTitle != null) {
            render(target, null, 1.0f - returnReveal(flight.returnTitle));
        } else if (flight.worldCamera != null) {
            render(target, flight.worldCamera, 1.0f);
        } else if ((flight.connect != null || flight.failure != null) && flight.background > 0.0f) {
            render(target, null, flight.background);
        }
    }

    public static void renderLaunchOverlay(RenderTarget target) {
        if (flight != null && flight.connect == null && flight.failure == null && flight.returnTitle == null) {
            float opacity = flight.handoff || flight.leaving ? 1.0f : flight.background;
            if (opacity > 0.0f) render(target, null, opacity);
        }
    }

    public static void worldRendered(Camera camera) {
        if (flight != null && flight.landing >= 0.0f && camera.isInitialized()) flight.worldCamera = camera;
    }

    private static void render(RenderTarget target, Camera camera, float opacity) {
        if (opacity <= 0.0f || target.width < 1 || target.height < 1 || !prepare()) return;
        Flight current = flight;
        GpuDevice device = RenderSystem.getDevice();
        prepareBuffers(device, target);
        var encoder = device.createCommandEncoder();
        var uniform = uniforms.currentBuffer();

        Matrix4f view = new Matrix4f();
        float aspect = (float) target.width / target.height;
        float lens = 2.13f * Math.min(1.0f, aspect / 0.83f);

        if (camera != null) {
            camera.getViewRotationMatrix(view);
            if (current.anchorView == null) {
                current.anchorView = new Matrix4f(view).invert();
                current.anchorPosition = camera.position();
            }

            Vec3 delta = current.anchorPosition.subtract(camera.position());
            view.translate((float) delta.x, (float) delta.y, (float) delta.z).mul(current.anchorView);
            lens = (float) (1.0 / Math.tan(Math.toRadians(camera.getFov()) * 0.5));
        }

        long animationTime = current.failure == null ? current.now : current.failureTime;
        float build = Math.max(0.0f, (animationTime - current.buildAt) * 0.001f);

        float ignition = current.retryAt != 0L ? current.retryElapsed() - RETRY_BUILD_MS * 0.001f
                : current.igniteAt == 0L ? -1.0f : (animationTime - current.igniteAt) * 0.001f;

        float entry = current.worldFailure ? 1.0f : current.entryAt == 0L ? 0.0f
                : Math.clamp((animationTime - current.entryAt) / (float) ENTRY_MS, 0.0f, 1.0f);

        long failurePose = current.retryAt != 0L ? current.retryAt : current.returnAt == 0L ? current.now : current.returnAt;
        float failure = current.failure == null ? -1.0f : Math.max(0.0f, (failurePose - current.failureTime) * 0.001f);
        float returning = current.returnAt == 0L ? -1.0f : current.returnElapsed();

        try (var mapped = encoder.mapBuffer(uniform, false, true)) {
            Std140Builder.intoBuffer(mapped.data())
                    .putVec4(target.width, target.height, build, (current.now - current.created) * 0.001f)
                    .putVec4(opacity, ignition, current.joining ? 1.0f : 0.0f, failure)
                    .putVec4(current.landing, lens, entry, current.retryLit ? 1.0f : 0.0f)
                    .putVec4(current.leaving ? returning : -1.0f, current.leaving ? -1.0f : returning,
                            current.retryAt == 0L ? -1.0f : current.retryElapsed(), 0.0f)
                    .putIVec4(current.layout.seed(), current.layout.cryingMask(), 0, 0)
                    .putMat4f(view);
        }

        var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES);
        GpuBuffer cubeIndices = indices.getBuffer(CUBE_VERTICES);

        var textures = Minecraft.getInstance().getTextureManager();
        var obsidian = textures.getTexture(OBSIDIAN);
        var crying = textures.getTexture(CRYING_OBSIDIAN);

        try (var pass = encoder.createRenderPass(() -> "KWRG server portal", target.getColorTextureView(), OptionalInt.empty(),
                depthView, OptionalDouble.of(1.0))) {
            if (current.landing < 0.55f) {
                pass.setPipeline(SKY);
                pass.setUniform(PORTAL_UNIFORM, uniform);
                pass.draw(0, 3);
            }

            pass.setPipeline(VOXELS);
            pass.setUniform(PORTAL_UNIFORM, uniform);

            pass.bindTexture(OBSIDIAN_SAMPLER, obsidian.getTextureView(), obsidian.getSampler());
            pass.bindTexture(CRYING_SAMPLER, crying.getTextureView(), crying.getSampler());
            pass.setIndexBuffer(cubeIndices, indices.type());

            int sceneVoxels = SCENE_VOXELS + AMBIENT_VOXELS + (current.failure == null ? 0 : DEBRIS_VOXELS + TEAR_VOXELS);
            pass.drawIndexed(0, 0, CUBE_VERTICES, camera != null ? ARRIVAL_VOXELS + AMBIENT_VOXELS : sceneVoxels);
            if (camera == null) {
                pass.setPipeline(CLOUD_DEPTH);
                pass.setUniform(PORTAL_UNIFORM, uniform);
                pass.drawIndexed(0, 0, CUBE_VERTICES, CLOUD_VOXELS);

                pass.setPipeline(CLOUD_COLOR);
                pass.setUniform(PORTAL_UNIFORM, uniform);
                pass.drawIndexed(0, 0, CUBE_VERTICES, CLOUD_VOXELS);
            }
        }
        uniforms.rotate();
    }

    private static void prepareBuffers(GpuDevice device, RenderTarget target) {
        if (uniforms == null) {
            uniforms = new MappableRingBuffer(() -> "KWRG portal frame", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 144);
        }

        if (depth == null || depth.getWidth(0) != target.width || depth.getHeight(0) != target.height) {
            closeDepth();
            depth = device.createTexture("KWRG portal depth", GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32,
                    target.width, target.height, 1, 1);
            depthView = device.createTextureView(depth);
        }
    }

    private static void closeDepth() {
        if (depth == null) return;
        depthView.close();
        depth.close();
        depthView = null;
        depth = null;
    }

    public static void close() {
        flight = null;
        activeVisit = null;
        closeDepth();
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    private static float smooth(float start, float end, float value) {
        float t = Math.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static final class Flight {
        private final Screen parent;
        private final Screen launchScreen;
        private final PortalLayout layout;

        private Screen connect;
        private Screen terrain;
        private Screen failure;

        private boolean handoff;
        private boolean joining;

        private final long created = Util.getMillis();

        private long buildAt;
        private long now = created;

        private long failureTime;
        private boolean failureSnapPlayed;

        private long landingStart;
        private float background;

        private long igniteAt;
        private long entryAt;
        private long returnAt;
        private long retryAt;

        private ServerRetry retryTarget;
        private boolean retrySoundPlayed;
        private boolean retryLit;

        private TitleScreen returnTitle;
        private boolean leaving;
        private boolean worldFailure;
        private float landing = -1.0f;
        private ClientLevel arrivalLevel;
        private Matrix4f anchorView;
        private Vec3 anchorPosition;
        private Camera worldCamera;

        private Flight(PortalVisit visit, Screen launchScreen) {
            parent = visit.list();
            layout = visit.layout();
            this.launchScreen = launchScreen;
            buildAt = created + (launchScreen == null ? 0L : 290L);
        }

        private void ignite() {
            if (igniteAt == 0L) igniteAt = Math.max(now, buildAt + BUILD_MS);
        }

        private float returnElapsed() {
            return Math.max(0.0f, (now - returnAt) * 0.001f);
        }

        private float retryElapsed() {
            return Math.max(0.0f, (now - retryAt) * 0.001f);
        }

        private static Flight completed(PortalVisit visit) {
            Flight scene = new Flight(visit, null);
            scene.buildAt = scene.created - BUILD_MS;
            scene.igniteAt = scene.created - IGNITE_MS - 100L;
            scene.joining = true;
            return scene;
        }
    }

    private record PortalVisit(Screen list, PortalLayout layout) {
    }

    private record PortalLayout(int seed, int cryingMask) {
        private static PortalLayout create() {
            var random = ThreadLocalRandom.current();
            int variants = random.nextInt(2, 5);
            int mask = 0;
            while (Integer.bitCount(mask) < variants) mask |= 1 << random.nextInt(4, 14);
            return new PortalLayout(random.nextInt(), mask);
        }
    }
}
