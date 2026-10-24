package me.cookie.kwrg.mixin;

import me.cookie.kwrg.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;

    @Shadow abstract int getWidth();
    @Shadow abstract double getScale();
    @Shadow abstract boolean isChatFocused();
    @Shadow abstract void scrollChat(int pos);
    @Shadow abstract void addMessageToQueue(GuiMessage message);
    @Shadow abstract void addMessageToDisplayQueue(GuiMessage message);

    @Inject(method = "addMessageToQueue", at = @At("HEAD"), cancellable = true)
    private void kwrg$onAddMessageToQueue(GuiMessage message, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            ci.cancel();
            client.execute(() -> this.addMessageToQueue(message));
        }
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void kwrg$onAddMessageToDisplayQueue(GuiMessage message, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            ci.cancel();
            client.execute(() -> this.addMessageToDisplayQueue(message));
            return;
        }

        ChatNotifier.onMessageAdded(message);

        if (ChatNotifier.hasAnimation(message)) {
            ci.cancel();

            int maxWidth = Mth.floor((double) this.getWidth() / this.getScale());
            List<FormattedCharSequence> lines = message.splitLines(this.minecraft.font, maxWidth);
            boolean chatting = this.isChatFocused();

            for (int i = 0; i < lines.size(); ++i) {
                FormattedCharSequence line = lines.get(i);
                if (chatting && this.chatScrollbarPos > 0) {
                    this.newMessageSinceScroll = true;
                    this.scrollChat(1);
                }

                boolean endOfEntry = (i == lines.size() - 1);
                FormattedCharSequence dynamicLine = ChatNotifier.createDynamicLine(message, i, line);
                this.trimmedMessages.addFirst(new GuiMessage.Line(message, dynamicLine, endOfEntry));
            }

            while (this.trimmedMessages.size() > 100) {
                this.trimmedMessages.removeLast();
            }
        }
    }
}
