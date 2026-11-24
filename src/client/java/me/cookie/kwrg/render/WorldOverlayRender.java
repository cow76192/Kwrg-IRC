package me.cookie.kwrg.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public enum WorldOverlayRender {
    BEFORE_GIZMOS,
    END_MAIN;

    static {
        LevelRenderEvents.BEFORE_GIZMOS.register(BEFORE_GIZMOS::renderInMainPass);
        LevelRenderEvents.END_MAIN.register(END_MAIN::renderInMainPass);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldOverlayRender.class);

    private static MethodHandle shaderPackInUse = findIrisApi();

    private final List<Consumer<Context>> renderers = new ArrayList<>();

    public record Context(CameraRenderState camera, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
    }

    public void register(Consumer<Context> renderer) {
        renderers.add(renderer);
    }

    private void renderInMainPass(LevelRenderContext context) {
        if (!renderers.isEmpty() && !isShaderPackInUse()) {
            render(new Context(context.levelState().cameraRenderState, context.poseStack(), context.bufferSource()));
        }
    }

    public static void renderAfterComposite(Context context) {
        BEFORE_GIZMOS.render(context);
        END_MAIN.render(context);
        context.bufferSource().endBatch();
    }

    private void render(Context context) {
        for (Consumer<Context> renderer : renderers) {
            renderer.accept(context);
        }
    }

    public static boolean isShaderPackInUse() {
        if (shaderPackInUse == null) return false;
        try {
            return (boolean) shaderPackInUse.invokeExact();
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Failed to query Iris shader pack state", e);
            shaderPackInUse = null;
            return false;
        } catch (Error e) {
            throw e;
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    private static MethodHandle findIrisApi() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Could not initialize Iris rendering compatibility", e);
            return null;
        }
    }
}
