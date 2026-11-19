package me.cookie.kwrg.mixin.title;

import me.cookie.kwrg.gui.title.TitleBrowser;
import me.cookie.kwrg.gui.title.TitleMenuUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class TitleButtonMixin {
    @Unique private long kwrg$hoverTime = Util.getMillis();
    @Unique private float kwrg$hoverBlend;

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void kwrg$drawTitleButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick, CallbackInfo ci) {
        AbstractButton button = (AbstractButton) (Object) this;
        long now = Util.getMillis();
        float elapsed = Math.clamp(now - kwrg$hoverTime, 0L, 50L) * 0.001f;
        kwrg$hoverTime = now;
        boolean highlighted = button.isActive() && (button.isHovered() || TitleMenuUi.hasKeyboardFocus(button));
        float ease = 1.0f - (float) Math.exp(-16.0f * elapsed);
        kwrg$hoverBlend = Mth.lerp(ease, kwrg$hoverBlend, highlighted ? 1.0f : 0.0f);
        if (TitleBrowser.drawButton(button, graphics, kwrg$hoverBlend) || TitleMenuUi.drawButton(button, graphics)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void kwrg$drawButtonBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof TitleScreen) return;
        TitleMenuUi.drawButtonBackground((AbstractButton) (Object) this, graphics, kwrg$hoverBlend);
        ci.cancel();
    }
}
