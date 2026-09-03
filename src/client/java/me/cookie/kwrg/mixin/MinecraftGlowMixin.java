package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void onShouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player) {
            if (PlayerFinderManager.isTarget(player) || IrcGlowManager.isIrcPlayerGlowing(player)) {
                cir.setReturnValue(true);
            }
        }
    }
}