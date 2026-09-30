package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.NickManager;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class FontMixin {

    @Shadow public abstract int width(FormattedCharSequence text);

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private FormattedCharSequence kwrg$wrapFormattedCharSequence(FormattedCharSequence text) {
        return NickManager.wrap(text);
    }

    @ModifyVariable(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String kwrg$wrapPlainText(String text) {
        return NickManager.replaceNick(text);
    }

    @ModifyVariable(
            method = "drawInBatch8xOutline",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private FormattedCharSequence kwrg$wrapOutlineText(FormattedCharSequence str) {
        return NickManager.wrap(str);
    }

    @ModifyVariable(
            method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private FormattedCharSequence kwrg$wrapWidthCharSeq(FormattedCharSequence text) {
        return NickManager.wrap(text);
    }

    @ModifyVariable(
            method = "width(Ljava/lang/String;)I",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String kwrg$wrapWidthString(String str) {
        return NickManager.replaceNick(str);
    }

    @Inject(
            method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kwrg$wrapWidthFormattedText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
        if (NickManager.isEnabled() && text != null) {
            cir.setReturnValue(this.width(NickManager.wrap(Language.getInstance().getVisualOrder(text))));
        }
    }
}