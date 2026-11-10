package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class TitleBrowserListMixin implements TitleBrowser.CompactList {
    @Unique private boolean kwrg$compact;

    @Override
    public boolean kwrg$isCompact() {
        return kwrg$compact;
    }

    @Override
    public void kwrg$compact() {
        kwrg$compact = true;
    }

    @Inject(method = {"extractListBackground", "extractListSeparators"}, at = @At("HEAD"), cancellable = true)
    private void kwrg$omitListBackdrop(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (kwrg$compact) ci.cancel();
    }

    @Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
    private void kwrg$omitSelectionFrame(GuiGraphicsExtractor graphics, @Coerce Object row, int color, CallbackInfo ci) {
        if (kwrg$compact) ci.cancel();
    }

    @Inject(method = "extractItem", at = @At("HEAD"))
    private void kwrg$drawCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick,
                               @Coerce Object row, CallbackInfo ci) {
        if (kwrg$compact) {
            TitleBrowser.drawRow((AbstractSelectionList<?>) (Object) this, (LayoutElement) row, graphics, mouseX, mouseY);
        }
    }
}
