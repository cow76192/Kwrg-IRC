package me.cookie.kwrg.mixin;

import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow abstract void addMessageToQueue(GuiMessage message);

    @Inject(method = "addMessageToQueue", at = @At("HEAD"), cancellable = true)
    private void kwrg$onAddMessageToQueue(GuiMessage message, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        if (!client.isSameThread()) {
            ci.cancel();
            client.execute(() -> this.addMessageToQueue(message));
            return;
        }

        synchronized (this.allMessages) {
            ChatNotifier.onMessageAdded(message);
        }
    }

}