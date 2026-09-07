package me.cookie.kwrg.mixin;

import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiMessage.Line.class)
public abstract class GuiMessageLineMixin {

    @Shadow @Final private GuiMessage parent;
    @Shadow @Final private FormattedCharSequence content;

    @Inject(method = "content", at = @At("HEAD"), cancellable = true)
    private void kwrg$onGetAnimatedLineContent(CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (ChatNotifier.hasActiveAnimations()) {
            GuiMessage.Line self = (GuiMessage.Line) (Object) this;
            FormattedCharSequence dynamicLine = ChatNotifier.getDynamicLineContent(self, this.parent, this.content);
            if (dynamicLine != null) {
                cir.setReturnValue(dynamicLine);
            }
        }
    }
}