package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.render.DynamicIslandRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.tabhud.screenbuilder.WidgetManager", remap = false)
public abstract class SkyblockerWidgetManagerMixin {

    @WrapOperation(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;isDown()Z"
            )
    )
    private static boolean kwrg$keepTabRenderingOnRelease(KeyMapping keyMapping, Operation<Boolean> original) {
        boolean down = original.call(keyMapping);
        if (down) return true;

        if (DynamicIslandRenderer.isTabFeatureOn() && DynamicIslandRenderer.isTabAnimating()) {
            Minecraft mc = Minecraft.getInstance();
            if (keyMapping == mc.options.keyPlayerList) {
                return true;
            }
        }
        return false;
    }
}
