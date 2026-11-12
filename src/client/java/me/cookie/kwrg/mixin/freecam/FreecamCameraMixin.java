package me.cookie.kwrg.mixin.freecam;

import me.cookie.kwrg.irc.manager.FreecamManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class FreecamCameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
    private void kwrg$alignFreeCamera(float partialTick, CallbackInfo ci) {
        FreecamManager.validateSession(Minecraft.getInstance());
        if (!FreecamManager.isActive()) return;
        FreecamManager.updateCamera();
        setPosition(FreecamManager.position());
        setRotation(FreecamManager.yaw(), FreecamManager.pitch());
        detached = true;
        ci.cancel();
    }
}
