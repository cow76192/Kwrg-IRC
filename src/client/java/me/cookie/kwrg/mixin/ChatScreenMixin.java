package me.cookie.kwrg.mixin;

import me.cookie.kwrg.module.modules.functions.ChatSuffixModule;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String kwrg$appendChatSuffix(String message) {
        return ChatSuffixModule.applySuffixSafely(message);
    }
}
