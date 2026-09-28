package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerScaleMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kwrg$onExtractStateTail(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (entity instanceof Player player) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getUser() != null && player.getGameProfile().name().equals(mc.getUser().getName())) {
                PlayerCustomizationManager.markLocalRenderState(state);
                float scale = PlayerCustomizationManager.getMyScale();
                if (Math.abs(scale - 1.0f) > 0.001f) {
                    state.scale *= scale;
                }
            }
        }
    }
}