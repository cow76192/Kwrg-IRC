package me.cookie.kwrg.window.render;

import com.mojang.blaze3d.platform.NativeImage;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.module.modules.functions.WindowModule;
import me.cookie.kwrg.render.WaypointRenderer;
import me.cookie.kwrg.render.WorldTextureRenderer;
import me.cookie.kwrg.window.capture.WindowCapture;
import me.cookie.kwrg.window.capture.WindowCompatibility;
import me.cookie.kwrg.window.input.WindowIme;
import me.cookie.kwrg.window.input.WindowInputFocus;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public final class WorldWindow {
    private static final WorldTextureRenderer DEPTH = new WorldTextureRenderer(true);
    private static final WorldTextureRenderer THROUGH = new WorldTextureRenderer(false);
    private static final Identifier POINTER = Identifier.fromNamespaceAndPath("kwrg", "dynamic/window_pointer");
    private static WindowCapture capture;
    private static ClientLevel level;
    private static Vec3 anchor;
    private static Quaternionf orientation;
    private static boolean controlling, pointerCreated, initialized, entryRelease;
    private static double u = 0.5, v = 0.5, lastX, lastY;
    private static long lastHoverRefresh;
    private static double imeAnchorU, imeAnchorV, imeLineHeight = 20;
    private static final Matrix4f screenProjection = new Matrix4f();
    private static Vec3 screenCamera = Vec3.ZERO;
    private static int imeButtons, sourceButtons;
    private static final WindowInputFocus.Listener INPUT = new WindowInputFocus.Listener() {
        public void move(double x, double y) {
            WorldWindow.move(x, y);
        }

        public boolean button(int button, int action) {
            return WorldWindow.controlButton(button, action);
        }

        public boolean scroll(double x, double y) {
            return WorldWindow.scroll(x, y);
        }

        public boolean nativePointer() {
            return capture != null && capture.nativePointer();
        }
    };

    private WorldWindow() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        LevelRenderEvents.END_MAIN.register(WorldWindow::render);
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            WindowCompatibility.update(mc.getWindow().handle(), capture != null && !capture.ended() && mc.isWindowActive());
            if (capture == null) return;
            if (mc.level != level || mc.player == null) {
                destroy();
                return;
            }

            if (controlling && (!mc.isWindowActive() || mc.screen != null || mc.getOverlay() != null || capture.ended()))
                leave();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> destroy());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> destroy());
    }

    public static void create(WindowCapture.Window window) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) throw new IllegalStateException("请先进入世界");

        if (window == null) throw new IllegalStateException("请先选择窗口");

        destroy();
        capture = new WindowCapture(window);
        WindowCompatibility.update(mc.getWindow().handle(), true);

        level = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        orientation = new Quaternionf(camera.rotation());
        anchor = camera.position().add(new Vec3(camera.forwardVector()).scale(4));
    }

    public static boolean exists() {
        return capture != null;
    }

    public static boolean controlling() {
        return controlling;
    }

    public static String status() {
        if (capture == null) return "未创建";

        if (GLFW.glfwGetWindowMonitor(Minecraft.getInstance().getWindow().handle()) != 0)
            return "独占全屏：请切换窗口化/无边框以兼容后台画面";
        return capture.status();
    }

    private static WindowSurface surface() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var settings = WindowModule.CONFIG.get().window;

        Quaternionf rotation = settings.followCamera ? new Quaternionf(camera.rotation()) : orientation;
        Vec3 center = settings.followCamera ? camera.position().add(new Vec3(camera.forwardVector()).scale(4)) : anchor;
        Vector3f offset = rotation.transform(new Vector3f(settings.offsetX, settings.offsetY, 0));

        return new WindowSurface(center.add(offset.x, offset.y, offset.z), rotation, settings.width, settings.width * capture.height() / capture.width(), settings.curved);
    }

    private static void render(LevelRenderContext context) {
        if (capture == null || Minecraft.getInstance().level != level || context.poseStack() == null || context.bufferSource() == null)
            return;

        Identifier image = capture.upload();
        if (image == null) return;

        long now = System.nanoTime();
        if (controlling && !WindowInputFocus.keepGameForeground(Minecraft.getInstance().getWindow().handle(), INPUT))
            leave();

        WindowSurface surface = surface();
        if (controlling) {
            screenCamera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            screenProjection.set(WaypointRenderer.worldProjection()).mul(context.levelState().cameraRenderState.viewRotationMatrix).mul(context.poseStack().last().pose());

            var insertion = capture.caret();
            if (insertion != null) {
                imeAnchorU = (insertion.x() - capture.originX()) / capture.clientWidth();
                imeAnchorV = (insertion.y() - capture.originY()) / capture.clientHeight();
                imeLineHeight = insertion.height();
            }

            double advance = 0;
            var preedit = WindowIme.composition();
            if (preedit != null) {
                int caretIndex = WindowIme.caretIndex(preedit);
                advance = Minecraft.getInstance().font.width(preedit.fullText().substring(0, caretIndex)) * imeLineHeight / Minecraft.getInstance().font.lineHeight / capture.clientWidth();
            }

            Vector2d caret = WindowSurface.screenPoint(surface.point(imeAnchorU + advance, imeAnchorV), screenCamera, screenProjection);
            Vector2d bottom = WindowSurface.screenPoint(surface.point(imeAnchorU + advance, imeAnchorV + imeLineHeight / capture.clientHeight()), screenCamera, screenProjection);
            if (caret != null && bottom != null) {
                WindowIme.update(caret.x, caret.y, (int) Math.max(1, Math.abs(bottom.y - caret.y) * Minecraft.getInstance().getWindow().getHeight()));
            }
        }

        if (controlling && now - lastHoverRefresh >= 16_000_000L) {
            routePointer();
            lastHoverRefresh = now;
        }

        var renderer = WindowModule.CONFIG.get().window.throughWalls ? THROUGH : DEPTH;
        for (int i = 0; i < surface.segments(); i++) {
            float a = (float) i / surface.segments(), b = (float) (i + 1) / surface.segments();
            renderer.renderQuad(context.poseStack(), context.bufferSource(), image, surface.point(a, 1), surface.point(b, 1), surface.point(b, 0), surface.point(a, 0), a, 0, b, 1, 0xFFFFFFFF);
        }

        if (controlling && WindowIme.composing()) renderPreedit(context, surface);

        if (controlling) {
            ensurePointer();
            double du = 18.0 / capture.width(), dv = 26.0 / capture.height();
            Vec3 lift = new Vec3(surface.rotation().transform(new Vector3f(0, 0, 0.02f)));
            renderer.renderQuad(context.poseStack(), context.bufferSource(), POINTER,
                    surface.point(u, v + dv).add(lift), surface.point(u + du, v + dv).add(lift),
                    surface.point(u + du, v).add(lift), surface.point(u, v).add(lift), 0, 0, 1, 1, 0xFFFFFFFF);
        }

        context.bufferSource().endBatch(renderer.getRenderType(image));
        if (controlling) context.bufferSource().endBatch(renderer.getRenderType(POINTER));
    }

    private static void ensurePointer() {
        if (pointerCreated) return;
        NativeImage image = new NativeImage(18, 26, true);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 15; x++) {
                boolean arrow = x <= y * 0.65 && (y < 15 || x >= 5 && x <= 8);
                if (arrow) image.setPixel(x, y, x == 0 || x >= (int) (y * 0.65) || y == 21 ? 0xFF101010 : 0xFFFFFFFF);
            }

        Minecraft.getInstance().getTextureManager().register(POINTER, new DynamicTexture(() -> "kwrg-window-pointer", image));
        pointerCreated = true;
    }

    private static void renderPreedit(LevelRenderContext context, WindowSurface surface) {
        var event = WindowIme.composition();
        if (event == null || event.fullText().isEmpty()) return;

        var mc = Minecraft.getInstance();
        var poses = context.poseStack();
        var position = surface.point(imeAnchorU, imeAnchorV).subtract(screenCamera);

        float scale = (float) (surface.width() / capture.clientWidth() * imeLineHeight / mc.font.lineHeight);
        int background = capture.background(imeAnchorU + 2 / capture.clientWidth(), imeAnchorV + 2 / capture.clientHeight());
        int brightness = ((background >> 16 & 255) * 299 + (background >> 8 & 255) * 587 + (background & 255) * 114) / 1000;
        int foreground = brightness > 128 ? 0xff101010 : 0xfff0f0f0;

        poses.pushPose();
        poses.translate(position.x, position.y, position.z);
        poses.mulPose(surface.rotation());

        if (surface.curved()) poses.mulPose(new Quaternionf().rotationY((float) (-(imeAnchorU - .5) * 1.2)));

        poses.translate(0, 0, .015);
        poses.scale(scale, -scale, scale);

        var mode = WindowModule.CONFIG.get().window.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET;
        mc.font.drawInBatch(event.toFormattedText(Style.EMPTY.withUnderlined(true)), 0, 0, foreground, false, poses.last().pose(), context.bufferSource(), mode, background, 0xf000f0);
        int cursor = WindowIme.caretIndex(event);
        if ((System.currentTimeMillis() / 500 & 1) == 0)
            mc.font.drawInBatch("|", mc.font.width(event.fullText().substring(0, cursor)), 0, foreground, false, poses.last().pose(), context.bufferSource(), mode, 0, 0xf000f0);
        poses.popPose();
    }

    /**
     * Called before vanilla dispatch so Esc, F3 and gameplay bindings cannot leak.
     */
    public static boolean key(int key, int action, int modifiers) {
        if (!controlling) return false;
        if (key == GLFW.GLFW_KEY_G && action == GLFW.GLFW_PRESS && (modifiers & GLFW.GLFW_MOD_ALT) != 0) leave();
        else if (!WindowIme.consumes(key, action, modifiers)) capture.key(key, action);
        return true;
    }

    public static boolean character(int codepoint) {
        if (!controlling) return false;
        if (WindowIme.textInput()) capture.text(codepoint);
        else capture.character(codepoint);
        return true;
    }

    public static boolean preedit(PreeditEvent event) {
        if (!controlling) return false;
        if (!WindowIme.composing() && event != null && !event.fullText().isEmpty()) capture.refreshCaret();
        WindowIme.preedit(event);
        return true;
    }

    public static boolean button(int button, int action) {
        if (controlling) {
            if (entryRelease && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_RELEASE) {
                entryRelease = false;
                WindowInputFocus.entryReleased();
            }
            return true;
        }

        if (entryRelease && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_RELEASE) {
            entryRelease = false;
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (capture == null || !capture.ready() || button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || action != GLFW.GLFW_PRESS
                || mc.screen != null || mc.getOverlay() != null || !mc.isWindowActive() || !mc.mouseHandler.isMouseGrabbed())
            return false;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 start = camera.position(), direction = new Vec3(camera.forwardVector());
        WindowSurface.Hit hit = surface().hit(start, direction, 64);
        if (hit == null) return false;

        if (!WindowModule.CONFIG.get().window.throughWalls) {
            var block = level.clip(new ClipContext(start, start.add(direction.scale(hit.distance())),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceTo(start) + 0.01 < hit.distance())
                return false;
        }

        if (!WindowInputFocus.keepGameForeground(mc.getWindow().handle(), INPUT)) return false;

        try {
            WindowIme.start(mc.getWindow().handle());
            mc.textInputManager().startTextInput();
        } catch (RuntimeException | LinkageError unavailable) {
            try {
                WindowIme.stop();
            } catch (RuntimeException | LinkageError cleanup) {
                unavailable.addSuppressed(cleanup);
            } finally {
                WindowInputFocus.release();
            }

            CookieMain.LOGGER.warn("Window IME initialization failed", unavailable);
            return false;
        }

        u = hit.u();
        v = hit.v();

        controlling = true;
        entryRelease = WindowInputFocus.entryPending();
        imeAnchorU = u;
        imeAnchorV = v;

        KeyMapping.releaseAll();
        lastX = WindowInputFocus.mouseX();
        lastY = WindowInputFocus.mouseY();
        capture.releaseInput();
        capture.move(u, v);
        return true;
    }

    private static boolean controlButton(int button, int action) {
        if (!controlling) return false;

        if (action == GLFW.GLFW_RELEASE && (sourceButtons & (1 << button)) != 0) {
            sourceButtons &= ~(1 << button);
            capture.button(button, false, u, v);
            return true;
        }

        if (action == GLFW.GLFW_RELEASE && (imeButtons & (1 << button)) != 0) {
            imeButtons &= ~(1 << button);
            return false;
        }

        if (overIme()) {
            if (!WindowIme.pointerOnCandidate()) return true;
            if (action == GLFW.GLFW_PRESS) imeButtons |= 1 << button;
            return false;
        }

        if (entryRelease && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_RELEASE) {
            entryRelease = false;
            return true;
        }

        capture.button(button, action != GLFW.GLFW_RELEASE, u, v);
        if (button == 0 && action == GLFW.GLFW_PRESS) {
            imeAnchorU = u;
            imeAnchorV = v;
            capture.requestCaret();
        }

        if (action == GLFW.GLFW_PRESS) sourceButtons |= 1 << button;
        return true;
    }

    private static WindowInputFocus.Point imePoint() {
        Vector2d screen = WindowSurface.screenPoint(surface().point(u, v), screenCamera, screenProjection);
        return screen == null ? null : WindowIme.point(screen.x, screen.y);
    }

    private static boolean overIme() {
        return imePoint() != null;
    }

    private static void routePointer() {
        var point = sourceButtons == 0 ? imePoint() : null;
        if (point != null) capture.moveIme(point.x, point.y);
        else capture.move(u, v);
    }

    private static boolean move(double x, double y) {
        if (!controlling) return false;
        u = Math.clamp(u + (x - lastX) / capture.width(), 0, 1);
        v = Math.clamp(v + (y - lastY) / capture.height(), 0, 1);
        lastX = x;
        lastY = y;
        routePointer();
        return true;
    }

    public static boolean scroll(double x, double y) {
        if (!controlling) return false;
        if (overIme()) return !WindowIme.pointerOnCandidate();
        capture.scroll(x, y, u, v);
        return true;
    }

    public static void leave() {
        if (!controlling) return;
        controlling = false;
        imeButtons = sourceButtons = 0;
        if (capture != null) capture.releaseInput();
        WindowInputFocus.release();

        try {
            WindowIme.stop();
        } catch (RuntimeException | LinkageError failure) {
            me.cookie.kwrg.CookieMain.LOGGER.warn("Window IME cleanup failed", failure);
        }

        Minecraft.getInstance().textInputManager().stopTextInput();
        KeyMapping.releaseAll();
        Minecraft.getInstance().mouseHandler.setIgnoreFirstMove();
    }

    public static void destroy() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        // Disconnect callbacks can run on Netty; GLFW windows and textures belong to the render thread.
        if (!mc.isSameThread()) {
            mc.execute(WorldWindow::destroy);
            return;
        }

        WindowCompatibility.release(mc.getWindow().handle());
        leave();
        entryRelease = false;
        if (capture != null) capture.close();

        capture = null;
        level = null;
        anchor = null;
        orientation = null;
        if (pointerCreated) Minecraft.getInstance().getTextureManager().release(POINTER);
        pointerCreated = false;
    }
}
