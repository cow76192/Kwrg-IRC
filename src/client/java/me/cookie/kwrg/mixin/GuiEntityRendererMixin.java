package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.cookie.kwrg.render.PlayerDollHudRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {

    @WrapOperation(
            method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V")
    )
    private void kwrg$hideDollNameTags(FeatureRenderDispatcher dispatcher, Operation<Void> original,
                                     @Local(argsOnly = true) GuiEntityRenderState state) {
        if (PlayerDollHudRenderer.isHudDollRenderState(state.renderState())) {
            for (var submits : dispatcher.getSubmitNodeStorage().getSubmitsPerOrder().values()) {
                submits.getNameTagSubmits().clear();
                // Mods can also submit custom name tags as ordinary text.
                submits.getTextSubmits().clear();
            }
        }
        original.call(dispatcher);
    }
}
