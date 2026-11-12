package me.cookie.kwrg.mixin.freecam;

import me.cookie.kwrg.irc.manager.FreecamManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class FreecamInteractionMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void kwrg$disableCameraAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamManager.isActive()) cir.setReturnValue(false);
    }

    @Inject(method = {"startUseItem", "pickBlockOrEntity", "continueAttack"}, at = @At("HEAD"), cancellable = true)
    private void kwrg$disableCameraInteraction(CallbackInfo ci) {
        if (FreecamManager.isActive()) ci.cancel();
    }
}
