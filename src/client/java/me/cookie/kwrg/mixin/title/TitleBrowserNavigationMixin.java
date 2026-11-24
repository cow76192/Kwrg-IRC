package me.cookie.kwrg.mixin.title;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TitleBrowserNavigationMixin {
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"))
    private void kwrg$startPortalDeparture(Component message, CallbackInfo ci) {
        ServerPortalRenderer.beginDeparture((Minecraft) (Object) this);
    }

    @WrapOperation(method = "disconnectFromWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void kwrg$returnThroughPortal(Gui gui, Screen next, Operation<Void> original) {
        if (!ServerPortalRenderer.finishDeparture((Minecraft) (Object) this, next)) original.call(gui, next);
    }
}
