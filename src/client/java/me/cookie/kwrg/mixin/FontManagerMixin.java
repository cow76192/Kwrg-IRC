package me.cookie.kwrg.mixin;

import me.cookie.kwrg.gui.util.FontManagerHelper;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontManager.class)
public class FontManagerMixin {

    @Inject(method = "apply", at = @At("TAIL"))
    private void kwrg$onFontManagerReload(CallbackInfo ci) {
        FontManagerHelper.onResourceReload();
    }
}