package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({WorldSelectionList.class, ServerSelectionList.class})
public abstract class TitleBrowserRowWidthMixin {
    @Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
    private void kwrg$fitRowToPanel(CallbackInfoReturnable<Integer> cir) {
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        if (((TitleBrowser.CompactList) list).kwrg$isCompact()) {
            cir.setReturnValue(Math.max(60, list.getWidth() - 26));
        }
    }
}
