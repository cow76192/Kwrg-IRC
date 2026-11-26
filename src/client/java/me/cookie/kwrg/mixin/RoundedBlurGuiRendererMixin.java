package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.wispforest.owo.mixin.ui.CommandEncoderAccessor;
import io.wispforest.owo.mixin.ui.access.GlCommandEncoderAccessor;
import me.cookie.kwrg.render.RoundedBlurRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class RoundedBlurGuiRendererMixin {

    @Unique
    private RenderPipeline kwrg$currentPipeline;

    @ModifyArg(
            method = "executeDraw",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V")
    )
    private RenderPipeline kwrg$capturePipeline(RenderPipeline pipeline) {
        this.kwrg$currentPipeline = pipeline;
        return pipeline;
    }

    @Inject(
            method = "executeDraw",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"),
            cancellable = true
    )
    private void kwrg$drawRoundedBlur(CallbackInfo ci, @Local(argsOnly = true) RenderPass pass) {
        if (this.kwrg$currentPipeline != RoundedBlurRenderer.ROUNDED_BLUR_PIPELINE) return;

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var glEncoder = (GlCommandEncoderAccessor) (
                encoder instanceof GlCommandEncoderAccessor direct
                        ? direct
                        : ((CommandEncoderAccessor) encoder).owo$getBackend()
        );

        GpuTextureView backdropView;
        glEncoder.owo$setInRenderPass(false);
        try {
            backdropView = RoundedBlurRenderer.captureBackdrop(encoder);
        } finally {
            glEncoder.owo$setInRenderPass(true);
        }

        if (backdropView == null) {
            ci.cancel();
            return;
        }

        pass.bindTexture(
                "Sampler0",
                backdropView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        );
    }
}