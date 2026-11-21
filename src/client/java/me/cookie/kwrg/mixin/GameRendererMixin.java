package me.cookie.kwrg.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.cookie.kwrg.render.utils.LoadingOverlayRenderer;
import me.cookie.kwrg.irc.manager.FreecamManager;
import me.cookie.kwrg.render.ServerPortalRenderer;
import me.cookie.kwrg.render.WaypointRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = {"renderItemInHand", "bobView", "bobHurt"}, at = @At("HEAD"), cancellable = true)
    private void kwrg$keepFreeCameraIndependent(CallbackInfo ci) {
        if (FreecamManager.isActive()) ci.cancel();
    }

    @Shadow
    public abstract RenderTarget mainRenderTarget();

    @Inject(method = "preloadUiShader", at = @At("TAIL"))
    private void kwrg$preloadLoadingOverlay(CallbackInfo ci) {
        LoadingOverlayRenderer.preload(mainRenderTarget());
    }

    @Inject(method = "extract", at = @At("HEAD"))
    private void kwrg$beginLoadingOverlayFrame(CallbackInfo ci) {
        LoadingOverlayRenderer.beginFrame();
    }

    @Inject(method = "extract", at = @At("HEAD"))
    private void kwrg$updateWarp(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        ServerPortalRenderer.beginFrame(Minecraft.getInstance(), renderLevel);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void kwrg$drawLoadingOverlay(CallbackInfo ci) {
        LoadingOverlayRenderer.render(mainRenderTarget());
        ServerPortalRenderer.renderLaunchOverlay(mainRenderTarget());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void kwrg$closeLoadingOverlay(CallbackInfo ci) {
        LoadingOverlayRenderer.close();
        ServerPortalRenderer.close();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void kwrg$markWarpArrival(CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        ServerPortalRenderer.worldRendered(renderer.mainCamera());
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
    private Matrix4f kwrg$captureWorldProjection(Matrix4f projection) {
        WaypointRenderer.captureWorldProjection(projection);
        return projection;
    }
}
