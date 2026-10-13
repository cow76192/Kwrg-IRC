package me.cookie.kwrg.mixin;

import me.cookie.kwrg.duck.KwrgAvatarState;
import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
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

            if (player == mc.player && PlayerCustomizationManager.inInventoryRendering) {
                if (state instanceof KwrgAvatarState avatarState) {
                    RenderType capeType = PlayerCustomizationManager.getPlayerCapeRenderType(player);
                    avatarState.kwrg$setCustomCape(capeType);
                    // 【修正】：方法名对齐为 kwrg$setCustomElytra
                    avatarState.kwrg$setCustomElytra(PlayerCustomizationManager.getPlayerElytraTexture(player));
                }
                PlayerCustomizationManager.markLocalRenderState(state);
                return;
            }

            // 世界中的缩放
            float scale = PlayerCustomizationManager.getPlayerScale(player);
            if (Math.abs(scale - 1.0f) > 0.001f) {
                state.scale *= scale;
                state.shadowRadius *= scale;
                if (state.nameTagAttachment != null) {
                    state.nameTagAttachment = state.nameTagAttachment.scale(scale);
                }
            }

            // 披风与鞘翅注入
            if (state instanceof KwrgAvatarState avatarState) {
                RenderType capeType = PlayerCustomizationManager.getPlayerCapeRenderType(player);
                avatarState.kwrg$setCustomCape(capeType);
                // 【修正】：方法名对齐为 kwrg$setCustomElytra
                avatarState.kwrg$setCustomElytra(PlayerCustomizationManager.getPlayerElytraTexture(player));
            }

            if (player == mc.player) {
                PlayerCustomizationManager.markLocalRenderState(state);
            }
        }
    }
}