package me.cookie.kwrg.mixin.freecam;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.cookie.kwrg.irc.manager.FreecamManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class FreecamSodiumMixin {
    @ModifyReturnValue(method = "shouldUseOcclusionCulling", at = @At("RETURN"), require = 0)
    private boolean kwrg$allowCameraInsideTerrain(boolean cull) {
        return cull && !FreecamManager.isActive();
    }
}
