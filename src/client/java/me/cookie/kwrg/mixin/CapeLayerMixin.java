package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Unique private static boolean kwrg$inCape = false;

    @Inject(method = "submit", at = @At("HEAD"))
    private void kwrg$onSubmitHead(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        kwrg$inCape = false;
        if (!PlayerCustomizationManager.isLocalRenderState(state)) return;
        if (PlayerCustomizationManager.getMyCape() == null) return;
        if (PlayerCustomizationManager.resolveMyCapeTexture() == null) return;

        PlayerSkin skin = state.skin;
        if (skin != null && skin.body() != null) {
            if (skin.cape() == null) {
                state.skin = new PlayerSkin(skin.body(), skin.body(), skin.elytra(), skin.model(), skin.secure());
            }
            state.showCape = true;
            kwrg$inCape = true;
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void kwrg$onSubmitReturn(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        kwrg$inCape = false;
    }

    @WrapOperation(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/ClientAsset$Texture;texturePath()Lnet/minecraft/resources/Identifier;")
    )
    private Identifier kwrg$replaceCapePath(ClientAsset.Texture self, Operation<Identifier> original) {
        if (kwrg$inCape) {
            Identifier custom = PlayerCustomizationManager.resolveMyCapeTexture();
            if (custom != null) return custom;
        }
        return original.call(self);
    }
}