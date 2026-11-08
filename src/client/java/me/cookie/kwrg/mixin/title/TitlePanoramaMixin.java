package me.cookie.kwrg.mixin.title;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.cookie.kwrg.render.LoadingOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Panorama.class)
public abstract class TitlePanoramaMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V"))
    private void kwrg$fadePanoramaOverlay(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture,
                                          int x, int y, float u, float v, int width, int height, int regionWidth,
                                          int regionHeight, int textureWidth, int textureHeight, Operation<Void> original) {
        float alpha = LoadingOverlayRenderer.panoramaOverlayAlpha();
        if (alpha == 1.0f) {
            original.call(graphics, pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        } else if (alpha > 0.0f) {
            graphics.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, ARGB.white(alpha));
        }
    }
}
