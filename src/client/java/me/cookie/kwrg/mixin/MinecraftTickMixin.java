package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.textures.GpuTexture;
import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.irc.manager.DanceRoomManager;
import me.cookie.kwrg.render.utils.LoadingAtlasUpload;
import me.cookie.kwrg.render.utils.LoadingAudio;
import me.cookie.kwrg.render.utils.LoadingFontSets;
import me.cookie.kwrg.render.utils.LoadingFramePacer;
import me.cookie.kwrg.render.utils.LoadingShaderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin extends ReentrantBlockableEventLoop<Runnable> {
    @Unique private final LoadingFramePacer kwrg$loadingPacer = new LoadingFramePacer();
    @Unique private long kwrg$tickStarted;
    @Unique private boolean kwrg$deferLoadingTasks;
    @Unique private boolean kwrg$presentLoadingFrame;
    @Unique private boolean kwrg$usedLoadingLimiter;

    protected MinecraftTickMixin(String name, boolean propagatesCrashes) {
        super(name, propagatesCrashes);
    }

    @Inject(method = "onGameLoadFinished", at = @At("TAIL"))
    private void kwrg$reportStartupAtlas(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        TextureAtlas atlas;
        GpuTexture texture;
        try {
            atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            texture = atlas.getTexture();
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return;
        }
        if (!texture.isClosed()) {
            // PCL recognizes the atlas marker even when a console filter hides vanilla's allocation logs.
            CookieMain.LOGGER.info("Startup resources ready. Created: {}x{}x{} {}-atlas",
                    texture.getWidth(0), texture.getHeight(0), texture.getMipLevels() - 1, atlas.location());
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void kwrg$startClientFrame(CallbackInfo ci) {
        kwrg$tickStarted = System.nanoTime();
        kwrg$deferLoadingTasks = false;
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void kwrg$paceLoadingTasks(Minecraft client, Operation<Void> original) {
        if (LoadingAtlasUpload.isLoadingWithoutWorld(client)) {
            kwrg$deferLoadingTasks = true;
            return;
        }
        kwrg$loadingPacer.reset();
        LoadingAtlasUpload.step(client);
        LoadingFontSets.step(client);
        LoadingShaderPipelines.step(client);
        original.call(client);
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
    private void kwrg$renderLoadingFrame(Minecraft client, boolean tick, Operation<Void> original) {
        kwrg$presentLoadingFrame = kwrg$deferLoadingTasks;
        kwrg$usedLoadingLimiter = false;
        try {
            original.call(client, tick);
        } finally {
            kwrg$presentLoadingFrame = false;
            kwrg$usedLoadingLimiter = false;
        }
    }

    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void kwrg$presentBeforeReloadWork(TracyFrameCapture capture, Operation<Void> original) {
        if (!kwrg$presentLoadingFrame) {
            original.call(capture);
            return;
        }
        kwrg$presentLoadingFrame = false;
        Minecraft client = (Minecraft) (Object) this;
        var window = client.getWindow();
        int vanillaLimit = client.gameRenderer.getGameRenderState().framerateLimit;
        boolean loading = LoadingAtlasUpload.isLoadingWithoutWorld(client);
        boolean paced = loading && !window.isMinimized();
        if (loading) {
            long renderNanos = System.nanoTime() - kwrg$tickStarted;
            try (var ignored = Profiler.get().zone("kwrgLoadingPacing")) {
                kwrg$loadingPacer.beforePresent(client.getOverlay(), window, client.options.enableVsync().get(),
                        vanillaLimit, renderNanos);
            }
        }
        original.call(capture);

        if (LoadingAtlasUpload.isLoadingWithoutWorld(client)) {
            kwrg$loadingPacer.presented(System.nanoTime(), vanillaLimit);
            try (var ignored = Profiler.get().zone("kwrgLoadingWork")) {
                kwrg$loadingPacer.runWork(() -> LoadingAtlasUpload.step(client),
                        () -> LoadingFontSets.step(client), () -> LoadingShaderPipelines.step(client), this::pollTask);
            }
            kwrg$usedLoadingLimiter = paced;
        } else {
            kwrg$loadingPacer.reset();
            LoadingAtlasUpload.step(client);
            LoadingFontSets.step(client);
            LoadingShaderPipelines.step(client);
            runAllTasks();
        }
    }

    @ModifyExpressionValue(method = "renderFrame", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/renderer/state/GameRenderState;framerateLimit:I"))
    private int kwrg$skipSecondFrameLimit(int vanillaLimit) {
        return kwrg$usedLoadingLimiter ? Options.UNLIMITED_FRAMERATE_CUTOFF : vanillaLimit;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void kwrg$closeStagedReload(CallbackInfo ci) {
        kwrg$loadingPacer.reset();
        LoadingAtlasUpload.close();
        LoadingAudio.close();
        LoadingShaderPipelines.close();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        DanceRoomManager.onClientTick();
    }
}
