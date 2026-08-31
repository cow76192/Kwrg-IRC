package me.cookie.kwrg.mixin;

import me.cookie.kwrg.CookieMain;
import me.cookie.kwrg.gui.IrcScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftKeybindMixin {
    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void onHandleKeybinds(CallbackInfo ci) {
        while (CookieMain.openIrcKey.consumeClick()) {
            IrcScreen.showGui();
        }
    }
}