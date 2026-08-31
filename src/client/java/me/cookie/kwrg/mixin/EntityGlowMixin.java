package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.functions.IrcGlowManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player) {
            if (IrcGlowManager.isIrcPlayerGlowing(player)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof Player player) {
            if (IrcGlowManager.isIrcPlayerGlowing(player)) {
                cir.setReturnValue(IrcGlowManager.getGlowColorRgb());
            }
        }
    }
}