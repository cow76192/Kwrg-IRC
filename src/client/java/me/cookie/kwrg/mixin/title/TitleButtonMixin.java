package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import me.cookie.kwrg.gui.title.TitleMenuUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class TitleButtonMixin {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void kwrg$drawTitleButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick, CallbackInfo ci) {
        AbstractButton button = (AbstractButton) (Object) this;
        if (TitleBrowser.drawButton(button, graphics) || TitleMenuUi.drawButton(button, graphics)) {
            ci.cancel();
        }
    }
}
