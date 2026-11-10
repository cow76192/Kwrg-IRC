package me.cookie.kwrg.mixin.warp;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class WarpCameraMixin {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float kwrg$settleWarpFov(float fov) {
        float offset = ServerPortalRenderer.landingFov();
        return offset > 0.0f ? Math.min(175.0f, fov + offset) : fov;
    }
}
