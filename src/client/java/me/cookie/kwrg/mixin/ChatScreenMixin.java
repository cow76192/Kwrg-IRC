package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.ChatTypingManager;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "onEdited", at = @At("HEAD"))
    private void kwrg$onChatEdited(String newText, CallbackInfo ci) {
        ChatTypingManager.onTextChanged(newText);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void kwrg$onChatRemoved(CallbackInfo ci) {
        ChatTypingManager.onChatClosed();
    }
}