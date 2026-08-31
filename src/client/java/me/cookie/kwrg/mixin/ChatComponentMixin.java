package me.cookie.kwrg.mixin;

import me.cookie.kwrg.util.ChatNotifier;
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

    /**
     * 捕获刚加入队列的消息，自动绑定流光动画配置
     */
    @Inject(method = "addMessageToQueue", at = @At("HEAD"))
    private void kwrg$onAddMessageToQueue(GuiMessage message, CallbackInfo ci) {
        ChatNotifier.onMessageAdded(message);
    }

    /**
     * 在聊天框每帧提取渲染状态时，动态刷新流光颜色与 URL 点击事件
     */
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD")
    )
    private void kwrg$onExtractRenderState(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        if (!ChatNotifier.hasActiveAnimations() || this.allMessages.isEmpty()) {
            return;
        }

        boolean updated = false;

        for (int i = 0; i < this.allMessages.size(); i++) {
            GuiMessage msg = this.allMessages.get(i);
            Component animatedContent = ChatNotifier.getAnimatedFrame(msg);

            if (animatedContent != null) {
                // 构造当前帧具有最新流光颜色、保留 URL 点击事件的 GuiMessage
                GuiMessage newMsg = new GuiMessage(
                        msg.addedTime(),
                        animatedContent,
                        msg.signature(),
                        msg.source(),
                        msg.tag()
                );
                this.allMessages.set(i, newMsg);
                // 同步更新绑定映射
                ChatNotifier.updateMessageBinding(msg, newMsg);
                updated = true;
            }
        }

        if (updated) {
            this.refreshTrimmedMessages();
        }
    }
}