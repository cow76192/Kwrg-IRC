package me.cookie.kwrg.mixin;

import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
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
    @Shadow abstract void refreshTrimmedMessages();
    @Shadow abstract void addMessageToQueue(GuiMessage message);

    /**
     * 【防护 1】：若第三方 Mod 在 Netty/异步线程直接塞消息，拦截并派发回渲染主线程
     */
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

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD")
    )
    private void kwrg$onExtractRenderState(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        if (!ChatNotifier.hasActiveAnimations()) {
            return;
        }

        boolean updated = false;

        synchronized (this.allMessages) {
            int size = this.allMessages.size();
            if (size == 0) return;

            int checkLimit = Math.min(size, 100);

            for (int i = 0; i < checkLimit; i++) {
                if (i >= this.allMessages.size()) break;

                GuiMessage msg = this.allMessages.get(i);
                Component animatedContent = ChatNotifier.getAnimatedFrame(msg);

                if (animatedContent != null) {
                    GuiMessage newMsg = new GuiMessage(
                            msg.addedTime(),
                            animatedContent,
                            msg.signature(),
                            msg.source(),
                            msg.tag()
                    );
                    this.allMessages.set(i, newMsg);
                    ChatNotifier.updateMessageBinding(msg, newMsg);
                    updated = true;
                }
            }

            if (updated) {
                this.refreshTrimmedMessages();
            }
        }
    }
}