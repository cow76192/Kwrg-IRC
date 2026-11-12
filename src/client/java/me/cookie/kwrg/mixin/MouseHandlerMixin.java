package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.irc.manager.FreecamManager;
import me.cookie.kwrg.irc.manager.RouteEditor;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void kwrg$turnCameraOnly(LocalPlayer player, double horizontal, double vertical, Operation<Void> original) {
        if (FreecamManager.isActive()) FreecamManager.turn(horizontal, vertical);
        else original.call(player, horizontal, vertical);
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void kwrg$onMouseButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        RouteEditor.onMouseButton(window, button, action);
    }
}
