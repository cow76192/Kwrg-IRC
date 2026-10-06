package me.cookie.kwrg.mixin;

import me.cookie.kwrg.render.ScoreboardIslandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.gui.customscoreboard.CustomScoreboard", remap = false)
public abstract class SkyHanniScoreboardMixin {

    @Inject(method = "onGuiRenderOverlay", at = @At("HEAD"), require = 0)
    private void kwrg$onScoreboardRenderHead(CallbackInfo ci) {
        ScoreboardIslandRenderer.beginExternalSource();
    }

    @Inject(method = "onGuiRenderOverlay", at = @At("RETURN"), require = 0)
    private void kwrg$onScoreboardRenderReturn(CallbackInfo ci) {
        ScoreboardIslandRenderer.endExternalContent();
        ScoreboardIslandRenderer.endExternalSource();
    }
}
