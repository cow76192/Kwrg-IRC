package me.cookie.kwrg.mixin;

import me.cookie.kwrg.window.render.WorldWindow;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, priority = 2000)
public abstract class WindowKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void kwrg$windowKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (WorldWindow.key(event.key(), action, event.modifiers())) ci.cancel();
    }
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void kwrg$windowCharacter(long window, CharacterEvent event, CallbackInfo ci) {
        if (WorldWindow.character(event.codepoint())) ci.cancel();
    }
    @Inject(method = "preeditCallback", at = @At("HEAD"), cancellable = true)
    private void kwrg$windowPreedit(long window, PreeditEvent event, CallbackInfo ci) {
        if (WorldWindow.preedit(event)) ci.cancel();
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kwrg$windowOnly(CallbackInfo ci) {
        if (WorldWindow.controlling()) ci.cancel();
    }
}
