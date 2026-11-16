package me.cookie.kwrg.mixin.freecam;

import me.cookie.kwrg.irc.manager.FreecamManager;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SectionOcclusionGraph.class)
public abstract class FreecamLevelRendererMixin {
    // Target the vanilla graph itself: Sodium replaces LevelRenderer.cullTerrain entirely.
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean kwrg$allowCameraInsideTerrain(boolean smartCull) {
        return smartCull && !FreecamManager.isActive();
    }
}
