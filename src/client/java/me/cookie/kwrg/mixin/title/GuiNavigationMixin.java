package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.ServerRetry;
import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiNavigationMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void kwrg$routeBrowserPanel(Screen next, CallbackInfo ci) {
        ServerRetry.clearOnMenu(next);
        if (TitleBrowser.route(Minecraft.getInstance(), next)) ci.cancel();
    }
}
