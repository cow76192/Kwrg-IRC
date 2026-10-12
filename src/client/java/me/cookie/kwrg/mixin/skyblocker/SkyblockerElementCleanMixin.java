package me.cookie.kwrg.mixin.skyblocker;

import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.tabhud.widget.ElementBasedWidget", remap = false)
public abstract class SkyblockerElementCleanMixin {

    @Shadow
    protected float backgroundOpacity;

    @Unique
    private float kwrg$savedOpacity = 0.75f;

    @Inject(
            method = "extractWidgetRenderState",
            at = @At("HEAD"), require = 0)
    private void kwrg$onExtractWidgetRenderStateHead(GuiGraphicsExtractor context, float delta, CallbackInfo ci) {
        this.kwrg$savedOpacity = this.backgroundOpacity;
        if (DynamicIslandRenderer.isTabFeatureOn() && DynamicIslandRenderer.getTabProgress() > 0.005f) {
            this.backgroundOpacity = 0.0f;
        }
    }

    @Inject(
            method = "extractWidgetRenderState",
            at = @At("TAIL"), require = 0)
    private void kwrg$onExtractWidgetRenderStateTail(GuiGraphicsExtractor context, float delta, CallbackInfo ci) {
        this.backgroundOpacity = this.kwrg$savedOpacity;
    }
}
