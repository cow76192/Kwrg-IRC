package me.cookie.kwrg.mixin.cape;

import me.cookie.kwrg.render.PlayerDollHudRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureOpacityMixin {

    @ModifyConstant(
            method = "blitTexture",
            constant = @Constant(intValue = -1)
    )
    private int kwrg$modifyHudDollBlitOpacity(int originalColor, PictureInPictureRenderState renderState) {
        if (renderState instanceof GuiEntityRenderState entityState
                && PlayerDollHudRenderer.isHudDollRenderState(entityState.renderState())) {
            return PlayerDollHudRenderer.getBlitColor();
        }
        return originalColor;
    }
}