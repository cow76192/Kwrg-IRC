package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcClient;
import me.cookie.kwrg.irc.IrcMessageHandler;
import me.cookie.kwrg.irc.manager.PlayerFinderManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
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
        // 通知 Manager 玩家发出了命令（区分玩家手打和代码静默触发）
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

    /**
     * 拦截系统聊天消息包：
     * 如果处于“静默获取 Party 列表”期间，取消该数据包的展示，防止刷屏。
     */
    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void onHandleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet == null || packet.content() == null) return;

        // 如果 processSystemChat 返回 true，说明这是需要被静音的队伍信息，直接拦截
        if (PlayerFinderManager.processSystemChat(packet.content().getString())) {
            ci.cancel();
        }
    }
}