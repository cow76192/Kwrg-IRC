package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.irc.manager.FreecamManager;
import me.cookie.kwrg.irc.manager.RouteEditor;
import me.cookie.kwrg.window.render.WorldWindow;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 2000)
public abstract class MouseHandlerMixin {
    @Shadow private boolean isLeftPressed;
    @Shadow private boolean isMiddlePressed;
    @Shadow private boolean isRightPressed;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void kwrg$turnCameraOnly(LocalPlayer player, double horizontal, double vertical, Operation<Void> original) {
        if (FreecamManager.isActive()) FreecamManager.turn(horizontal, vertical);
        else original.call(player, horizontal, vertical);
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void kwrg$onMouseButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (WorldWindow.button(button.button(), action)) {
            isLeftPressed = isMiddlePressed = isRightPressed = false;
            accumulatedDX = accumulatedDY = 0;
            ci.cancel(); return;
        }
        RouteEditor.onMouseButton(window, button, action);
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void kwrg$windowMove(long window, double x, double y, CallbackInfo ci) {
        if (WorldWindow.controlling()) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void kwrg$windowScroll(long window, double x, double y, CallbackInfo ci) {
        if (WorldWindow.controlling()) { WorldWindow.scroll(x, y); ci.cancel(); }
    }

    @Inject(method = {"handleAccumulatedMovement", "onDrop"}, at = @At("HEAD"), cancellable = true)
    private void kwrg$windowOnly(CallbackInfo ci) {
        if (WorldWindow.controlling()) ci.cancel();
    }
}
