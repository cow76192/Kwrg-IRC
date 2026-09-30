package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cookie.kwrg.irc.manager.PlayerCustomizationManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Unique private static final ThreadLocal<RenderType> kwrg$pendingCustomType = new ThreadLocal<>();

    @Inject(method = "submit", at = @At("HEAD"))
    private void kwrg$onSubmitHead(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        kwrg$pendingCustomType.remove();

        if (!PlayerCustomizationManager.isLocalRenderState(state)) return;
        if (PlayerCustomizationManager.getMyCape() == null) return;
        RenderType customType = PlayerCustomizationManager.resolveMyCapeRenderType();
        if (customType == null) return;

        PlayerSkin skin = state.skin;
        if (skin != null && skin.body() != null) {
            if (skin.cape() == null) {
                state.skin = new PlayerSkin(skin.body(), skin.body(), skin.elytra(), skin.model(), skin.secure());
            }
            state.showCape = true;
            kwrg$pendingCustomType.set(customType);
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void kwrg$onSubmitReturn(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        kwrg$pendingCustomType.remove();
    }

    @WrapOperation(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType kwrg$replaceCapeType(Identifier texture, Operation<RenderType> original) {
        RenderType customType = kwrg$pendingCustomType.get();
        if (customType != null) {
            return customType;
        }
        return original.call(texture);
    }
}