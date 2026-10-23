package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.RouteEditor;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void kwrg$onMouseButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        RouteEditor.onMouseButton(window, button, action);
    }
}
