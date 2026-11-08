package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TitleBrowserNavigationMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void kwrg$routeBrowserPanel(Screen next, CallbackInfo ci) {
        if (TitleBrowser.route((Minecraft) (Object) this, next)) ci.cancel();
    }
}
