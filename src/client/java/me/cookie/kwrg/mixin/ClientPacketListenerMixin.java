package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import me.cookie.kwrg.module.modules.functions.ChatSuffixModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    @ModifyVariable(method = "sendChat", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String kwrg$appendPublicChatSuffix(String message) {
        if (IrcClient.isIrcChatActive) return message;
        return ChatSuffixModule.applyPublicSuffixSafely(message);
    }

    @ModifyVariable(method = "sendCommand", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String kwrg$appendPublicCommandSuffix(String command) {
        return ChatSuffixModule.applyPublicCommandSuffixSafely(command);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
       PlayerFinderManager.onCommandSent(command);

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


    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void onHandleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet == null || packet.content() == null) return;

        if (PlayerFinderManager.processSystemChat(packet.content().getString())) {
            ci.cancel();
        }
    }
}