package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcMessageHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        if (IrcClient.isIrcChatActive) {
            IrcClient.sendMessage(message);
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        String lower = command.trim().toLowerCase();

        if (lower.equals("ac") || lower.startsWith("ac ") ||
                lower.equals("cc") || lower.startsWith("cc ") ||
                lower.equals("pc") || lower.startsWith("pc ")) {
            return;
        }

        if (lower.startsWith("chat ") || lower.startsWith("msg ") || lower.startsWith("message ")) {
            String sub = lower.substring(5).trim();
            if (sub.equals("irc")) {
                IrcClient.isIrcChatActive = true;
                IrcMessageHandler.printMessage("§a[IRC] 已切换至 IRC 聊天频道!");
                ci.cancel();
            } else {
                if (IrcClient.isIrcChatActive) {
                    IrcClient.isIrcChatActive = false;
                    IrcMessageHandler.printMessage("§e[IRC] 已退出 IRC 频道");
                }
            }
        }
    }
}