package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.IrcGlowManager;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
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
            // 【核心拓展】：优先判断是否为追踪目标，其次判断普通 IRC 玩家
            if (PlayerFinderManager.isTarget(player) || IrcGlowManager.isIrcPlayerGlowing(player)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof Player player) {
            // 【核心拓展】：如果是追踪目标，返回 playerFinderColor；否则返回 IRC glowColor
            if (PlayerFinderManager.isTarget(player)) {
                cir.setReturnValue(PlayerFinderManager.getGlowColorRgb());
            } else if (IrcGlowManager.isIrcPlayerGlowing(player)) {
                cir.setReturnValue(IrcGlowManager.getGlowColorRgb());
            }
        }
    }
}