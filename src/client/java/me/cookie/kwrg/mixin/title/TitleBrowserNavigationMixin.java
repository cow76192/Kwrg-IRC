package me.cookie.kwrg.mixin.title;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cookie.kwrg.gui.title.TitleBrowser;
import me.cookie.kwrg.gui.title.ServerRetry;
import me.cookie.kwrg.render.ServerPortalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TitleBrowserNavigationMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void kwrg$routeBrowserPanel(Screen next, CallbackInfo ci) {
        ServerRetry.clearOnMenu(next);
        if (TitleBrowser.route((Minecraft) (Object) this, next)) ci.cancel();
    }

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"))
    private void kwrg$startPortalDeparture(Component message, CallbackInfo ci) {
        ServerPortalRenderer.beginDeparture((Minecraft) (Object) this);
    }

    @WrapOperation(method = "disconnectFromWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void kwrg$returnThroughPortal(Minecraft game, Screen next, Operation<Void> original) {
        if (!ServerPortalRenderer.finishDeparture(game, next)) original.call(game, next);
    }
}
