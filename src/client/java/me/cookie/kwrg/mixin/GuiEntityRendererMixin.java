package me.cookie.kwrg.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cookie.kwrg.render.PlayerDollHudRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {

    @Inject(
            method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("TAIL")
    )
    private void kwrg$hideDollNameTags(GuiEntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CallbackInfo ci) {
        if (PlayerDollHudRenderer.isHudDollRenderState(state.renderState()) && collector instanceof SubmitNodeStorage storage) {
            for (var submits : storage.getSubmitsPerOrder().values()) {
                submits.nameTags.clear();
                submits.seeThroughNameTags.sortInto((submit, translucent) -> {});
                // Mods can also submit custom name tags as ordinary text.
                submits.texts.clear();
            }
        }
    }
}
