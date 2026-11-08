package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.cookie.kwrg.render.LoadingFramePacer;
import me.cookie.kwrg.render.LoadingOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Unique private long kwrg$frameTime = -1L;
    @Unique private long kwrg$animationNanos;
    @Unique private long kwrg$inputTime;
    @Unique private float kwrg$mouseX;
    @Unique private float kwrg$mouseY;
    @Unique private float kwrg$lastMouseX;
    @Unique private float kwrg$lastMouseY;
    @Unique private float kwrg$mouseMotion;
    @Unique private boolean kwrg$useShaders;

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Util;getMillis()J"))
    private long kwrg$captureOverlayTime(long now) {
        if (kwrg$frameTime >= 0L) {
            kwrg$animationNanos += LoadingFramePacer.animationStep((LoadingOverlay) (Object) this, now - kwrg$frameTime);
        }
        kwrg$frameTime = now;
        kwrg$useShaders = LoadingOverlayRenderer.prepare();
        if (kwrg$useShaders) {
            LoadingOverlayRenderer.setLoadingFade(kwrg$fadeOut());
        }
        return now;
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private boolean kwrg$showVanillaBackground(GuiGraphicsExtractor graphics, int x1, int y1,
                                               int x2, int y2, int color) {
        return !kwrg$useShaders;
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private boolean kwrg$showVanillaLogo(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture,
                                        int x, int y, float u, float v, int width, int height, int regionWidth,
                                        int regionHeight, int textureWidth, int textureHeight, int color) {
        return !kwrg$useShaders;
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
    private boolean kwrg$showVanillaProgress(LoadingOverlay overlay, GuiGraphicsExtractor graphics,
                                             int x1, int y1, int x2, int y2, float alpha) {
        return !kwrg$useShaders;
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void kwrg$submitVoxelScene(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        if (!kwrg$useShaders) {
            return;
        }

        float fadeOut = kwrg$fadeOut();
        if (fadeOut >= 2.0f) {
            return;
        }

        float fadeIn = fadeInStart < 0L ? 1.0f : Mth.clamp(
                (kwrg$frameTime - fadeInStart) / (float) LoadingOverlay.FADE_IN_TIME, 0.15f, 1.0f);
        // The last 300 ms of vanilla's opaque hold charge the block before the screen is revealed.
        float progress = fadeOut >= 1.0f ? 1.0f
                : Mth.lerp(Mth.clamp(fadeOut / 0.7f, 0.0f, 1.0f), Math.min(currentProgress, 0.99f), 0.99f);
        float charge = Mth.clamp((fadeOut - 0.7f) / 0.3f, 0.0f, 1.0f);
        kwrg$updatePointer(graphics, mouseX, mouseY);
        LoadingOverlayRenderer.submit(kwrg$animationNanos / 1_000_000_000.0f,
                progress, fadeIn, Math.max(0.0f, fadeOut - 1.0f), charge,
                kwrg$mouseX, kwrg$mouseY, kwrg$mouseMotion);
    }

    @Unique
    private float kwrg$fadeOut() {
        return fadeOutStart < 0L ? -1.0f : (kwrg$frameTime - fadeOutStart) / (float) LoadingOverlay.FADE_OUT_TIME;
    }

    @Unique
    private void kwrg$updatePointer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        float x = Mth.clamp((2.0f * mouseX + 1.0f) / graphics.guiWidth() - 1.0f, -1.0f, 1.0f);
        float y = Mth.clamp(1.0f - (2.0f * mouseY + 1.0f) / graphics.guiHeight(), -1.0f, 1.0f);
        float dt = Mth.clamp((kwrg$frameTime - kwrg$inputTime) / 1000.0f, 0.0f, 0.15f);
        float follow = 1.0f - (float) Math.exp(-9.0f * dt);
        kwrg$mouseX = Mth.lerp(follow, kwrg$mouseX, x);
        kwrg$mouseY = Mth.lerp(follow, kwrg$mouseY, y);
        float movement = Math.abs(x - kwrg$lastMouseX) + Math.abs(y - kwrg$lastMouseY);
        kwrg$mouseMotion = Math.max(kwrg$mouseMotion * (float) Math.exp(-4.5f * dt),
                kwrg$inputTime == 0L ? 0.0f : Math.min(1.0f, movement * 9.0f));
        kwrg$lastMouseX = x;
        kwrg$lastMouseY = y;
        kwrg$inputTime = kwrg$frameTime;
    }
}
