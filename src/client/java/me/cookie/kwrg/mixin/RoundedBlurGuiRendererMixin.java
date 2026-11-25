package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import io.wispforest.owo.mixin.ui.CommandEncoderAccessor;
import io.wispforest.owo.mixin.ui.access.GlCommandEncoderAccessor;
import io.wispforest.owo.ui.renderstate.BlurQuadElementRenderState;
import me.cookie.kwrg.render.RoundedBlurRenderer;
import net.minecraft.client.Minecraft;
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
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V")
    )
    private void kwrg$drawRoundedBlur(CallbackInfo ci, @Local(argsOnly = true) RenderPass pass) {
        if (this.kwrg$currentPipeline != RoundedBlurRenderer.ROUNDED_BLUR_PIPELINE) return;
        if (BlurQuadElementRenderState.input == null || BlurQuadElementRenderState.inputView == null) return;

        var mainTarget = Minecraft.getInstance().getMainRenderTarget();
        var mainColor = mainTarget.getColorTexture();
        var inputColor = BlurQuadElementRenderState.input.getColorTexture();
        if (mainColor == null || inputColor == null) return;

        int copyW = Math.min(mainTarget.width, inputColor.getWidth(0));
        int copyH = Math.min(mainTarget.height, inputColor.getHeight(0));
        if (copyW <= 0 || copyH <= 0) return;

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var glEncoder = (GlCommandEncoderAccessor) (
                encoder instanceof GlCommandEncoderAccessor direct
                        ? direct
                        : ((CommandEncoderAccessor) encoder).owo$getBackend()
        );

        glEncoder.owo$setInRenderPass(false);
        encoder.copyTextureToTexture(mainColor, inputColor, 0, 0, 0, 0, 0, copyW, copyH);
        glEncoder.owo$setInRenderPass(true);

        pass.bindTexture(
                "Sampler0",
                BlurQuadElementRenderState.inputView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        );
    }
}