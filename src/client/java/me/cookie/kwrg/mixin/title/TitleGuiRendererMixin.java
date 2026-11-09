package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.render.LoadingOverlayRenderer;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class TitleGuiRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
    private void kwrg$drawTitleAfterPanorama(CallbackInfo ci) {
        var target = Minecraft.getInstance().getMainRenderTarget();
        LoadingOverlayRenderer.renderTitle(target);
        ServerPortalRenderer.renderBackground(target);
    }
}
