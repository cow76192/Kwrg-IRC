package me.cookie.kwrg.mixin.cape;

import me.cookie.kwrg.duck.KwrgAvatarState;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Inject(method = "getPlayerElytraTexture", at = @At("HEAD"), cancellable = true)
    private static void kwrg$overrideElytraTexture(HumanoidRenderState state, CallbackInfoReturnable<Identifier> cir) {
        if (state instanceof KwrgAvatarState avatarState) {
            if (avatarState.kwrg$getCustomCape() != null) {
              cir.setReturnValue(avatarState.kwrg$getCustomElytra());
            }
        }
    }
}